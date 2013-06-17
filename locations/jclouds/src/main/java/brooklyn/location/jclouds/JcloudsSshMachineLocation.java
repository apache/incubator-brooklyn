package brooklyn.location.jclouds;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.callables.RunScriptOnNode;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.scriptbuilder.domain.InterpretableStatement;
import org.jclouds.scriptbuilder.domain.Statement;

import brooklyn.location.OsDetails;
import brooklyn.location.basic.BasicOsDetails;
import brooklyn.location.basic.HasSubnetHostname;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;

public class JcloudsSshMachineLocation extends SshMachineLocation implements HasSubnetHostname {
    final JcloudsLocation parent;
    final NodeMetadata node;
    private final RunScriptOnNode.Factory runScriptFactory;
    
    public JcloudsSshMachineLocation(Map flags, JcloudsLocation parent, NodeMetadata node) {
        super(flags);
        this.parent = parent;
        this.node = node;
        
        ComputeServiceContext context = parent.getComputeService().getContext();
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
    
    public JcloudsLocation getParent() {
        return parent;
    }
    
    /** returns the hostname for use by peers in the same subnet,
     * defaulting to public hostname if nothing special
     * <p>
     * for use e.g. in clouds like amazon where other machines
     * in the same subnet need to use a different IP
     */
    public String getSubnetHostname() {
        if (truth(node.getPrivateAddresses()))
            return node.getPrivateAddresses().iterator().next();
        return parent.getPublicHostname(node, null);
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
        if (parent.getProvider().equals("aws-ec2")) {
            try {
                return JcloudsUtil.waitForPasswordOnAws(parent.getComputeService(), node, 15, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                throw Throwables.propagate(e);
            }
        } else {
            LoginCredentials credentials = node.getCredentials();
            return (credentials != null) ? credentials.getPassword() : null;
        }
    }

    @Override
    public OsDetails getOsDetails() {
        if (node.getOperatingSystem() != null) {
            return new BasicOsDetails(
                    node.getOperatingSystem().getName() != null
                            ? node.getOperatingSystem().getName() : "linux",
                    node.getOperatingSystem().getArch() != null
                            ? node.getOperatingSystem().getArch() : BasicOsDetails.OsArchs.I386,
                    node.getOperatingSystem().getVersion() != null
                            ? node.getOperatingSystem().getVersion() : "unknown",
                    node.getOperatingSystem().is64Bit());
        }
        return super.getOsDetails();
    }
}