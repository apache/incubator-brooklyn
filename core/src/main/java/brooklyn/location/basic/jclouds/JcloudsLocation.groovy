package brooklyn.location.basic.jclouds;

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.jclouds.compute.options.RunScriptOptions.Builder.overrideLoginCredentials
import static org.jclouds.scriptbuilder.domain.Statements.exec
import static com.google.common.base.Preconditions.checkNotNull

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit

import javax.annotation.Nullable


import org.jclouds.Constants
import org.jclouds.compute.ComputeService
import org.jclouds.compute.RunNodesException
import org.jclouds.compute.callables.RunScriptOnNode
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.Template
import org.jclouds.compute.domain.TemplateBuilder
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.compute.options.TemplateOptions
import org.jclouds.domain.Credentials
import org.jclouds.domain.LoginCredentials
import org.jclouds.ec2.compute.options.EC2TemplateOptions
import org.jclouds.scriptbuilder.domain.InterpretableStatement
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.domain.Statements
import org.jclouds.scriptbuilder.statements.login.UserAdd
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Entities;
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.AbstractLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.templates.PortableTemplateBuilder
import brooklyn.util.IdGenerator
import brooklyn.util.MutableMap
import brooklyn.util.internal.Repeater

import com.google.common.base.Charsets
import com.google.common.base.Throwables
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.io.Files
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
    
    public static final Logger LOG = LoggerFactory.getLogger(JcloudsLocation.class);
    
    public static final String ROOT_USERNAME = "root";
    public static final List NON_ADDABLE_USERS = [ ROOT_USERNAME, "ubuntu" ];
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
        if (!name) name = conf.providerLocationId ?:
            conf.get(Constants.PROPERTY_ENDPOINT) ? conf.provider+":"+conf.get(Constants.PROPERTY_ENDPOINT) :
            conf.provider ?: 
            "default";
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+conf?.identity+":"+name+"]";
    }
    
    public String getProvider() {
        return conf.provider;
    }
    
    public Map getConf() { return leftoverProperties; }
    
    public void setTagMapping(Map<String,Map<String, ? extends Object>> val) {
        tagMapping.clear();
        tagMapping.putAll(val);
    }
    
    public void setDefaultImageId(String val) {
        defaultImageId = val;
    }
    
    // TODO Decide on semantics. If I give "TomcatServer" and "Ubuntu", then must I get back an image that matches both?
    // Currently, just takes first match that it finds...
    Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        Collection<String> unmatchedTags = Lists.newArrayList();
        for (String it : tags) {
            if (tagMapping.get(it) && !result) {
                result.putAll(tagMapping.get(it));
            } else {
                unmatchedTags.add(it);
            }
        }
        if (unmatchedTags) {
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
            useConfig(instance.conf);
        }
        
        BrooklynJcloudsSetupHolder useConfig(Map flags) {
            allconf.putAll(flags);
            unusedConf.putAll(flags);
            return this;
        }
        
        BrooklynJcloudsSetupHolder apply() {
            if (unusedConf.remove("callerContext")) _callerContext = allconf.callerContext;
            
            // this _creates_ the indicated userName (not a good API...)
            if (!unusedConf.remove("userName")) allconf.userName = ROOT_USERNAME
            
            // perhaps deprecate supply of data (and of different root key?) to keep it simpler?
            if (unusedConf.remove("publicKeyFile")) allconf.sshPublicKeyData = Files.toString(instance.getPublicKeyFile(allconf), Charsets.UTF_8)
            if (unusedConf.remove("privateKeyFile")) allconf.sshPrivateKeyData = Files.toString(instance.getPrivateKeyFile(allconf), Charsets.UTF_8)
            if (unusedConf.remove("sshPublicKey")) allconf.sshPublicKeyData = Files.toString(asFile(allconf.sshPublicKey), Charsets.UTF_8)
            if (unusedConf.remove("sshPrivateKey")) allconf.sshPrivateKeyData = Files.toString(asFile(allconf.sshPrivateKey), Charsets.UTF_8)
            if (unusedConf.remove("rootSshPrivateKey")) allconf.rootSshPrivateKeyData = Files.toString(asFile(allconf.rootSshPrivateKey), Charsets.UTF_8)
            if (unusedConf.remove("rootSshPublicKey")) allconf.rootSshPublicKeyData = Files.toString(asFile(allconf.rootSshPublicKey), Charsets.UTF_8)
            if (unusedConf.remove("dontCreateUser")) allconf.dontCreateUser = true;
            // allows specifying a LoginCredentials object, for use by jclouds, if known for the VM (ie it is non-standard);
            if (unusedConf.remove("customCredentials")) customCredentials = allconf.customCredentials;
     
            // this does not apply to creating a password
            unusedConf.remove("password");
            
            unusedConf.remove("provider");
            unusedConf.remove("providerLocationId");
            unusedConf.remove("noDefaultSshKeys");
            return this;
        }
        
        String remove(String key) {
            return unusedConf.remove(key);
        }
        
        void warnIfUnused(String context) {
            if (!unusedConf.isEmpty())
                LOG.debug("NOTE: unused flags passed to "+context+" in "+(allconf.providerLocationId?:allconf.provider)+": "+unusedConf);
        }
        
        public String getCallerContext() {
            if (_callerContext) return _callerContext;
            return "thread "+Thread.currentThread().id;
        }
        
    }
    
    public static final Set getAllSupportedProperties() {
        return SUPPORTED_BASIC_PROPERTIES.keySet() + SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.keySet() + SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.keySet();    
    }

    //FIXME use of this map and the unusedConf list, with the CredentialsFromEnv and JCloudsLocationFactory, seems overly complicated!
    //also, we need a way to define imageId (and others?) with a specific location
        
    public static final Map SUPPORTED_BASIC_PROPERTIES = [
        provider:{}, identity:{}, credential:{},
        userName:{}, 
        publicKeyFile:{}, privateKeyFile:{}, 
        sshPublicKey:{}, sshPrivateKey:{}, 
        rootSshPrivateKey:{}, rootSshPublicKey:{}, 
        groupId:{},
        providerLocationId:{}, provider:{} ];
    
    /** returns public key file, if one has been configured */
    public File getPublicKeyFile() { return getPublicKeyFile(conf); }

    public File getPublicKeyFile(Map allconf) { asFile(allconf.publicKeyFile) ?: asFile(allconf.sshPublicKey) }
    
    /** returns private key file, if one has been configured */
    public File getPrivateKeyFile() { return getPrivateKeyFile(conf); }

    public File getPrivateKeyFile(Map allconf) { asFile(allconf.privateKeyFile) ?: asFile(allconf.sshPrivateKey) }
    
    public ComputeService getComputeService(Map flags=[:]) {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
        return JcloudsUtil.buildOrFindComputeService(setup.allconf, setup.unusedConf);
    }
    
    /** returns the location ID used by the provider, if set, e.g. us-west-1 */
    public String getJcloudsProviderLocationId() {
        return conf.providerLocationId;
    }
    
    public Set<? extends ComputeMetadata> listNodes(Map flags=[:]) {
        return getComputeService(flags).listNodes();
    }
    
    public JcloudsSshMachineLocation obtain(Map flags=[:], TemplateBuilder tb) throws NoMachinesAvailableException {
        Map flags2=[:]+flags+[templateBuilder: tb];
        obtain(flags2);
    }
    public JcloudsSshMachineLocation obtain(Map flags=[:]) throws NoMachinesAvailableException {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
                
        String groupId = (setup.remove("groupId") ?: "brooklyn_"+System.getProperty("user.name")+"_"+IdGenerator.makeRandomId(8))
        ComputeService computeService = JcloudsUtil.buildOrFindComputeService(setup.allconf, setup.unusedConf);
        
        NodeMetadata node = null;
        try {
            LOG.info("Creating VM in "+(setup.allconf.providerLocationId?:setup.allconf.provider)+" for "+setup.callerContext);

            Template template = buildTemplate(computeService, setup.allconf.providerLocationId, setup);

            setup.warnIfUnused("JcloudsLocation.obtain")            
    
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            LOG.debug("jclouds created $node for ${setup.callerContext}");
            if (node == null) {
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in location ${allconf.providerLocationId} for ${setup.callerContext}");
            }

            String vmIp = JcloudsUtil.getFirstReachableAddress(node);
            LoginCredentials expectedCredentials = setup.customCredentials;
            if (expectedCredentials!=null) {
                //set userName and other data, from these credentials
                setup.allconf.userName = expectedCredentials.getUser();
                if (expectedCredentials.getPassword()) setup.allconf.password = expectedCredentials.getPassword();
                if (expectedCredentials.getPrivateKey()) setup.allconf.sshPrivateKeyData = expectedCredentials.getPrivateKey();
            }
            if (expectedCredentials==null && setup.allconf.sshPrivateKeyData) {
                expectedCredentials = LoginCredentials.fromCredentials(node.getCredentials());
                String userName = setup.allconf.userName;
                if (expectedCredentials.getUser()) {
                    if ("root".equals(userName) && "ubuntu".equals(expectedCredentials.getUser())) {
                        // FIXME should use 'null' as username then learn it from jclouds
                        // (or use AdminAccess!)
                        LOG.debug("overriding username 'root' in favour of 'ubuntu' at ${node}");
                        setup.allconf.userName = "ubuntu";
                        userName = "ubuntu";
                    }
                }
                expectedCredentials = LoginCredentials.fromCredentials(new Credentials(userName, setup.allconf.sshPrivateKeyData));
                //override credentials
            }
            if (expectedCredentials)
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
            else
                expectedCredentials = LoginCredentials.fromCredentials(node.getCredentials());
            
            // Wait for the VM to be reachable over SSH
            if (setup.allconf.waitForSshable != null ? setup.allconf.waitForSshable : true) {
                LOG.info("Started VM in ${setup.allconf.providerLocationId ?: setup.allconf.provider} for ${setup.callerContext}; "+
                        "waiting for it to be sshable on ${setup.allconf.userName}@${vmIp}");
                boolean reachable = new Repeater()
                    .repeat()
                    .every(1,SECONDS)
                    .until {
                        Statement statement = Statements.newStatementList(exec('hostname'))
                        ExecResponse response = computeService.runScriptOnNode(node.getId(), statement, 
                                overrideLoginCredentials(expectedCredentials));
                        return response.exitCode == 0 }
                    .limitTimeTo(START_SSHABLE_TIMEOUT,MILLISECONDS)
                    .run()

                if (!reachable) {
                    throw new IllegalStateException("SSH failed for ${setup.allconf.userName}@${vmIp} (for ${setup.callerContext}) after waiting ${START_SSHABLE_TIMEOUT}ms");
                }
            }
            
            String vmHostname = getPublicHostname(node, setup.allconf)
            
            Map sshConfig = [:]

            if (getPrivateKeyFile()) sshConfig.keyFiles = [ getPrivateKeyFile().getCanonicalPath() ];
            if (setup.allconf.sshPrivateKeyData) {
                sshConfig.privateKey = setup.allconf.sshPrivateKeyData;
                sshConfig.privateKeyData = setup.allconf.sshPrivateKeyData;
                sshConfig.sshPrivateKeyData = setup.allconf.sshPrivateKeyData;
            } else if (getPrivateKeyFile()) {
                sshConfig.keyFiles = [ getPrivateKeyFile().getCanonicalPath() ];
            } else if (node.getCredentials().getPassword() != null) {
                sshConfig.password = node.getCredentials().getPassword();
            }
            if (setup.allconf.sshPublicKeyData) {
                sshConfig.sshPublicKeyData = setup.allconf.sshPublicKeyData;
            }
    
            if (LOG.isDebugEnabled())
                LOG.debug("creating JcloudsSshMachineLocation for {}@{} for {} with {}", setup.allconf.userName, vmHostname, setup.callerContext, Entities.sanitize(sshConfig))
            JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(this, node,
                    address:vmHostname, 
                    displayName:vmHostname,
                    username:setup.allconf.userName, 
                    config:sshConfig);
            if (setup.allconf.sshPrivateKeyData) sshLocByHostname.configure(sshPrivateKeyData: setup.allconf.sshPrivateKeyData);
            if (setup.allconf.sshPublicKeyData) sshLocByHostname.configure(sshPublicKeyData: setup.allconf.sshPublicKeyData);
            if (setup.allconf.password) sshLocByHostname.configure(password: setup.allconf.password);
            
            sshLocByHostname.setParentLocation(this);
            vmInstanceIds.put(sshLocByHostname, node.getId())
            
            return sshLocByHostname
        } catch (RunNodesException e) {
            if (e.getNodeErrors().size() > 0) {
                node = Iterables.get(e.getNodeErrors().keySet(), 0);
            }
            LOG.error "Failed to start VM: {}", e.message
            throw Throwables.propagate(e)
        } catch (Exception e) {
            LOG.error "Failed to start VM: {}", e.message
            LOG.info Throwables.getStackTraceAsString(e)
            throw Throwables.propagate(e)
        } finally {
            //leave it open for reuse
//            computeService.getContext().close();
        }

    }
    
    public JcloudsSshMachineLocation rebindMachine(Map flags=[:], NodeMetadata metadata) throws NoMachinesAvailableException {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
        
        def newFlags = setup.allconf;
        newFlags.id = metadata.getId();
        LoginCredentials credentials = metadata.getCredentials();
        if (credentials) {
            if (credentials.getUser()) newFlags.userName = credentials.getUser();
            if (credentials.getPrivateKey()) newFlags.sshPrivateKeyData = credentials.getPrivateKey();
        } else {
            //username should already be set
            if (!newFlags.privateKeyFile)
                newFlags.privateKeyFile = getPrivateKeyFile();
        }
        try {
            newFlags.hostname = getPublicHostname(metadata, newFlags);
        } catch (Exception e) {
            // TODO this logic should be placed somewhere more useful/shared
            //try again with user ubuntu, then root
            newFlags.userName = "ubuntu";
            try {
                newFlags.hostname = getPublicHostname(metadata, newFlags);
            } catch (Exception e2) {
                newFlags.userName = "root";
                try {
                    newFlags.hostname = getPublicHostname(metadata, newFlags);
                } catch (Exception e3) {
                    LOG.warn "couldn't access "+metadata+" to discover hostname (rethrowing): "+e
                    throw Throwables.propagate(e);
                }
            }
            LOG.info "remapping username at "+metadata+" to "+newFlags.userName+" (this username works)"
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
        String id = checkNotNull(flags.id, "id")
        String hostname = checkNotNull(flags.hostname, "hostname")
        String username = checkNotNull(flags.userName, "userName")
        String password = flags.password
        
        LOG.info("Rebinding to VM $id ($username@$hostname), in jclouds location for provider $provider")
        
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
                
        ComputeService computeService = JcloudsUtil.buildComputeService(setup.allconf, setup.unusedConf);
        NodeMetadata node = computeService.getNodeMetadata(id)
        if (node == null) {
            throw new IllegalArgumentException("Node not found with id "+id)
        }

        if (setup.allconf.sshPrivateKeyData) {
            LoginCredentials expectedCredentials = LoginCredentials.fromCredentials(new Credentials(setup.allconf.userName, setup.allconf.sshPrivateKeyData));
            //override credentials
            node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
        }
        // TODO confirm we can SSH ?

        Map sshConfig = [:]
        if (password != null) {
            sshConfig.password = password;
        } else {
            if (getPrivateKeyFile()) sshConfig.keyFiles = [ getPrivateKeyFile().getCanonicalPath() ];
            if (setup.allconf.sshPrivateKeyData) {
                sshConfig.privateKey = setup.allconf.sshPrivateKeyData;
                sshConfig.privateKeyData = setup.allconf.sshPrivateKeyData;
                sshConfig.sshPrivateKeyData = setup.allconf.sshPrivateKeyData;
            } else if (getPrivateKeyFile()) {
                sshConfig.keyFiles = [ getPrivateKeyFile().getCanonicalPath() ];
            } else if (node.getCredentials().getPassword() != null) {
                sshConfig.password = node.getCredentials().getPassword();
            }
            if (setup.allconf.sshPublicKeyData) {
                sshConfig.sshPublicKeyData = setup.allconf.sshPublicKeyData;
            }
        }
        
        JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(this, node,
                address:hostname, 
                displayName:hostname,
                username:username, 
                config:sshConfig);
            
        if (setup.allconf.sshPrivateKeyData) sshLocByHostname.configure(sshPrivateKeyData, setup.allconf.sshPrivateKeyData);
        if (setup.allconf.sshPublicKeyData) sshLocByHostname.configure(sshPublicKeyData, setup.allconf.sshPublicKeyData);
        
        sshLocByHostname.setParentLocation(this)
        vmInstanceIds.put(sshLocByHostname, node.getId())
            
        return sshLocByHostname
    }
    
    public static File asFile(Object o) {
        if (o in File) return o;
        if (o==null) return o;
        return new File(o.toString());
    }

    public static String fileAsString(Object o) {
        if (o in String) return o;
        if (o in File) return ((File)o).absolutePath;
        if (o==null) return o;
        return o.toString()
    }

    void release(SshMachineLocation machine) {
        String instanceId = vmInstanceIds.remove(machine)
        if (!instanceId) {
            throw new IllegalArgumentException("Unknown machine "+machine)
        }
        
        LOG.info("Releasing machine $machine in $this, instance id $instanceId");
        
        removeChildLocation(machine)
        ComputeService computeService = null;
        try {
            computeService = JcloudsUtil.buildOrFindComputeService(conf);
            computeService.destroyNode(instanceId);
        } catch (Exception e) {
            LOG.error "Problem releasing machine $machine in $this, instance id $instanceId; discarding instance and continuing...", e
            Throwables.propagate(e)
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
    
    /** note, it is important these be written in correct camel case, so the routines
     *  which convert it to "min-ram" syntax and MIN_RAM syntax are correct */
    
    public static final Map SUPPORTED_TEMPLATE_BUILDER_PROPERTIES = [
            minRam:         { TemplateBuilder tb, Map props, Object v -> tb.minRam(v) },
            minCores:       { TemplateBuilder tb, Map props, Object v -> tb.minCores(v) },
            hardwareId:     { TemplateBuilder tb, Map props, Object v -> tb.hardwareId(v) },
            imageId:        { TemplateBuilder tb, Map props, Object v -> tb.imageId(v) },
            imageDescriptionRegex:        { TemplateBuilder tb, Map props, Object v -> tb.imageDescriptionMatches(v) },
            imageNameRegex:               { TemplateBuilder tb, Map props, Object v -> tb.imageNameMatches(v) },
            defaultImageId:               { TemplateBuilder tb, Map props, Object v -> /* deferred */ },
            templateBuilder:              { TemplateBuilder tb, Map props, Object v -> /* deferred */ },
// following are deprecated in 0.4.0, kept for backwards compatibility:
            imageDescriptionPattern:      { TemplateBuilder tb, Map props, Object v -> tb.imageDescriptionMatches(v) },
            imageNamePattern:             { TemplateBuilder tb, Map props, Object v -> tb.imageNameMatches(v) },
        ];

    public static final Map SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES = [
            securityGroups:    { TemplateOptions t, Map props, Object v -> 
                if (t instanceof EC2TemplateOptions) {
                    String[] securityGroups = (v instanceof Collection) ? v.toArray(new String[0]) : v;
                    ((EC2TemplateOptions)t).securityGroups(securityGroups);
                // jclouds 1.5, also support:
//                } else if (t instanceof NovaTemplateOptions) {
//                    String[] securityGroups = (v instanceof Collection) ? v.toArray(new String[0]) : v;
//                    ((NovaTemplateOptions)t).securityGroupNames(securityGroups);
                } else {
                    LOG.info("ignoring securityGroups({$v}) in VM creation because not supported for cloud/type (${t})");
                }
            },
            userData:    { TemplateOptions t, Map props, Object v ->
                /** expects UUENCODED byte array or string */
                if (v instanceof String) v = ((String)v).getBytes(); 
                if (t instanceof EC2TemplateOptions) {
                    ((EC2TemplateOptions)t).userData(v);
                } else {
                    LOG.info("ignoring userData({$v}) in VM creation because not supported for cloud/type (${t})");
                }
            },
            inboundPorts:      { TemplateOptions t, Map props, Object v ->
                int[] inboundPorts = (v instanceof Collection) ? v.toArray(new int[0]) : v;
                if (LOG.isDebugEnabled()) LOG.debug("opening inbound ports ${Arrays.toString(v)} for ${t}");
                t.inboundPorts(inboundPorts);
            },
            userMetadata:  { TemplateOptions t, Map props, Object v -> t.userMetadata(v) },
            rootSshPublicKeyData:  { TemplateOptions t, Map props, Object v -> t.authorizePublicKey(v) },
            sshPublicKey:  { TemplateOptions t, Map props, Object v -> /* special; not included here */  },
            userName:  { TemplateOptions t, Map props, Object v -> /* special; not included here */ },
            runAsRoot:  { TemplateOptions t, Map props, Object v -> t.runAsRoot(v) },
            overrideLoginUser:  { TemplateOptions t, Map props, Object v -> t.overrideLoginUser(v) }
        ];

    private Template buildTemplate(ComputeService computeService, String providerLocationId, BrooklynJcloudsSetupHolder setup) {
        Map<String,? extends Object> properties = setup.allconf;
        Map unusedConf = setup.unusedConf;
        TemplateBuilder templateBuilder = unusedConf.remove("templateBuilder");
        if (templateBuilder==null)
            templateBuilder = new PortableTemplateBuilder();
        else
            LOG.debug("jclouds using templateBuilder $templateBuilder as base for provisioning in $this for ${setup.callerContext}");
 
        if (providerLocationId!=null) {
            templateBuilder.locationId(providerLocationId);
        }
        
        SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.each { name, code ->
            if (unusedConf.remove(name)!=null)
                code.call(templateBuilder, properties, properties.get(name));
        }

        if (templateBuilder in PortableTemplateBuilder) {
            ((PortableTemplateBuilder)templateBuilder).attachComputeService(computeService);
            // do the default last, and only if nothing else specified (guaranteed to be a PTB if nothing else specified)
            if (unusedConf.remove("defaultImageId")) {
                if (((PortableTemplateBuilder)templateBuilder).isBlank()) {
                    templateBuilder.imageId(properties.get("defaultImageId"));
                }
            }
        }

        Template template = templateBuilder.build();
        TemplateOptions options = template.getOptions();
        
        SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.each { name, code ->
            if (unusedConf.remove(name)!=null)
                code.call(options, properties, properties.get(name));
        }
        
        if ((properties.userName in NON_ADDABLE_USERS) && properties.sshPublicKeyData) {
            String keyData = properties.sshPublicKeyData
            options.authorizePublicKey(keyData)
        }
        //NB: we ignore private key here because, by default we probably should not be installing it remotely;
        //also, it may not be valid for first login (it is created before login e.g. on amazon, so valid there;
        //but not elsewhere, e.g. on rackspace)
        
        // Setup the user
        if (properties.userName && !(properties.userName in NON_ADDABLE_USERS) && !properties.dontCreateUser) {
            UserAdd.Builder userBuilder = UserAdd.builder();
            userBuilder.login(properties.userName);
            String publicKeyData = properties.sshPublicKeyData
            userBuilder.authorizeRSAPublicKey(publicKeyData);
            Statement userBuilderStatement = userBuilder.build();
            options.runScript(userBuilderStatement);
        }
        
        LOG.debug("jclouds using template $template to provision machine in $this for ${setup.callerContext}");
        return template;
    }
    
    private String getPublicHostname(NodeMetadata node, Map allconf) {
        if (allconf?.provider?.equals("aws-ec2")) {
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
        if (node.getPublicAddresses()) {
            return node.getPublicAddresses().iterator().next()
        } else if (node.getHostname()) {
            return node.getHostname()
        } else if (node.getPrivateAddresses()) {
            return node.getPrivateAddresses().iterator().next()
        } else {
            return null
        }
    }
    
    private String getPublicHostnameAws(String ip, Map allconf) {
        Map sshConfig = [:]
        if (allconf.password) sshConfig.password = allconf.password
        if (getPrivateKeyFile()) sshConfig.keyFiles = [ getPrivateKeyFile().getCanonicalPath() ] 
        if (allconf.sshPrivateKeyData) sshConfig.privateKeyData = allconf.sshPrivateKeyData
        // TODO messy way to get an SSH session 
        SshMachineLocation sshLocByIp = new SshMachineLocation(address:ip, username:allconf.userName, config:sshConfig);
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errStream = new ByteArrayOutputStream()
        int exitcode = sshLocByIp.run([out:outStream,err:errStream], "echo `curl --silent --retry 20 http://169.254.169.254/latest/meta-data/public-hostname`; exit")
        String outString = new String(outStream.toByteArray())
        String[] outLines = outString.split("\n")
        for (String line : outLines) {
            if (line.startsWith("ec2-")) return line.trim()
        }
        throw new IllegalStateException("Could not obtain hostname for vm $ip; exitcode="+exitcode+"; stdout="+outString+"; stderr="+new String(errStream.toByteArray()))
    }
    
    public static class JcloudsSshMachineLocation extends SshMachineLocation {
        final JcloudsLocation parent;
        final NodeMetadata node;
        private final RunScriptOnNode.Factory runScriptFactory;
        
        public JcloudsSshMachineLocation(Map flags, JcloudsLocation parent, NodeMetadata node) {
            super(flags);
            this.parent = parent;
            this.node = node;
            
            def context = parent.getComputeService().context;
            runScriptFactory = context.utils().injector().getInstance(RunScriptOnNode.Factory.class);
        }
        /** returns the hostname for use by peers in the same subnet,
         * defaulting to public hostname if nothing special
         * <p>
         * for use e.g. in clouds like amazon where other machines
         * in the same subnet need to use a different IP
         */
        public String getSubnetHostname() {
            if (node.getPrivateAddresses())
                return node.getPrivateAddresses().iterator().next()
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
            ExecResponse result = submitRunScript(commands).get();
            if (result.getExitStatus()!=0)
                throw new IllegalStateException("Error running remote commands (code ${result.exitStatus}): ${commands}");
        }
    
        /**
         * Retrieves the password for this VM, if one exists. The behaviour/implementation is different for different clouds.
         * e.g. on Rackspace, the password for a windows VM is available immediately; on AWS-EC2, for a Windows VM you need 
         * to poll repeatedly until the password is available which can take up to 15 minutes.
         */
        public String waitForPassword() {
            // TODO Hacky; don't want aws specific stuff here but what to do?!
            if (parent.getProvider().equals("aws-ec2")) {
                return JcloudsUtil.waitForPasswordOnAws(parent.getComputeService(), node, 15, TimeUnit.MINUTES);
            } else {
                return node.getCredentials()?.getPassword();
            }
        }
    }
}
