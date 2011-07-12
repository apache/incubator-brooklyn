package brooklyn.location.basic.aws

import java.util.Collection
import java.util.Map
import java.util.concurrent.TimeUnit
import java.util.logging.Level

import org.jclouds.compute.ComputeService
import org.jclouds.compute.RunNodesException
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.Template
import org.jclouds.compute.domain.TemplateBuilder
import org.jclouds.compute.options.TemplateOptions
import org.jclouds.scriptbuilder.domain.Statement

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.AbstractLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.internal.Repeater

import com.google.common.collect.Iterables

public class AwsLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {

    public static final String ROOT_USERNAME = "root";
    public static final int START_SSHABLE_TIMEOUT = 15*60*1000;

    private final Map conf = [:];
    
    private final Map<String,Map<String, ? extends Object>> tagMapping = [:]
    private String defaultImageId
    private final Map<SshMachineLocation,String> vmInstanceIds = [:]

    AwsLocation(Map conf) {
        this.conf.putAll(conf)
        this.conf.provider = "aws-ec2"
    }
    
    AwsLocation(String identity, String credential, String providerLocationId) {
        this([identity:identity, credential:credential, providerLocationId:providerLocationId])
    }
    
    public void setSshPublicKey(String val) {
        conf.putAt("sshPublicKey", val)
    }
    
    public void setSshPrivateKey(String val) {
        conf.putAt("sshPrivateKey", val)
    }
    
    public void setUsername(String val) {
        conf.putAt("username", val)
    }
    
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
            LOG.info("AWS location $this, failed to match provisioning tags $unmatchedTags")
        }
        return result
    }
    
    public SshMachineLocation obtain(Map flags=[:]) throws NoMachinesAvailableException {
        Map allconf = union(flags, conf)
        String groupId = allconf.groupId ?: LanguageUtils.newUid();
        if (!allconf.imageId) allconf.imageId = defaultImageId
        
        ComputeService computeService = JCloudsUtil.buildComputeService(allconf);
        
        NodeMetadata node = null;
        try {
            LOG.info("Creating VM in "+allconf.providerLocationId);
            
            Template template = buildTemplate(computeService, allconf.providerLocationId, allconf);
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            if (node == null) {
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in location ${allconf.providerLocationId}");
            }

            String vmHostname = JCloudsUtil.getFirstReachableAddress(node);
            SshMachineLocation sshLoc = new SshMachineLocation(address:vmHostname, username:ROOT_USERNAME);
            
            // Wait for the VM to be reachable over SSH
            LOG.info("Started VM; waiting for it to be sshable by "+ROOT_USERNAME+"@"+vmHostname);
            boolean reachable = new Repeater()
                    .repeat( {} )
                    .every(1, TimeUnit.SECOND)
                    .until( {sshLoc.isSshable()} )
                    .limitTimeTo(START_SSHABLE_TIMEOUT, TimeUnit.MILLISECONDS)
                    .run()
        
            if (!reachable) {
                throw new IllegalStateException("SSH failed for "+ROOT_USERNAME+"@"+vmHostname+" after waiting "+START_SSHABLE_TIMEOUT+"ms");
            }

            sshLoc.setParentLocation(this)
            vmInstanceIds.put(sshLoc, node.getId())
            
            return sshLoc
        } catch (RunNodesException e) {
            if (e.getNodeErrors().size() > 0) {
                node = Iterables.get(e.getNodeErrors().keySet(), 0);
            }
            LOG.log(Level.INFO, "Failed to start VM", e);
            throw ExceptionUtils.throwRuntime(e);
        } finally {
            computeService.getContext().close();
        }

    }

    void release(SshMachineLocation machine) {
        String instanceId = vmInstanceIds.remove(machine)
        if (!instanceId) {
            throw new IllegalArgumentException("Unknown AWS machine "+machine)
        }
        
        LOG.info("Releasing machine $machine in $this, instance id $instanceId");
        
        ComputeService computeService = null;
        try {
            computeService = JCloudsUtil.buildComputeService(conf);
            computeService.destroyNode(instanceId);

        } finally {
            if (computeService != null) {
                try {
                    computeService.getContext().close();
                } catch (Exception e) {
                    LOG.log(Level.INFO, "Problem closing compute-service's context; continuing...", e);
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
            templateBuilder.hardwareId(hardwareId);
        }
        
        if (properties.imageId) {
            templateBuilder.imageId(properties.imageId);
        }

        if (properties.imagePattern) {
            switch (getImagePatternType()) {
                case NAME:
                    templateBuilder.imageNameMatches(properties.imagePattern);
                    break;
                case DESCRIPTION:
                    templateBuilder.imageDescriptionMatches(properties.imagePattern);
                    break;
                default:
                    throw new IllegalStateException("Unhandled imagePatternType: "+getImagePatternType());
            }
        }

        templateBuilder.locationId(providerLocationId);
        
        Template template = templateBuilder.build();
        TemplateOptions options = template.getOptions();
        
        if (properties.inboundPorts) {
            options.inboundPorts(properties.inboundPorts);
        }
        if (properties.sshPublicKey) {
            options.authorizePublicKey(properties.sshPublicKey);
        }
        if (properties.sshPrivateKey) {
            options.overrideLoginCredentialWith(properties.sshPrivateKey);
        }
        
        // setup the users
        if (properties.username) {
            Statement setupUserStatement = JCloudsUtil.setupUserAndExecuteStatements(properties.username, properties.sshPublicKey,
                    Collections.<Statement>emptyList());
            options.runScript(setupUserStatement);
        }
                
        return template;
    }
    
    private ImagePatternType getImagePatternType() {
        return ImagePatternType.DESCRIPTION;
    }

    private String customScriptSnippetForHostnameRetrieval(NodeMetadata node) {
        return "`curl --silent --retry 20 http://169.254.169.254/latest/meta-data/public-hostname`";
    }
    
    private static Map union(Map... maps) {
        Map result = [:]
        maps.each {
            result.putAll(it)
        }
        return result
    }
    
    enum ImagePatternType {
        NAME,
        DESCRIPTION;
    }
}
