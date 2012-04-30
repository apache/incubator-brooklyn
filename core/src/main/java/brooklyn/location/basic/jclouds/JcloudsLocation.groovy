package brooklyn.location.basic.jclouds

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.jclouds.compute.options.RunScriptOptions.Builder.overrideLoginCredentials
import static org.jclouds.scriptbuilder.domain.Statements.exec

import javax.annotation.Nullable

import org.jclouds.compute.ComputeService
import org.jclouds.compute.RunNodesException
import org.jclouds.compute.domain.ExecResponse
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.Template
import org.jclouds.compute.domain.TemplateBuilder
import org.jclouds.compute.options.TemplateOptions
import org.jclouds.domain.Credentials
import org.jclouds.domain.LoginCredentials
import org.jclouds.ec2.compute.options.EC2TemplateOptions
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.domain.Statements
import org.jclouds.scriptbuilder.statements.login.UserAdd
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.AbstractLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.IdGenerator
import brooklyn.util.internal.Repeater

import com.google.common.base.Charsets
import com.google.common.base.Throwables
import com.google.common.collect.Iterables
import com.google.common.io.Files

public class JcloudsLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {

    // TODO Needs a big overhaul of how config is being managed, and what the property names are (particularly for private-keys)
    
    public static final Logger LOG = LoggerFactory.getLogger(JcloudsLocation.class)
    
    public static final String ROOT_USERNAME = "root";
    public static final int START_SSHABLE_TIMEOUT = 5*60*1000;

    private final Map<String,Map<String, ? extends Object>> tagMapping = [:]
    private final Map<JcloudsSshMachineLocation,String> vmInstanceIds = [:]

    JcloudsLocation(Map conf) {
        super(conf)
    }
    
    JcloudsLocation(String identity, String credential, String providerLocationId) {
        this([identity:identity, credential:credential, providerLocationId:providerLocationId])
    }
    
    protected void configure(Map properties) {
        super.configure(properties)
        if (!name) name = conf.providerLocationId ?: conf.provider ?: "default";
	}
    
    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+conf?.identity+":"+name+"]";
    }
    
    public String getProvider() {
        return conf.provider
    }
    
    public Map getConf() { return leftoverProperties; }
    
    public void setTagMapping(Map<String,Map<String, ? extends Object>> val) {
        tagMapping.clear()
        tagMapping.putAll(val)
    }
    
    public void setDefaultImageId(String val) {
        defaultImageId = val
    }
    
    // TODO Decide on semantics. If I give "TomcatServer" and "Ubuntu", then must I get back an image that matches both?
    // Currently, just takes first match that it finds...
    Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        Map<String,Object> result = [:]
        Collection<String> unmatchedTags = []
        tags.each {
            if (tagMapping.get(it) && !result) {
                result.putAll(tagMapping.get(it))
            } else {
                unmatchedTags.add(it)
            }
        }
        if (unmatchedTags) {
            LOG.debug("Location $this, failed to match provisioning tags $unmatchedTags")
        }
        return result
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
    public File getPublicKeyFile() { asFile(conf.publicKeyFile) ?: asFile(conf.sshPublicKey) }
    
    /** returns private key file, if one has been configured */
    public File getPrivateKeyFile() { asFile(conf.privateKeyFile) ?: asFile(conf.sshPrivateKey) }

    public JcloudsSshMachineLocation obtain(Map flags=[:]) throws NoMachinesAvailableException {
        Map allconf = flags + conf;
        Map unusedConf = [:] + allconf
        if (!unusedConf.remove("userName")) allconf.userName = ROOT_USERNAME
        //TODO deprecate supply of data (and of different root key?) to keep it simpler
        if (unusedConf.remove("publicKeyFile")) allconf.sshPublicKeyData = Files.toString(getPublicKeyFile(), Charsets.UTF_8)
        if (unusedConf.remove("privateKeyFile")) allconf.sshPrivateKeyData = Files.toString(getPrivateKeyFile(), Charsets.UTF_8)
        if (unusedConf.remove("sshPublicKey")) allconf.sshPublicKeyData = Files.toString(asFile(allconf.sshPublicKey), Charsets.UTF_8)
        if (unusedConf.remove("sshPrivateKey")) allconf.sshPrivateKeyData = Files.toString(asFile(allconf.sshPrivateKey), Charsets.UTF_8)
        if (unusedConf.remove("rootSshPrivateKey")) allconf.rootSshPrivateKeyData = Files.toString(asFile(allconf.rootSshPrivateKey), Charsets.UTF_8)
        if (unusedConf.remove("rootSshPublicKey")) allconf.rootSshPublicKeyData = Files.toString(asFile(allconf.rootSshPublicKey), Charsets.UTF_8)
        String groupId = (unusedConf.remove("groupId") ?: "brooklyn-"+System.getProperty("user.name")+"-"+IdGenerator.makeRandomId(8))
 
        unusedConf.remove("provider");
        unusedConf.remove("providerLocationId");
        
        ComputeService computeService = JcloudsUtil.buildOrFindComputeService(allconf, unusedConf);
        
        NodeMetadata node = null;
        try {
            LOG.info("Creating VM in "+(allconf.providerLocationId?:allconf.provider));

            Template template = buildTemplate(computeService, allconf.providerLocationId, allconf, unusedConf);
            
            if (!unusedConf.isEmpty())
                LOG.debug("NOTE: unused flags passed to JcloudsLocation.buildTemplate in "+(allconf.providerLocationId?:allconf.provider)+": "+unusedConf);
    
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            if (node == null) {
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in location ${allconf.providerLocationId}");
            }

            String vmIp = JcloudsUtil.getFirstReachableAddress(node);
            Credentials nodeCredentials = node.getCredentials()
            Credentials expectedCredentials = LoginCredentials.fromCredentials(
                allconf.sshPrivateKeyData ? new Credentials(allconf.userName, allconf.sshPrivateKeyData) : nodeCredentials)
            
            // Wait for the VM to be reachable over SSH
            LOG.info("Started VM in ${allconf.providerLocationId ?: allconf.provider}; "+
                "waiting for it to be sshable by ${allconf.userName}@${vmIp}");
            boolean reachable = new Repeater()
                    .repeat()
                    .every(1,SECONDS)
                    .until {
                        Statement statement = Statements.newStatementList(exec('date'))
                        ExecResponse response = computeService.runScriptOnNode(node.getId(), statement,
                                overrideLoginCredentials(expectedCredentials))
                        return response.exitCode == 0 }
                    .limitTimeTo(START_SSHABLE_TIMEOUT,MILLISECONDS)
                    .run()
        
            if (!reachable) {
                throw new IllegalStateException("SSH failed for ${allconf.userName}@${vmIp} after waiting ${START_SSHABLE_TIMEOUT}ms");
            }

            String vmHostname = getPublicHostname(node, allconf)
            Map sshConfig = [:]
            if (getPrivateKeyFile()) sshConfig.keyFiles = getPrivateKeyFile().getCanonicalPath()
            if (allconf.sshPrivateKeyData) sshConfig.privateKey = allconf.sshPrivateKeyData
            JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(this, node,
                    address:vmHostname, 
                    displayName:vmHostname,
                    username:allconf.userName, 
                    config:sshConfig);

            sshLocByHostname.setParentLocation(this)
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
        String id = flags.id
        String hostname = flags.hostname
        String username = flags.userName
        
        LOG.info("Rebinding to VM $id ($username@$hostname), in lclouds location for provider $provider")
        
        // TODO Tidy code below
        Map allconf = flags + conf;
        Map unusedConf = [:] + allconf
        if (!unusedConf.remove("userName")) allconf.userName = ROOT_USERNAME
        //TODO deprecate supply of data (and of different root key?) to keep it simpler
        if (unusedConf.remove("publicKeyFile")) allconf.sshPublicKeyData = Files.toString(getPublicKeyFile(), Charsets.UTF_8)
        if (unusedConf.remove("privateKeyFile")) allconf.sshPrivateKeyData = Files.toString(getPrivateKeyFile(), Charsets.UTF_8)
        if (unusedConf.remove("sshPublicKey")) allconf.sshPublicKeyData = Files.toString(asFile(allconf.sshPublicKey), Charsets.UTF_8)
        if (unusedConf.remove("sshPrivateKey")) allconf.sshPrivateKeyData = Files.toString(asFile(allconf.sshPrivateKey), Charsets.UTF_8)
        if (unusedConf.remove("rootSshPrivateKey")) allconf.rootSshPrivateKeyData = Files.toString(asFile(allconf.rootSshPrivateKey), Charsets.UTF_8)
        if (unusedConf.remove("rootSshPublicKey")) allconf.rootSshPublicKeyData = Files.toString(asFile(allconf.rootSshPublicKey), Charsets.UTF_8)
        String groupId = (unusedConf.remove("groupId") ?: "brooklyn-"+System.getProperty("user.name")+"-"+IdGenerator.makeRandomId(8))
 
        unusedConf.remove("provider");
        unusedConf.remove("providerLocationId");
        
        ComputeService computeService = JcloudsUtil.buildComputeService(allconf, unusedConf);
        NodeMetadata node = computeService.getNodeMetadata(id)
        if (node == null) {
            throw new IllegalArgumentException("Node not found with id "+id)
        }
        
        Map sshConfig = [:]
        if (getPrivateKeyFile()) sshConfig.keyFiles = [ getPrivateKeyFile().getCanonicalPath() ] 
        if (allconf.sshPrivateKeyData) sshConfig.privateKey = allconf.sshPrivateKeyData 
        JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(this, node,
                address:hostname, 
                displayName:hostname,
                username:username, 
                config:sshConfig);

        sshLocByHostname.setParentLocation(this)
        vmInstanceIds.put(sshLocByHostname, node.getId())
            
        return sshLocByHostname
    }
    
    private static File asFile(Object o) {
        if (o in File) return o;
        if (o==null) return o;
        return new File(o.toString());
    }

    private static String fileAsString(Object o) {
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
            hardwareId:     { TemplateBuilder tb, Map props, Object v -> tb.hardwareId(v) },
//            imageSize:      { TemplateBuilder tb, Map props, Object v -> tb.imageSize(v) },  //doesn't exist?
            imageId:        { TemplateBuilder tb, Map props, Object v -> tb.imageId(v) },
            imageDescriptionRegex:        { TemplateBuilder tb, Map props, Object v -> tb.imageDescriptionMatches(v) },
            imageNameRegex:               { TemplateBuilder tb, Map props, Object v -> tb.imageNameMatches(v) },
            defaultImageId:               { TemplateBuilder tb, Map props, Object v ->
                if (!(props.imageId || props.imageDescriptionRegex || props.imageNameRegex
                        || props.imageDescriptionPattern || props.imageNamePattern))
                    tb.imageId(v) 
            },
//deprecated, kept for backwards compatibility:
            imageDescriptionPattern:      { TemplateBuilder tb, Map props, Object v -> tb.imageDescriptionMatches(v) },
            imageNamePattern:             { TemplateBuilder tb, Map props, Object v -> tb.imageNameMatches(v) },
        ];

    public static final Map SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES = [
            securityGroups:    { TemplateOptions t, Map props, Object v -> 
                if (t instanceof EC2TemplateOptions) {
                    String[] securityGroups = (v instanceof Collection) ? v.toArray(new String[0]) : v;
                    t.as(EC2TemplateOptions.class).securityGroups(securityGroups);
                } else {
                    LOG.info("ignoring securityGroups({$v}) in VM creation because not EC2 (${t})");
                }
            },
            inboundPorts:      { TemplateOptions t, Map props, Object v ->
                Object[] inboundPorts = (v instanceof Collection) ? v.toArray(new Integer[0]) : v;
                if (LOG.isDebugEnabled()) LOG.debug("opening inbound ports ${Arrays.toString(v)} for ${t}");
                t.inboundPorts(inboundPorts);
            },
            userMetadata:  { TemplateOptions t, Map props, Object v -> t.userMetadata(v) },
            rootSshPublicKeyData:  { TemplateOptions t, Map props, Object v -> t.authorizePublicKey(v) },
            sshPublicKey:  { TemplateOptions t, Map props, Object v -> /* special; not included here */  },
            userName:  { TemplateOptions t, Map props, Object v -> /* special; not included here */ },
        ];

    private Template buildTemplate(ComputeService computeService, String providerLocationId, Map<String,? extends Object> properties, Map unusedConf) {
        TemplateBuilder templateBuilder = computeService.templateBuilder();
 
        if (providerLocationId!=null) {
            templateBuilder.locationId(providerLocationId);
        }
        
        SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.each { name, code ->
            if (unusedConf.remove(name)!=null)
                code.call(templateBuilder, properties, properties.get(name));
        }
        
        Template template = templateBuilder.build();
        TemplateOptions options = template.getOptions();
        
        SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.each { name, code ->
            if (unusedConf.remove(name))
                code.call(options, properties, properties.get(name));
        }
        
        if (properties.userName == ROOT_USERNAME && properties.sshPublicKeyData) {
            String keyData = properties.sshPublicKeyData
            options.authorizePublicKey(keyData)
        }
        //NB: we ignore private key here because, by default we probably should not be installing it remotely;
        //also, it may not be valid for first login (it is created before login e.g. on amazon, so valid there;
        //but not elsewhere, e.g. on rackspace)
        
        // Setup the user
        if (properties.userName && properties.userName != ROOT_USERNAME) {
            UserAdd.Builder userBuilder = UserAdd.builder();
            userBuilder.login(properties.userName);
            String publicKeyData = properties.sshPublicKeyData
            userBuilder.authorizeRSAPublicKey(publicKeyData);
            Statement userBuilderStatement = userBuilder.build();
            options.runScript(userBuilderStatement);
        }
                      
        return template;
    }
    
    private String getPublicHostname(NodeMetadata node, Map allconf) {
        if (allconf?.provider?.equals("aws-ec2")) {
            return getPublicHostnameAws(node, allconf);
        } else {
            return getPublicHostnameGeneric(node, allconf);
        }
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
    
    private String getPublicHostnameAws(NodeMetadata node, Map allconf) {
        String vmIp = JcloudsUtil.getFirstReachableAddress(node);
        
        Map sshConfig = [:]
        if (getPrivateKeyFile()) sshConfig.keyFiles = getPrivateKeyFile().getCanonicalPath() 
        if (allconf.sshPrivateKeyData) sshConfig.privateKey = allconf.sshPrivateKeyData 
        SshMachineLocation sshLocByIp = new SshMachineLocation(address:vmIp, username:allconf.userName, config:sshConfig);
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        ByteArrayOutputStream errStream = new ByteArrayOutputStream()
        int exitcode = sshLocByIp.run([out:outStream,err:errStream], "echo `curl --silent --retry 20 http://169.254.169.254/latest/meta-data/public-hostname`; exit")
        String outString = new String(outStream.toByteArray())
        String[] outLines = outString.split("\n")
        for (String line : outLines) {
            if (line.startsWith("ec2-")) return line.trim()
        }
        throw new IllegalStateException("Could not obtain hostname for vm $vmIp ("+node.getId()+"); exitcode="+exitcode+"; stdout="+outString+"; stderr="+new String(errStream.toByteArray()))
    }
	
	public static class JcloudsSshMachineLocation extends SshMachineLocation {
		final JcloudsLocation parent;
		final NodeMetadata node;
		public JcloudsSshMachineLocation(Map flags, JcloudsLocation parent, NodeMetadata node) {
			super(flags);
			this.parent = parent;
			this.node = node;
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
	}
}
