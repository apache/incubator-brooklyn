package brooklyn.location.jclouds;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.callables.RunScriptOnNode;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Processor;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.scriptbuilder.domain.InterpretableStatement;
import org.jclouds.scriptbuilder.domain.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.HardwareDetails;
import brooklyn.location.MachineDetails;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.BasicHardwareDetails;
import brooklyn.location.basic.BasicMachineDetails;
import brooklyn.location.basic.BasicOsDetails;
import brooklyn.location.basic.HasSubnetHostname;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Networking;
import brooklyn.util.text.Strings;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ListenableFuture;

public class JcloudsSshMachineLocation extends SshMachineLocation implements HasSubnetHostname {
    
    private static final Logger LOG = LoggerFactory.getLogger(JcloudsSshMachineLocation.class);
    private static final long serialVersionUID = -443866395634771659L;

    @SetFromFlag
    JcloudsLocation jcloudsParent;
    
    @SetFromFlag
    NodeMetadata node;
    
    @SetFromFlag
    Template template;
    
    private RunScriptOnNode.Factory runScriptFactory;
    
    public JcloudsSshMachineLocation() {
    }
    
    /**
     * @deprecated since 0.6; use LocationSpec (which calls no-arg constructor)
     */
    @Deprecated
    public JcloudsSshMachineLocation(Map<?,?> flags, JcloudsLocation jcloudsParent, NodeMetadata node) {
        super(flags);
        this.jcloudsParent = jcloudsParent;
        this.node = node;
        
        init();
    }

    @Override
    public void init() {
        if (jcloudsParent != null) {
            super.init();
            ComputeServiceContext context = jcloudsParent.getComputeService().getContext();
            runScriptFactory = context.utils().injector().getInstance(RunScriptOnNode.Factory.class);
        } else {
            // TODO Need to fix the rebind-detection, and not call init() on rebind.
            // This will all change when locations become entities.
            if (LOG.isDebugEnabled()) LOG.debug("Not doing init() of {} because parent not set; presuming rebinding", this);
        }
    }
    
    @Override
    public void rebind() {
        super.rebind();
        ComputeServiceContext context = jcloudsParent.getComputeService().getContext();
        runScriptFactory = context.utils().injector().getInstance(RunScriptOnNode.Factory.class);
    }
    
    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName())
                .add("user", getUser()).add("address", getAddress()).add("port", getConfig(SSH_PORT))
                .add("node", getNode())
                .add("jcloudsId", getJcloudsId())
                .add("privateAddresses", node.getPrivateAddresses())
                .add("publicAddresses", node.getPublicAddresses())
                .add("parentLocation", getParent())
                .add("osDetails", getOsDetails())
                .toString();
    }

    public NodeMetadata getNode() {
        return node;
    }
    
    public Template getTemplate() {
        return template;
    }
    
    public JcloudsLocation getParent() {
        return jcloudsParent;
    }
    
    /** returns the hostname (or sometimes IP) for use by peers in the same subnet,
     * defaulting to public hostname if nothing special
     * <p>
     * for use e.g. in clouds like amazon where other machines
     * in the same subnet need to use a different IP
     */
    @Override
    public String getSubnetHostname() {
        String publicHostname = jcloudsParent.getPublicHostname(node, Optional.<HostAndPort>absent(), getAllConfigBag());
        
        if ("aws-ec2".equals(jcloudsParent.getProvider())) {
            // prefer hostname over IP for aws (resolves to private ip in subnet, and to public from outside)
            if (!Networking.isValidIp4(publicHostname)) {
                return publicHostname; // assume it's a hostname; could check for ip6!
            }
        }
        Optional<String> privateAddress = getPrivateAddress();
        return privateAddress.isPresent() ? privateAddress.get() : publicHostname;
    }

    @Override
    public String getSubnetIp() {
        Optional<String> privateAddress = getPrivateAddress();
        if (privateAddress.isPresent()) {
            return privateAddress.get();
        }
        
        String hostname = jcloudsParent.getPublicHostname(node, Optional.<HostAndPort>absent(), getAllConfigBag());
        if (hostname != null && !Networking.isValidIp4(hostname)) {
            try {
                return InetAddress.getByName(hostname).getHostAddress();
            } catch (UnknownHostException e) {
                LOG.debug("Cannot resolve IP for hostname {} of machine {} (so returning hostname): {}", new Object[] {hostname, this, e});
            }
        }
        return hostname;
    }

    protected Optional<String> getPrivateAddress() {
        if (truth(node.getPrivateAddresses())) {
            Iterator<String> pi = node.getPrivateAddresses().iterator();
            while (pi.hasNext()) {
                String p = pi.next();
                // disallow local only addresses
                if (Networking.isLocalOnly(p)) continue;
                // other things may be public or private, but either way, return it
                return Optional.of(p);
            }
        }
        return Optional.absent();
    }
    
    public String getJcloudsId() {
        return node.getId();
    }
    
    /** executes the given statements on the server using jclouds ScriptBuilder,
     * wrapping in a script which is polled periodically.
     * the output is returned once the script completes (disadvantage compared to other methods)
     * but the process is nohupped and the SSH session is not kept, 
     * so very useful for long-running processes
     */
    public ListenableFuture<ExecResponse> submitRunScript(String ...statements) {
        return submitRunScript(new InterpretableStatement(statements));
    }
    public ListenableFuture<ExecResponse> submitRunScript(Statement script) {
        return submitRunScript(script, new RunScriptOptions());            
    }
    public ListenableFuture<ExecResponse> submitRunScript(Statement script, RunScriptOptions options) {
        return runScriptFactory.submit(node, script, options);
    }
    /** uses submitRunScript to execute the commands, and throws error if it fails or returns non-zero */
    public void execRemoteScript(String ...commands) {
        try {
            ExecResponse result = submitRunScript(commands).get();
            if (result.getExitStatus()!=0)
                throw new IllegalStateException("Error running remote commands (code "+result.getExitStatus()+"): "+commands);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        } catch (ExecutionException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Retrieves the password for this VM, if one exists. The behaviour/implementation is different for different clouds.
     * e.g. on Rackspace, the password for a windows VM is available immediately; on AWS-EC2, for a Windows VM you need 
     * to poll repeatedly until the password is available which can take up to 15 minutes.
     */
    public String waitForPassword() {
        // TODO Hacky; don't want aws specific stuff here but what to do?!
        if (jcloudsParent.getProvider().equals("aws-ec2")) {
            try {
                return JcloudsUtil.waitForPasswordOnAws(jcloudsParent.getComputeService(), node, 15, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                throw Throwables.propagate(e);
            }
        } else {
            LoginCredentials credentials = node.getCredentials();
            return (credentials != null) ? credentials.getPassword() : null;
        }
    }

    @Override
    public MachineDetails getMachineDetails() {
        Optional<String> name = Optional.absent();
        Optional<String> version = Optional.absent();
        Optional<String> architecture = Optional.absent();

        OperatingSystem os = node.getOperatingSystem();
        if (os == null && getTemplate() != null && getTemplate().getImage() != null)
            // some nodes (eg cloudstack, gce) might not get OS available on the node,
            // so also try taking it from the template if available
            os = getTemplate().getImage().getOperatingSystem();

        if (os != null) {
            // Note using family rather than name. Name is often unset.
            name = Optional.fromNullable(os.getFamily() != null && !OsFamily.UNRECOGNIZED.equals(os.getFamily()) ? os.getFamily().toString() : null);
            version = Optional.fromNullable(!Strings.isBlank(os.getVersion()) ? os.getVersion() : null);
            // Using is64Bit rather then getArch because getArch often returns "paravirtual"
            architecture = Optional.fromNullable(os.is64Bit() ? BasicOsDetails.OsArchs.X_86_64 : BasicOsDetails.OsArchs.I386);
        }

        Hardware hardware = node.getHardware();
        Optional<Integer> ram = hardware==null ? Optional.<Integer>absent() : Optional.fromNullable(hardware.getRam());
        Optional<Integer> cpus = hardware==null ? Optional.<Integer>absent() : Optional.fromNullable(hardware.getProcessors() != null ? hardware.getProcessors().size() : null);

        // Skip superclass' SSH to machine if all data is present, otherwise defer to super
        if (name.isPresent() && version.isPresent() && architecture.isPresent() && ram.isPresent() && cpus.isPresent()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Gathered machine details from Jclouds, skipping SSH test on {}", this);
            }
            OsDetails osD = new BasicOsDetails(name.get(), architecture.get(), version.get());
            HardwareDetails hwD = new BasicHardwareDetails(cpus.get(), ram.get());
            return new BasicMachineDetails(hwD, osD);
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Machine details for {} missing from Jclouds, using SSH test instead. name={}, version={}, " +
                                "arch={}, ram={}, #cpus={}",
                        new Object[]{this, name, version, architecture, ram, cpus}
                );
            }
            return super.getMachineDetails();
        }
    }

    @Override
    public Map<String, String> toMetadataRecord() {
        Hardware hardware = node.getHardware();
        List<? extends Processor> processors = (hardware != null) ? hardware.getProcessors() : null;
        
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.putAll(super.toMetadataRecord());
        putIfNotNull(builder, "provider", getParent().getProvider());
        putIfNotNull(builder, "account", getParent().getIdentity());
        putIfNotNull(builder, "serverId", node.getProviderId());
        putIfNotNull(builder, "imageId", node.getImageId());
        putIfNotNull(builder, "instanceTypeName", (hardware != null ? hardware.getName() : null));
        putIfNotNull(builder, "instanceTypeId", (hardware != null ? hardware.getProviderId() : null));
        putIfNotNull(builder, "ram", "" + (hardware != null ? hardware.getRam() : null));
        putIfNotNull(builder, "cpus", "" + (processors != null ? processors.size() : null));
        
        try {
            OsDetails osDetails = getOsDetails();
            putIfNotNull(builder, "osName", osDetails.getName());
            putIfNotNull(builder, "osArch", osDetails.getArch());
            putIfNotNull(builder, "64bit", osDetails.is64bit() ? "true" : "false");
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            LOG.warn("Unable to get OS Details for "+node+"; continuing", e);
        }
        
        return builder.build();
    }
    
    private void putIfNotNull(ImmutableMap.Builder<String, String> builder, String key, @Nullable String value) {
        if (value != null) builder.put(key, value);
    }

}
