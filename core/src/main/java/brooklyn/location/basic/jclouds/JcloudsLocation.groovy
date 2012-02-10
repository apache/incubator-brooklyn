package brooklyn.location.basic.jclouds

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.jclouds.compute.options.RunScriptOptions.Builder.overrideCredentialsWith
import static org.jclouds.scriptbuilder.domain.Statements.exec

import org.jclouds.compute.ComputeService
import org.jclouds.compute.RunNodesException
import org.jclouds.compute.domain.ExecResponse
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.Template
import org.jclouds.compute.domain.TemplateBuilder
import org.jclouds.compute.options.TemplateOptions
import org.jclouds.domain.Credentials
import org.jclouds.ec2.compute.options.EC2TemplateOptions
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.domain.Statements
import org.jclouds.scriptbuilder.statements.login.UserAdd

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

    public static final String ROOT_USERNAME = "root";
    public static final int START_SSHABLE_TIMEOUT = 5*60*1000;

    private final Map<String,Map<String, ? extends Object>> tagMapping = [:]
    private final Map<SshMachineLocation,String> vmInstanceIds = [:]

    JcloudsLocation(Map conf) {
        super(conf)
    }
    
    JcloudsLocation(String identity, String credential, String providerLocationId) {
        this([identity:identity, credential:credential, providerLocationId:providerLocationId])
    }
    
    protected void configure(Map properties) {
        super.configure(properties)
        if (!name) name = conf.providerLocationId ?: "default";
	}
    
    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+conf?.identity+":"+name+"]";
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
            LOG.info("Location $this, failed to match provisioning tags $unmatchedTags")
        }
        return result
    }
    
    public SshMachineLocation obtain(Map flags=[:]) throws NoMachinesAvailableException {
        Map allconf = flags + conf
        if (!allconf.userName) allconf.userName = ROOT_USERNAME
        if (allconf.sshPublicKey) allconf.sshPublicKeyData = Files.toString(asFile(allconf.sshPublicKey), Charsets.UTF_8)
        if (allconf.sshPrivateKey) allconf.sshPrivateKeyData = Files.toString(asFile(allconf.sshPrivateKey), Charsets.UTF_8)
        if (allconf.rootSshPrivateKey) allconf.rootSshPrivateKeyData = Files.toString(asFile(allconf.rootSshPrivateKey), Charsets.UTF_8)
        if (allconf.rootSshPublicKey) allconf.rootSshPublicKeyData = Files.toString(asFile(allconf.rootSshPublicKey), Charsets.UTF_8)
        String groupId = (allconf.groupId ?: IdGenerator.makeRandomId(8))
 
        ComputeService computeService = JcloudsUtil.buildComputeService(allconf);
        
        NodeMetadata node = null;
        try {
            LOG.info("Creating VM in "+allconf.providerLocationId);

            Template template = buildTemplate(computeService, allconf.providerLocationId, allconf);
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            if (node == null) {
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in location ${allconf.providerLocationId}");
            }

            String vmIp = JcloudsUtil.getFirstReachableAddress(node);
            Credentials nodeCredentials = node.getCredentials()
            Credentials expectedCredentials = allconf.sshPrivateKeyData ? new Credentials(allconf.userName, allconf.sshPrivateKeyData) : nodeCredentials
            
            // Wait for the VM to be reachable over SSH
            LOG.info("Started VM in ${allconf.providerLocationId}; waiting for it to be sshable by "+allconf.userName+"@"+vmIp);
            boolean reachable = new Repeater()
                    .repeat()
                    .every(1,SECONDS)
                    .until {
                        Statement statement = Statements.newStatementList(exec('date'))
                        ExecResponse response = computeService.runScriptOnNode(node.getId(), statement,
                                overrideCredentialsWith(expectedCredentials))
                        return response.exitCode == 0 }
                    .limitTimeTo(START_SSHABLE_TIMEOUT,MILLISECONDS)
                    .run()
        
            if (!reachable) {
                throw new IllegalStateException("SSH failed for "+allconf.userName+"@"+vmIp+" after waiting "+START_SSHABLE_TIMEOUT+"ms");
            }

            String vmHostname = getPublicHostname(node, allconf)
            Map sshConfig = [:]
            if (allconf.sshPrivateKey) sshConfig.keyFiles = [ fileAsString(allconf.sshPrivateKey) ]
            SshMachineLocation sshLocByHostname = new SshMachineLocation(
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
            computeService.getContext().close();
        }

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
            computeService = JcloudsUtil.buildComputeService(conf);
            computeService.destroyNode(instanceId);
        } catch (Exception e) {
            LOG.error "Problem releasing machine $machine in $this, instance id $instanceId; discarding instance and continuing...", e
            Throwables.propagate(e)
        } finally {
            if (computeService != null) {
                try {
                    computeService.getContext().close();
                } catch (Exception e) {
                    LOG.error "Problem closing compute-service's context; continuing...", e
                }
            }
        }
    }
    
    private Template buildTemplate(ComputeService computeService, String providerLocationId, Map<String,? extends Object> properties) {
        TemplateBuilder templateBuilder = computeService.templateBuilder();
 
        if (properties.minRam) {
            templateBuilder.minRam(properties.minRam);
        }
        
        if (properties.hardwareId) {
            templateBuilder.hardwareId(properties.hardwareId);
        }
        
        if (properties.imageSize) {
            templateBuilder.imageSize(properties.imageSize);
        }
        
        if (properties.imageId) {
            templateBuilder.imageId(properties.imageId);
        }

        if (properties.imageDescriptionPattern) {
            templateBuilder.imageDescriptionMatches(properties.imageDescriptionPattern);
        }

        if (properties.imageNamePattern) {
            templateBuilder.imageNameMatches(properties.imageNamePattern);
        }

        if (!(properties.imageId || properties.imageDescriptionPattern || properties.imageNamePattern) && properties.defaultImageId) {
            templateBuilder.imageId(properties.defaultImageId);
        }

        if (providerLocationId!=null) {
            templateBuilder.locationId(providerLocationId);
        }
        
        Template template = templateBuilder.build();
        TemplateOptions options = template.getOptions();
        
        if (properties.securityGroups) {
            String[] securityGroups = (properties.securityGroups instanceof Collection) ? properties.securityGroups.toArray(new String[0]): properties.securityGroups
            template.getOptions().as(EC2TemplateOptions.class).securityGroups(securityGroups);
        }
        if (properties.inboundPorts) {
            Object[] inboundPorts = (properties.inboundPorts instanceof Collection) ? properties.inboundPorts.toArray(new Integer[0]): properties.inboundPorts
            options.inboundPorts(inboundPorts);
        }
        if ((properties.userName == ROOT_USERNAME && properties.sshPublicKeyData) || properties.rootSshPublicKeyData) {
            String keyData = properties.rootSshPublicKeyData ?: properties.sshPublicKeyData
            options.authorizePublicKey(keyData)
        }
        if ((properties.userName == ROOT_USERNAME && properties.sshPrivateKey) || properties.rootSshPrivateKey) {
            String keyData = properties.rootSshPrivateKeyData ?: properties.sshPrivateKeyData
            options.overrideLoginCredentialWith(keyData)
            options.overrideLoginUserWith(ROOT_USERNAME)
        }
        
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
        if (allconf.provider.equals("aws-ec2")) {
            return getPublicHostnameAws(node, allconf);
        } else {
            return getPublicHostnameGeneric(node, allconf);
        }
    }
    
    private String getPublicHostnameGeneric(NodeMetadata node, Map allconf) {
        if (node.getHostname()) {
            return node.getHostname()
        } else if (node.getPublicAddresses()) {
            return node.getPublicAddresses().iterator().next()
        } else if (node.getPrivateAddresses()) {
            return node.getPrivateAddresses().iterator().next()
        } else {
            return null
        }
    }
    
    private String getPublicHostnameAws(NodeMetadata node, Map allconf) {
        String vmIp = JcloudsUtil.getFirstReachableAddress(node);
        
        Map sshConfig = [:]
        if (allconf.sshPrivateKey) sshConfig.keyFiles = [ fileAsString(allconf.sshPrivateKey) ]
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
}
