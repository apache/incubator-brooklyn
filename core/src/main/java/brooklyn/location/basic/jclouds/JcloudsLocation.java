package brooklyn.location.basic.jclouds;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jclouds.compute.options.RunScriptOptions.Builder.overrideLoginCredentials;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.jclouds.Constants;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.callables.RunScriptOnNode;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.scriptbuilder.domain.InterpretableStatement;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.login.UserAdd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.templates.PortableTemplateBuilder;
import brooklyn.util.IdGenerator;
import brooklyn.util.MutableMap;
import brooklyn.util.internal.Repeater;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * For provisioning and managing VMs in a particular provider/region, using jclouds.
 * 
 * Configuration flags include the following:
 *  - userName (defaults to "root")
 *  - publicKeyFile
 *  - privateKeyFile
 *  - sshPublicKey
 *  - sshPrivateKey
 *  - rootSshPrivateKey (@Beta)
 *  - rootSshPublicKey (@Beta)
 *  - rootSshPublicKeyData (@Beta; calls templateOptions.authorizePublicKey())
 *  - dontCreateUser (otherwise if user != root, then creates this user)
 *  - provider (e.g. "aws-ec2")
 *  - providerLocationId (e.g. "eu-west-1")
 *  - defaultImageId
 * 
 * The flags can also includes values passed straight through to jclouds; to the TemplateBuilder:
 *  - minRam
 *  - hardwareId
 *  - imageSize
 *  - imageId
 *  - imageDescriptionRegex
 *  - imageNameRegex
 *  - imageDescriptionPattern (deprecated: use imageDescriptionRegex)
 *  - imageNamePattern (deprecated: use imageNameRegex)
 * 
 * And flag values passed to TemplateOptions:
 *  - securityGroups (for ec2)
 *  - inboundPorts
 *  - userMetadata
 *  - runAsRoot
 *  - overrideLoginUser
 */
public class JcloudsLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {

    // TODO Needs a big overhaul of how config is being managed, and what the property names are (particularly for private-keys)

    // TODO After converting from Groovy to Java, this is now very bad code! It relies entirely on putting 
    // things into and taking them out of maps; it's not type-safe, and it's thus very error-prone.
    // In Groovy, that's considered ok but not in Java. 
    
    public static final Logger LOG = LoggerFactory.getLogger(JcloudsLocation.class);
    
    public static final String ROOT_USERNAME = "root";
    public static final List<String> NON_ADDABLE_USERS = ImmutableList.of(ROOT_USERNAME, "ubuntu");
    public static final int START_SSHABLE_TIMEOUT = 5*60*1000;

    private final Map<String,Map<String, ? extends Object>> tagMapping = Maps.newLinkedHashMap();
    private final Map<JcloudsSshMachineLocation,String> vmInstanceIds = Maps.newLinkedHashMap();

    JcloudsLocation(Map conf) {
        super(conf);
    }
    
    JcloudsLocation(String identity, String credential, String providerLocationId) {
        this(MutableMap.of("identity", identity, "credential", credential, "providerLocationId", providerLocationId));
    }
    
    protected void configure(Map properties) {
        super.configure(properties);
        if (!truth(name)) {
            name = (String) elvis(getConf().get("providerLocationId"),
                    truth(getConf().get(Constants.PROPERTY_ENDPOINT)) ? 
                            getConf().get("provider")+":"+getConf().get(Constants.PROPERTY_ENDPOINT) :
                            elvis(getConf().get("provider"), "default"));
        }
    }
    
    @Override
    public String toString() {
        Object identity = getConf().get("identity");
        return getClass().getSimpleName()+"["+(identity != null ? identity : null)+":"+name+"]";
    }
    
    public String getProvider() {
        return (String) getConf().get("provider");
    }
    
    public Map getConf() { return leftoverProperties; }
    
    public void setTagMapping(Map<String,Map<String, ? extends Object>> val) {
        tagMapping.clear();
        tagMapping.putAll(val);
    }

    // TODO Delete this? In groovy it was wrong so presumably never called?!
    public void setDefaultImageId(String val) {
        getConf().put("defaultImageId", val);
    }
    
    // TODO Decide on semantics. If I give "TomcatServer" and "Ubuntu", then must I get back an image that matches both?
    // Currently, just takes first match that it finds...
    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        Collection<String> unmatchedTags = Lists.newArrayList();
        for (String it : tags) {
            if (truth(tagMapping.get(it)) && !truth(result)) {
                result.putAll(tagMapping.get(it));
            } else {
                unmatchedTags.add(it);
            }
        }
        if (unmatchedTags.size() > 0) {
            LOG.debug("Location {}, failed to match provisioning tags {}", this, unmatchedTags);
        }
        return result;
    }
    
    public static class BrooklynJcloudsSetupHolder {
        // TODO this could use an external immutable pattern (unused kept internal, used for logging)
        final JcloudsLocation instance;
        public final Map allconf = Maps.newLinkedHashMap();
        public final Map unusedConf = Maps.newLinkedHashMap();
        
        Object _callerContext = null;
        
        LoginCredentials customCredentials;
        
        public BrooklynJcloudsSetupHolder(JcloudsLocation instance) {
            this.instance = instance;
            useConfig(instance.getConf());
        }
        
        BrooklynJcloudsSetupHolder useConfig(Map flags) {
            allconf.putAll(flags);
            unusedConf.putAll(flags);
            return this;
        }
        
        BrooklynJcloudsSetupHolder apply() {
            try {
                if (truth(unusedConf.remove("callerContext"))) _callerContext = allconf.get("callerContext");
                
                // this _creates_ the indicated userName (not a good API...)
                if (!truth(unusedConf.remove("userName"))) allconf.put("userName", ROOT_USERNAME);
                
                // perhaps deprecate supply of data (and of different root key?) to keep it simpler?
                if (truth(unusedConf.remove("publicKeyFile"))) 
                    allconf.put("sshPublicKeyData", Files.toString(instance.getPublicKeyFile(allconf), Charsets.UTF_8));
                if (truth(unusedConf.remove("privateKeyFile"))) 
                    allconf.put("sshPrivateKeyData", Files.toString(instance.getPrivateKeyFile(allconf), Charsets.UTF_8));
                if (truth(unusedConf.remove("sshPublicKey"))) 
                    allconf.put("sshPublicKeyData", Files.toString(asFile(allconf.get("sshPublicKey")), Charsets.UTF_8));
                if (truth(unusedConf.remove("sshPrivateKey"))) 
                    allconf.put("sshPrivateKeyData", Files.toString(asFile(allconf.get("sshPrivateKey")), Charsets.UTF_8));
                if (truth(unusedConf.remove("rootSshPrivateKey"))) 
                    allconf.put("rootSshPrivateKeyData", Files.toString(asFile(allconf.get("rootSshPrivateKey")), Charsets.UTF_8));
                if (truth(unusedConf.remove("rootSshPublicKey"))) 
                    allconf.put("rootSshPublicKeyData", Files.toString(asFile(allconf.get("rootSshPublicKey")), Charsets.UTF_8));
                if (truth(unusedConf.remove("dontCreateUser"))) 
                    allconf.put("dontCreateUser", true);
                // allows specifying a LoginCredentials object, for use by jclouds, if known for the VM (ie it is non-standard);
                if (truth(unusedConf.remove("customCredentials"))) 
                    customCredentials = (LoginCredentials) allconf.get("customCredentials");
         
                // this does not apply to creating a password
                unusedConf.remove("password");
                
                unusedConf.remove("provider");
                unusedConf.remove("providerLocationId");
                unusedConf.remove("noDefaultSshKeys");
                return this;
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        
        String remove(String key) {
            return (String) unusedConf.remove(key);
        }
        
        void warnIfUnused(String context) {
            if (!unusedConf.isEmpty())
                LOG.debug("NOTE: unused flags passed to "+context+" in "+
                        elvis(allconf.get("providerLocationId"), allconf.get("provider"))+": "+unusedConf);
        }
        
        public String getCallerContext() {
            if (truth(_callerContext)) return _callerContext.toString();
            return "thread "+Thread.currentThread().getId();
        }
        
    }
    
    public static final Set<String> getAllSupportedProperties() {
        return ImmutableSet.<String>builder()
                .addAll(SUPPORTED_BASIC_PROPERTIES)
                .addAll(SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.keySet())
                .addAll(SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.keySet())
                .build();    
    }

    //FIXME use of this map and the unusedConf list, with the CredentialsFromEnv and JCloudsLocationFactory, seems overly complicated!
    //also, we need a way to define imageId (and others?) with a specific location
        
    public static final Collection<String> SUPPORTED_BASIC_PROPERTIES = ImmutableSet.of(
        "provider", "identity", "credential", "userName", "publicKeyFile", "privateKeyFile", 
        "sshPublicKey", "sshPrivateKey", "rootSshPrivateKey", "rootSshPublicKey", "groupId", 
        "providerLocationId", "provider");
    
    /** returns public key file, if one has been configured */
    public File getPublicKeyFile() { return getPublicKeyFile(getConf()); }

    public File getPublicKeyFile(Map allconf) { return elvis(asFile(allconf.get("publicKeyFile")), asFile(allconf.get("sshPublicKey"))); }
    
    /** returns private key file, if one has been configured */
    public File getPrivateKeyFile() { return getPrivateKeyFile(getConf()); }

    public File getPrivateKeyFile(Map allconf) { return elvis(asFile(allconf.get("privateKeyFile")), asFile(allconf.get("sshPrivateKey"))); }

    public ComputeService getComputeService() {
        return getComputeService(MutableMap.of());
    }
    public ComputeService getComputeService(Map flags) {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
        return JcloudsUtil.buildOrFindComputeService(setup.allconf, setup.unusedConf);
    }
    
    /** returns the location ID used by the provider, if set, e.g. us-west-1 */
    public String getJcloudsProviderLocationId() {
        return (String) getConf().get("providerLocationId");
    }

    public Set<? extends ComputeMetadata> listNodes() {
        return listNodes(MutableMap.of());
    }
    public Set<? extends ComputeMetadata> listNodes(Map flags) {
        return getComputeService(flags).listNodes();
    }

    public JcloudsSshMachineLocation obtain(TemplateBuilder tb) throws NoMachinesAvailableException {
        return obtain(MutableMap.of(), tb);
    }
    public JcloudsSshMachineLocation obtain(Map flags, TemplateBuilder tb) throws NoMachinesAvailableException {
        Map flags2 = MutableMap.builder().putAll(flags).put("templateBuilder", tb).build();
        return obtain(flags2);
    }
    public JcloudsSshMachineLocation obtain() throws NoMachinesAvailableException {
        return obtain(MutableMap.of());
    }
    public JcloudsSshMachineLocation obtain(Map flags) throws NoMachinesAvailableException {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
                
        String groupId = elvis(setup.remove("groupId"), "brooklyn_"+System.getProperty("user.name")+"_"+IdGenerator.makeRandomId(8));
        final ComputeService computeService = JcloudsUtil.buildOrFindComputeService(setup.allconf, setup.unusedConf);
        
        NodeMetadata node = null;
        try {
            LOG.info("Creating VM in "+
                    elvis(setup.allconf.get("providerLocationId"), setup.allconf.get("provider"))+
                    " for "+setup.getCallerContext());

            Template template = buildTemplate(computeService, (String)setup.allconf.get("providerLocationId"), setup);

            setup.warnIfUnused("JcloudsLocation.obtain");            
    
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            LOG.debug("jclouds created {} for {}", node, setup.getCallerContext());
            if (node == null) {
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in location "
                        +setup.allconf.get("providerLocationId")+" for "+setup.getCallerContext());
            }

            LoginCredentials expectedCredentials = setup.customCredentials;
            if (expectedCredentials!=null) {
                //set userName and other data, from these credentials
                setup.allconf.put("userName", expectedCredentials.getUser());
                if (truth(expectedCredentials.getPassword())) setup.allconf.put("password", expectedCredentials.getPassword());
                if (truth(expectedCredentials.getPrivateKey())) setup.allconf.put("sshPrivateKeyData", expectedCredentials.getPrivateKey());
            }
            if (expectedCredentials==null && truth(setup.allconf.get("sshPrivateKeyData"))) {
                expectedCredentials = LoginCredentials.fromCredentials(node.getCredentials());
                String userName = (String) setup.allconf.get("userName");
                if (truth(expectedCredentials.getUser())) {
                    if ("root".equals(userName) && "ubuntu".equals(expectedCredentials.getUser())) {
                        // FIXME should use 'null' as username then learn it from jclouds
                        // (or use AdminAccess!)
                        LOG.debug("overriding username 'root' in favour of 'ubuntu' at {}", node);
                        setup.allconf.put("userName", "ubuntu");
                        userName = "ubuntu";
                    }
                }
                expectedCredentials = LoginCredentials.fromCredentials(new Credentials(userName, (String)setup.allconf.get("sshPrivateKeyData")));
                //override credentials
            }
            if (expectedCredentials != null)
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
            else
                expectedCredentials = LoginCredentials.fromCredentials(node.getCredentials());
            
            // Wait for the VM to be reachable over SSH
            if (setup.allconf.get("waitForSshable") != null ? truth(setup.allconf.get("waitForSshable")) : true) {
                String vmIp = JcloudsUtil.getFirstReachableAddress(node);
                final NodeMetadata nodeRef = node;
                final LoginCredentials expectedCredentialsRef = expectedCredentials;
                
                LOG.info("Started VM in {} for {}; waiting for it to be sshable on {}@{}",
                        new Object[] {
                                elvis(setup.allconf.get("providerLocationId"), setup.allconf.get("provider")),
                                setup.getCallerContext(), 
                                setup.allconf.get("userName"), 
                                vmIp
                        });
                boolean reachable = new Repeater()
                    .repeat()
                    .every(1,SECONDS)
                    .until(new Callable<Boolean>() {
                        public Boolean call() {
                            Statement statement = Statements.newStatementList(exec("hostname"));
                            ExecResponse response = computeService.runScriptOnNode(nodeRef.getId(), statement, 
                                    overrideLoginCredentials(expectedCredentialsRef));
                            return response.getExitCode() == 0;
                        }})
                    .limitTimeTo(START_SSHABLE_TIMEOUT,MILLISECONDS)
                    .run();

                if (!reachable) {
                    throw new IllegalStateException("SSH failed for "+
                            setup.allconf.get("userName")+"@"+vmIp+" (for "+setup.getCallerContext()+") after waiting "+
                            START_SSHABLE_TIMEOUT+"ms");
                }
            }
            
            String vmHostname = getPublicHostname(node, setup.allconf);
            
            Map sshConfig = Maps.newLinkedHashMap();

            if (truth(getPrivateKeyFile())) sshConfig.put("keyFiles", ImmutableList.of(getPrivateKeyFile().getCanonicalPath()));
            if (truth(setup.allconf.get("sshPrivateKeyData"))) {
                sshConfig.put("privateKey", setup.allconf.get("sshPrivateKeyData"));
                sshConfig.put("privateKeyData", setup.allconf.get("sshPrivateKeyData"));
                sshConfig.put("sshPrivateKeyData", setup.allconf.get("sshPrivateKeyData"));
            } else if (truth(getPrivateKeyFile())) {
                sshConfig.put("keyFiles", ImmutableList.of(getPrivateKeyFile().getCanonicalPath()));
            } else if (node.getCredentials().getPassword() != null) {
                sshConfig.put("password", node.getCredentials().getPassword());
            }
            if (truth(setup.allconf.get("sshPublicKeyData"))) {
                sshConfig.put("sshPublicKeyData", setup.allconf.get("sshPublicKeyData"));
            }
    
            if (LOG.isDebugEnabled())
                LOG.debug("creating JcloudsSshMachineLocation for {}@{} for {} with {}", 
                        new Object[] {
                                setup.allconf.get("userName"), 
                                vmHostname, 
                                setup.getCallerContext(), 
                                Entities.sanitize(sshConfig)
                        });
            JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(
                    MutableMap.builder()
                            .put("address", vmHostname) 
                            .put("displayName", vmHostname)
                            .put("username", setup.allconf.get("userName"))
                            .put("config", sshConfig)
                            .build(),
                    this, 
                    node);
            if (truth(setup.allconf.get("sshPrivateKeyData"))) 
                sshLocByHostname.configure(MutableMap.of("sshPrivateKeyData", setup.allconf.get("sshPrivateKeyData")));
            if (truth(setup.allconf.get("sshPublicKeyData"))) 
                sshLocByHostname.configure(MutableMap.of("sshPublicKeyData", setup.allconf.get("sshPublicKeyData")));
            if (truth(setup.allconf.get("password"))) 
                sshLocByHostname.configure(MutableMap.of("password", setup.allconf.get("password")));
            
            sshLocByHostname.setParentLocation(this);
            vmInstanceIds.put(sshLocByHostname, node.getId());
            
            return sshLocByHostname;
        } catch (RunNodesException e) {
            if (e.getNodeErrors().size() > 0) {
                node = Iterables.get(e.getNodeErrors().keySet(), 0);
            }
            LOG.error("Failed to start VM: {}", e.getMessage());
            throw Throwables.propagate(e);
        } catch (Exception e) {
            LOG.error("Failed to start VM: {}", e.getMessage());
            LOG.info(Throwables.getStackTraceAsString(e));
            throw Throwables.propagate(e);
        } finally {
            //leave it open for reuse
//            computeService.getContext().close();
        }

    }

    public JcloudsSshMachineLocation rebindMachine(NodeMetadata metadata) throws NoMachinesAvailableException {
        return rebindMachine(MutableMap.of(), metadata);
    }
    public JcloudsSshMachineLocation rebindMachine(Map flags, NodeMetadata metadata) throws NoMachinesAvailableException {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
        
        Map newFlags = setup.allconf;
        newFlags.put("id", metadata.getId());
        LoginCredentials credentials = metadata.getCredentials();
        if (truth(credentials)) {
            if (truth(credentials.getUser())) newFlags.put("userName", credentials.getUser());
            if (truth(credentials.getPrivateKey())) newFlags.put("sshPrivateKeyData", credentials.getPrivateKey());
        } else {
            //username should already be set
            if (!truth(newFlags.get("privateKeyFile")))
                newFlags.put("privateKeyFile", getPrivateKeyFile());
        }
        try {
            newFlags.put("hostname", getPublicHostname(metadata, newFlags));
        } catch (Exception e) {
            // TODO this logic should be placed somewhere more useful/shared
            //try again with user ubuntu, then root
            newFlags.put("userName", "ubuntu");
            try {
                newFlags.put("hostname", getPublicHostname(metadata, newFlags));
            } catch (Exception e2) {
                newFlags.put("userName", "root");
                try {
                    newFlags.put("hostname", getPublicHostname(metadata, newFlags));
                } catch (Exception e3) {
                    LOG.warn("couldn't access "+metadata+" to discover hostname (rethrowing): "+e);
                    throw Throwables.propagate(e);
                }
            }
            LOG.info("remapping username at "+metadata+" to "+newFlags.get("userName")+" (this username works)");
        }
        return rebindMachine(newFlags);
    }
    
    /**
     * Brings an existing machine with the given details under management.
     * <p>
     * Required fields are:
     * <ul>
     *   <li>id: the jclouds VM id, e.g. "eu-west-1/i-5504f21d"
     *   <li>hostname: the public hostname or IP of the machine, e.g. "ec2-176-34-93-58.eu-west-1.compute.amazonaws.com"
     *   <li>userName: the username for ssh'ing into the machine
     * <ul>
     */
    public JcloudsSshMachineLocation rebindMachine(Map flags) throws NoMachinesAvailableException {
        try {
            String id = (String) checkNotNull(flags.get("id"), "id");
            String hostname = (String) checkNotNull(flags.get("hostname"), "hostname");
            String username = (String) checkNotNull(flags.get("userName"), "userName");
            String password = (String) flags.get("password");
            
            LOG.info("Rebinding to VM {} ({}@{}), in jclouds location for provider {}", 
                    new Object[] {id, username, hostname, getProvider()});
            
            BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
                    
            ComputeService computeService = JcloudsUtil.buildComputeService(setup.allconf, setup.unusedConf);
            NodeMetadata node = computeService.getNodeMetadata(id);
            if (node == null) {
                throw new IllegalArgumentException("Node not found with id "+id);
            }
    
            if (truth(setup.allconf.get("sshPrivateKeyData"))) {
                LoginCredentials expectedCredentials = LoginCredentials.fromCredentials(new Credentials((String)setup.allconf.get("userName"), (String)setup.allconf.get("sshPrivateKeyData")));
                //override credentials
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
            }
            // TODO confirm we can SSH ?
    
            Map sshConfig = Maps.newLinkedHashMap();
            if (password != null) {
                sshConfig.put("password", password);
            } else {
                if (truth(getPrivateKeyFile())) sshConfig.put("keyFiles", ImmutableList.of(getPrivateKeyFile().getCanonicalPath()));
                if (truth(setup.allconf.get("sshPrivateKeyData"))) {
                    sshConfig.put("privateKey", setup.allconf.get("sshPrivateKeyData"));
                    sshConfig.put("privateKeyData", setup.allconf.get("sshPrivateKeyData"));
                    sshConfig.put("sshPrivateKeyData", setup.allconf.get("sshPrivateKeyData"));
                } else if (truth(getPrivateKeyFile())) {
                    sshConfig.put("keyFiles", ImmutableList.of(getPrivateKeyFile().getCanonicalPath()));
                } else if (node.getCredentials().getPassword() != null) {
                    sshConfig.put("password", node.getCredentials().getPassword());
                }
                if (truth(setup.allconf.get("sshPublicKeyData"))) {
                    sshConfig.put("sshPublicKeyData", setup.allconf.get("sshPublicKeyData"));
                }
            }
            
            JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(
                    MutableMap.builder()
                            .put("address", hostname) 
                            .put("displayName", hostname)
                            .put("username", username)
                            .put("config", sshConfig)
                            .build(),
                    this, 
                    node);
                
            if (truth(setup.allconf.get("sshPrivateKeyData"))) 
                sshLocByHostname.configure(MutableMap.of("sshPrivateKeyData", setup.allconf.get("sshPrivateKeyData")));
            if (truth(setup.allconf.get("sshPublicKeyData"))) 
                sshLocByHostname.configure(MutableMap.of("sshPublicKeyData", setup.allconf.get("sshPublicKeyData")));
            
            sshLocByHostname.setParentLocation(this);
            vmInstanceIds.put(sshLocByHostname, node.getId());
                
            return sshLocByHostname;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    public static File asFile(Object o) {
        if (o instanceof File) return (File)o;
        if (o == null) return null;
        return new File(o.toString());
    }

    public static String fileAsString(Object o) {
        if (o instanceof String) return (String)o;
        if (o instanceof File) return ((File)o).getAbsolutePath();
        if (o==null) return null;
        return o.toString();
    }

    public void release(SshMachineLocation machine) {
        String instanceId = vmInstanceIds.remove(machine);
        if (!truth(instanceId)) {
            throw new IllegalArgumentException("Unknown machine "+machine);
        }
        
        LOG.info("Releasing machine {} in {}, instance id {}", new Object[] {machine, this, instanceId});
        
        removeChildLocation(machine);
        ComputeService computeService = null;
        try {
            computeService = JcloudsUtil.buildOrFindComputeService(getConf());
            computeService.destroyNode(instanceId);
        } catch (Exception e) {
            LOG.error("Problem releasing machine "+machine+" in "+this+", instance id "+instanceId+
                    "; discarding instance and continuing...", e);
            Throwables.propagate(e);
        } finally {
        /*
         //don't close
            if (computeService != null) {
                try {
                    computeService.getContext().close();
                } catch (Exception e) {
                    LOG.error "Problem closing compute-service's context; continuing...", e
                }
            }
         */
        }
    }
    
    private static interface CustomizeTemplateBuilder {
        void apply(TemplateBuilder tb, Map props, Object v);
    }
    
    private static interface CustomizeTemplateOptions {
        void apply(TemplateOptions tb, Map props, Object v);
    }
    
    /** note, it is important these be written in correct camel case, so the routines
     *  which convert it to "min-ram" syntax and MIN_RAM syntax are correct */
    
    public static final Map<String,CustomizeTemplateBuilder> SUPPORTED_TEMPLATE_BUILDER_PROPERTIES = ImmutableMap.<String,CustomizeTemplateBuilder>builder()
            .put("minRam", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.minRam((Integer)v);
                    }})
            .put("minCores", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.minCores(toDouble(v));
                    }})
            .put("hardwareId", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.hardwareId(((CharSequence)v).toString());
                    }})
            .put("imageId", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageId(((CharSequence)v).toString());
                    }})
            .put("imageDescriptionRegex", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageDescriptionMatches(((CharSequence)v).toString());
                    }})
            .put("imageNameRegex", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageNameMatches(((CharSequence)v).toString());
                    }})
            .put("defaultImageId", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        /* deferred */
                    }})
            .put("templateBuilder", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        /* deferred */
                    }})
                    
             // following are deprecated in 0.4.0, kept for backwards compatibility:
            .put("imageDescriptionPattern", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageDescriptionMatches(((CharSequence)v).toString());
                    }})
            .put("imageNamePattern", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageNameMatches(((CharSequence)v).toString());
                    }})
            .build();

    public static final Map<String,CustomizeTemplateOptions> SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES = ImmutableMap.<String,CustomizeTemplateOptions>builder()
            .put("securityGroups", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            String[] securityGroups = toStringArray(v);
                            ((EC2TemplateOptions)t).securityGroups(securityGroups);
                        // jclouds 1.5, also support:
        //                } else if (t instanceof NovaTemplateOptions) {
        //                    String[] securityGroups = (v instanceof Collection) ? v.toArray(new String[0]) : v;
        //                    ((NovaTemplateOptions)t).securityGroupNames(securityGroups);
                        } else {
                            LOG.info("ignoring securityGroups({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
            .put("userData", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        /* expects UUENCODED byte array or string */
                        if (t instanceof EC2TemplateOptions) {
                            byte[] bytes = toByteArray(v);
                            ((EC2TemplateOptions)t).userData(bytes);
                        } else {
                            LOG.info("ignoring userData({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
            .put("inboundPorts", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        int[] inboundPorts = toIntArray(v);
                        if (LOG.isDebugEnabled()) LOG.debug("opening inbound ports {} for {}", Arrays.toString(inboundPorts), t);
                        t.inboundPorts(inboundPorts);
                    }})
            .put("userMetadata", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.userMetadata(toMapStringString(v));
                    }})
            .put("rootSshPublicKeyData", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.authorizePublicKey(((CharSequence)v).toString());
                    }})
            .put("sshPublicKey", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        /* special; not included here */
                    }})
            .put("userName", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        /* special; not included here */
                    }})
            .put("runAsRoot", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.runAsRoot((Boolean)v);
                    }})
            .put("overrideLoginUser", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.overrideLoginUser(((CharSequence)v).toString());
                    }})
            .build();

    private Template buildTemplate(ComputeService computeService, String providerLocationId, BrooklynJcloudsSetupHolder setup) {
        Map<String,? extends Object> properties = setup.allconf;
        Map unusedConf = setup.unusedConf;
        TemplateBuilder templateBuilder = (TemplateBuilder) unusedConf.remove("templateBuilder");
        if (templateBuilder==null)
            templateBuilder = new PortableTemplateBuilder();
        else
            LOG.debug("jclouds using templateBuilder {} as base for provisioning in {} for {}", new Object[] {templateBuilder, this, setup.getCallerContext()});
 
        if (providerLocationId!=null) {
            templateBuilder.locationId(providerLocationId);
        }
        
        for (Map.Entry<String, CustomizeTemplateBuilder> entry : SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.entrySet()) {
            String name = entry.getKey();
            CustomizeTemplateBuilder code = entry.getValue();
            if (unusedConf.remove(name)!=null)
                code.apply(templateBuilder, properties, properties.get(name));
        }

        if (templateBuilder instanceof PortableTemplateBuilder) {
            ((PortableTemplateBuilder)templateBuilder).attachComputeService(computeService);
            // do the default last, and only if nothing else specified (guaranteed to be a PTB if nothing else specified)
            if (truth(unusedConf.remove("defaultImageId"))) {
                if (((PortableTemplateBuilder)templateBuilder).isBlank()) {
                    CharSequence defaultImageId = (CharSequence) properties.get("defaultImageId");
                    templateBuilder.imageId(defaultImageId.toString());
                }
            }
        }

        Template template = templateBuilder.build();
        TemplateOptions options = template.getOptions();
        
        for (Map.Entry<String, CustomizeTemplateOptions> entry : SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.entrySet()) {
            String name = entry.getKey();
            CustomizeTemplateOptions code = entry.getValue();
            if (unusedConf.remove(name)!=null)
                code.apply(options, properties, properties.get(name));
        }
        
        if (NON_ADDABLE_USERS.contains(properties.get("userName")) && truth(properties.get("sshPublicKeyData"))) {
            String keyData = (String) properties.get("sshPublicKeyData");
            options.authorizePublicKey(keyData);
        }
        //NB: we ignore private key here because, by default we probably should not be installing it remotely;
        //also, it may not be valid for first login (it is created before login e.g. on amazon, so valid there;
        //but not elsewhere, e.g. on rackspace)
        
        // Setup the user
        if (truth(properties.get("userName")) && !NON_ADDABLE_USERS.contains(properties.get("userName")) && 
                !truth(properties.get("dontCreateUser"))) {
            UserAdd.Builder userBuilder = UserAdd.builder();
            userBuilder.login((String)properties.get("userName"));
            String publicKeyData = (String) properties.get("sshPublicKeyData");
            userBuilder.authorizeRSAPublicKey(publicKeyData);
            Statement userBuilderStatement = userBuilder.build();
            options.runScript(userBuilderStatement);
        }
        
        LOG.debug("jclouds using template {} to provision machine in {} for {}", new Object[] {template, this, setup.getCallerContext()});
        return template;
    }
    
    private String getPublicHostname(NodeMetadata node, Map allconf) {
        if ("aws-ec2".equals(allconf != null ? allconf.get("provider") : null)) {
            String vmIp = null;
            try {
                vmIp = JcloudsUtil.getFirstReachableAddress(node);
            } catch (Exception e) {
                LOG.warn("Error reaching aws-ec2 instance on port 22; falling back to jclouds metadata for address", e);
            }
            if (vmIp != null) {
                try {
                    return getPublicHostnameAws(vmIp, allconf);
                } catch (Exception e) {
                    LOG.warn("Error querying aws-ec2 instance over ssh for its hostname; falling back to first reachable IP", e);
                    return vmIp;
                }
            }
        }
        
        return getPublicHostnameGeneric(node, allconf);
    }
    
    private String getPublicHostnameGeneric(NodeMetadata node, @Nullable Map allconf) {
        //prefer the public address to the hostname because hostname is sometimes wrong/abbreviated
        //(see that javadoc; also e.g. on rackspace, the hostname lacks the domain)
        if (truth(node.getPublicAddresses())) {
            return node.getPublicAddresses().iterator().next();
        } else if (truth(node.getHostname())) {
            return node.getHostname();
        } else if (truth(node.getPrivateAddresses())) {
            return node.getPrivateAddresses().iterator().next();
        } else {
            return null;
        }
    }
    
    private String getPublicHostnameAws(String ip, Map allconf) {
        try {
            Map sshConfig = Maps.newLinkedHashMap();
            if (truth(allconf.get("password"))) 
                sshConfig.put("password", allconf.get("password"));
            if (truth(getPrivateKeyFile())) 
                sshConfig.put("keyFiles", ImmutableList.of(getPrivateKeyFile().getCanonicalPath())); 
            if (truth(allconf.get("sshPrivateKeyData"))) 
                sshConfig.put("privateKeyData", allconf.get("sshPrivateKeyData"));
            // TODO messy way to get an SSH session 
            SshMachineLocation sshLocByIp = new SshMachineLocation(MutableMap.of("address", ip, "username", allconf.get("userName"), "config", sshConfig));
            
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            int exitcode = sshLocByIp.run(
                    MutableMap.of("out", outStream, "err", errStream), 
                    "echo `curl --silent --retry 20 http://169.254.169.254/latest/meta-data/public-hostname`; exit");
            String outString = new String(outStream.toByteArray());
            String[] outLines = outString.split("\n");
            for (String line : outLines) {
                if (line.startsWith("ec2-")) return line.trim();
            }
            throw new IllegalStateException("Could not obtain hostname for vm "+ip+"; exitcode="+exitcode+"; stdout="+outString+"; stderr="+new String(errStream.toByteArray()));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    public static class JcloudsSshMachineLocation extends SshMachineLocation {
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
    }

    private static double toDouble(Object v) {
        if (v instanceof Number) {
            return ((Number)v).doubleValue();
        } else {
            throw new IllegalArgumentException("Invalid type for double: "+v+" of type "+v.getClass());
        }
    }

    private static int[] toIntArray(Object v) {
        int[] result;
        if (v instanceof Iterable) {
            result = new int[Iterables.size((Iterable)v)];
            int i = 0;
            for (Object o : (Iterable)v) {
                result[i++] = (Integer) o;
            }
        } else if (v instanceof int[]) {
            result = (int[]) v;
        } else if (v instanceof Object[]) {
            result = new int[((Object[])v).length];
            for (int i = 0; i < result.length; i++) {
                result[i] = (Integer) ((Object[])v)[i];
            }
        } else if (v instanceof Integer) {
            result = new int[] {(Integer)v};
        } else {
            throw new IllegalArgumentException("Invalid type for int[]: "+v+" of type "+v.getClass());
        }
        return result;
    }

    private static String[] toStringArray(Object v) {
        Collection<String> result = Lists.newArrayList();
        if (v instanceof Iterable) {
            int i = 0;
            for (Object o : (Iterable)v) {
                result.add(o.toString());
            }
        } else if (v instanceof Object[]) {
            for (int i = 0; i < ((Object[])v).length; i++) {
                result.add(((Object[])v)[i].toString());
            }
        } else if (v instanceof String) {
            result.add((String) v);
        } else {
            throw new IllegalArgumentException("Invalid type for String[]: "+v+" of type "+v.getClass());
        }
        return result.toArray(new String[0]);
    }
    
    private static byte[] toByteArray(Object v) {
        if (v instanceof byte[]) {
            return (byte[]) v;
        } else if (v instanceof CharSequence) {
            return v.toString().getBytes();
        } else {
            throw new IllegalArgumentException("Invalid type for byte[]: "+v+" of type "+v.getClass());
        }
    }
    
    // Handles GString
    private static Map<String,String> toMapStringString(Object v) {
        if (v instanceof Map<?,?>) {
            Map<String,String> result = Maps.newLinkedHashMap();
            for (Map.Entry<?,?> entry : ((Map<?,?>)v).entrySet()) {
                String key = ((CharSequence)entry.getKey()).toString();
                String value = ((CharSequence)entry.getValue()).toString();
                result.put(key, value);
            }
            return result;
        } else {
            throw new IllegalArgumentException("Invalid type for Map<String,String>: "+v+" of type "+v.getClass());
        }
    }
}
