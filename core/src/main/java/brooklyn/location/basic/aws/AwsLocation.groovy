package brooklyn.location.basic.aws
import brooklyn.location.MachineLocation;
import brooklyn.location.NoMachinesAvailableException;

package brooklyn.location.basic

import brooklyn.location.MachineProvisioningLocation;

public class AwsLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {

    public T obtain() throws NoMachinesAvailableException {
        
    }

    void release(T machine) {
        
    }
    
    public Template buildTemplate(ComputeService computeService, Map) {
        TemplateBuilder templateBuilder = computeService.templateBuilder();
 
        if (jcloudsConf.getMinRam() > 0) {
            templateBuilder.minRam(jcloudsConf.getMinRam());
        }
        
        if (jcloudsConf.getHardwareId() != null && jcloudsConf.getHardwareId().length() > 0) {
            templateBuilder.hardwareId(jcloudsConf.getHardwareId());
        }
        
        if (jcloudsConf.getImageId() != null && jcloudsConf.getImageId().length() > 0) {
            templateBuilder.imageId(jcloudsConf.getImageId());
        }

        if (jcloudsConf.getImagePattern() != null && jcloudsConf.getImagePattern().length() > 0) {
            switch (getImagePatternType()) {
                case NAME:
                    templateBuilder.imageNameMatches(jcloudsConf.getImagePattern());
                    break;
                case DESCRIPTION:
                    templateBuilder.imageDescriptionMatches(jcloudsConf.getImagePattern());
                    break;
                default:
                    throw new IllegalStateException("Unhandled imagePatternType: "+getImagePatternType());
            }
        }

        if (location.getProviderLocationId() != null && location.getProviderLocationId().length() > 0) {
            templateBuilder.locationId(location.getProviderLocationId());
        }
        
        Template template = templateBuilder.build();
        TemplateOptions options = template.getOptions();
        
        options.inboundPorts(resourceProvisionerConf.getSshPort(), resourceProvisionerConf.getMontereyNodePort(), resourceProvisionerConf.getMontereyHubLppPort());
        options.authorizePublicKey(resourceProvisionerConf.getSshPublicKey());
        options.overrideLoginCredentialWith(resourceProvisionerConf.getSshPrivateKey());
        
        customizeTemplate(templateBuilder, options, jcloudsConf);
        
        // setup the users
        Statement setupUserStatement = JCloudsUtils.setupUserAndExecuteStatements(USERNAME, resourceProvisionerConf.getSshPublicKey(),
                Collections.<Statement>emptyList());
        options.runScript(setupUserStatement);
        
        return template;
    }
    
    public Machine createMachine(ResourceHandle handle) {
        final MontereyLocation location = getActiveLocation(handle.getMontereyLocation().getId()).getLocation();
        
        ComputeService computeService = JCloudsUtils.buildComputeService(jcloudsConf);
        NodeMetadata node = null;
        try {
            // Create the VM
            LOG.info("Creating VM for network-node in "+location.getProviderLocationId()+", for network "+networkId);
            
            Template template = buildTemplate(computeService, location);
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(networkId.toLowerCase(), 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            if (node == null) {
                throw new StartupException("No nodes returned by jclouds create-nodes");
            }

            String vmHostname = JCloudsUtils.getFirstReachableAddress(node);

            // Wait for the VM to be reachable over SSH
            LOG.info("Started VM; waiting for it to be sshable by "+ROOT_USERNAME+"@"+vmHostname);
            SshableMachine sshableMachine = new SshableMachine(vmHostname, ROOT_USERNAME, sshPrivateKeyFile, HostKeyChecking.NO, sshPort);
            if (!isSshableMachineReady(sshableMachine)) {
                throw new StartupException("SSH failed for "+ROOT_USERNAME+"@"+vmHostname+" after waiting "+TimeUtils.makeTimeString(START_SSHABLE_TIMEOUT));
            }
            if (resourceProvisionerConf.getMontereyWebApiSslKeystore() != null) {
                try {
                    sshableMachine.copyToHost(truststore.getPath(), JCloudsAccountConfig.MONTEREY_NETWORK_HOME+"/"+JCloudsAccountConfig.NETWORK_NODE_SSL_TRUSTSTORE_RELATIVE_PATH);
                } catch (IOException ie) {
                    LOG.log(Level.INFO, "Failed to copy truststore to VM", ie);
                    throw new StartupException("Failed to copy keystore to VM "+vmHostname);
                }
            }

            return new JCloudsMachine(sshableMachine,node);
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
    
}
