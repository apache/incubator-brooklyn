/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.location.basic.aws;

import static com.cloudsoftcorp.monterey.jclouds.deploymentservice.JCloudsAccountConfig.ROOT_USERNAME;
import static com.cloudsoftcorp.monterey.jclouds.deploymentservice.JCloudsAccountConfig.USERNAME;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.RunScriptOnNodesException;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.ssh.SshClient;

import com.cloudsoftcorp.monterey.clouds.dto.CloudAccountDto;
import com.cloudsoftcorp.monterey.control.provisioning.ProvisioningConstants;
import com.cloudsoftcorp.monterey.jclouds.deploymentservice.JCloudsAccountConfig;
import com.cloudsoftcorp.monterey.jclouds.deploymentservice.JCloudsAccountConfig.ImagePatternType;
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation;
import com.cloudsoftcorp.monterey.location.api.MontereyLocation;
import com.cloudsoftcorp.monterey.location.impl.MontereyActiveLocationImpl;
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo;
import com.cloudsoftcorp.monterey.network.control.wipapi.CloudProviderAccountAndLocationId;
import com.cloudsoftcorp.monterey.node.api.PropertiesContext;
import com.cloudsoftcorp.monterey.provisioning.NodeCreationCoordinator;
import com.cloudsoftcorp.monterey.provisioning.NodeCreator;
import com.cloudsoftcorp.monterey.provisioning.ResourceHandle;
import com.cloudsoftcorp.monterey.provisioning.ResourceProvisioner;
import com.cloudsoftcorp.monterey.provisioning.api.NodeProvisioningListener;
import com.cloudsoftcorp.monterey.provisioning.basic.Machine;
import com.cloudsoftcorp.monterey.provisioning.basic.SshableMachine;
import com.cloudsoftcorp.monterey.provisioning.basic.SshableMachine.HostKeyChecking;
import com.cloudsoftcorp.util.StringUtils;
import com.cloudsoftcorp.util.TimeUtils;
import com.cloudsoftcorp.util.exception.ExceptionUtils;
import com.cloudsoftcorp.util.exception.RuntimeInterruptedException;
import com.cloudsoftcorp.util.io.FileUtils;
import com.cloudsoftcorp.util.logging.LoggingUtils;
import com.cloudsoftcorp.util.osgi.BundleManager;
import com.cloudsoftcorp.util.text.StringEscapeHelper;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public abstract class JCloudsResourceProvisioner implements ResourceProvisioner,NodeCreator {
    
    // TODO Includes workaround for NodeMetadata's equals/hashcode method being wrong.
    // See how result of computeService.runScriptOnNodesMatching is used...
    
    static final Logger LOG = LoggingUtils.getLogger(JCloudsResourceProvisioner.class);

    protected static final int START_SSHABLE_TIMEOUT = 0;

    private final CloudAccountDto account;
    private final Map<String, MontereyLocation> locations;
    private final Map<String, MontereyActiveLocationImpl> activeLocations;
    private final JCloudsAccountConfig resourceProvisionerConf;
    private final JCloudsConfiguration jcloudsConf;
    private final String networkId;
    private final File sshPublicKeyFile;
    private final File sshPrivateKeyFile;
    private final int sshPort;
    private final File truststore;

	private final BundleManager bundleManager;
	private final NodeCreationCoordinator nodeCreationCoordinator;
   
    public JCloudsResourceProvisioner(CloudAccountDto account, Collection<MontereyLocation> locationsList, Properties conf, String networkId,BundleManager bundleManager, NodeCreationCoordinator nodeCreationCoordinator) {
	if (account == null || locationsList == null || conf == null || networkId == null) {
           throw new IllegalArgumentException("Must not be null: account="+account+"; locationsList="+locationsList+"; conf="+conf+"; networkId="+networkId);
       }
       this.account = checkNotNull(account, "account");
       this.resourceProvisionerConf = new JCloudsAccountConfig(conf);
       this.jcloudsConf = checkNotNull(resourceProvisionerConf.getCloudConfig(), "jcloudsConf");
       this.networkId = checkNotNull(networkId, "networkId");
       
       nodeCreationCoordinator.setCreator(this);
       this.bundleManager = bundleManager;
       this.nodeCreationCoordinator = nodeCreationCoordinator;
       
       this.locations = new LinkedHashMap<String,MontereyLocation>(locationsList.size());
       this.activeLocations = new LinkedHashMap<String,MontereyActiveLocationImpl>(locationsList.size());
       for (MontereyLocation location : locationsList) {
           this.locations.put(location.getId(), location);
           
           Map<String,String> metadata = new LinkedHashMap<String, String>();
           metadata.put(Dmn1NetworkInfo.LOCATION_CURRENT_USAGE_KEY, Integer.toString(0));
           metadata.put(Dmn1NetworkInfo.LOCATION_SPARE_CAPACITY_KEY, Integer.toString(Integer.MAX_VALUE));
           MontereyActiveLocationImpl activeLocation = new MontereyActiveLocationImpl(location, account.getAccount(), metadata);
           this.activeLocations.put(location.getId(), activeLocation);
       }
       
       try {
           File tempDir = FileUtils.makeTempDirOnDisk("jcloudsResourceProvisioner.");
           FileUtils.chmod0700(tempDir);
           
           sshPublicKeyFile = FileUtils.createFile(new File(tempDir, "id-publickey"), resourceProvisionerConf.getSshPublicKey().getBytes());
           FileUtils.chmod0600(sshPublicKeyFile);
           
           sshPrivateKeyFile = FileUtils.createFile(new File(tempDir, "id-privatekey"), resourceProvisionerConf.getSshPrivateKey().getBytes());
           FileUtils.chmod0600(sshPrivateKeyFile);

           sshPort = resourceProvisionerConf.getSshPort();
           
	       if (resourceProvisionerConf.getMontereyWebApiSslKeystoreData() != null) {
	           truststore = FileUtils.makeTempFileOnDisk("truststore", Base64.decodeBase64(resourceProvisionerConf.getMontereyWebApiSslKeystoreData().getBytes()));
	           FileUtils.chmod0600(truststore);
	       } else truststore = null;
       } catch (IOException e) {
           throw ExceptionUtils.throwRuntime(e);
       }
   }
   
   @Override
   public Collection<MontereyActiveLocation> getActiveLocations() {
       return Collections.<MontereyActiveLocation>unmodifiableCollection(activeLocations.values());
   }

   @Override
   public MontereyActiveLocationImpl getActiveLocation(String locationId) {
       return activeLocations.get(locationId);
   }
   
   public ResourceHandle createNodeAt(final CloudProviderAccountAndLocationId accountAndLocationId, final String creationId, final PropertiesContext nodeProperties) {
        final List<NodeProvisioningListener> listeners = bundleManager.loadServices(NodeProvisioningListener.class.getName(), null);
        return nodeCreationCoordinator.createAsync(accountAndLocationId, creationId, nodeProperties,listeners);
   }

    /**
     * Returns an IP address of the given node that can be used by other nodes
     * to contact it, as a shell script that evaluates to desired value.
     */
    protected String customScriptSnippetForHostnameRetrieval(NodeMetadata node) {
        return JCloudsUtils.getNodeAddress(node);
    }

    public Statement buildRunAppStatement(NodeMetadata node, PropertiesContext nodeProperties, MontereyLocation location, String creationId, String networkId) throws IOException {
       final String PUBLIC_HOSTNAME_VAR = "PUBLIC_HOSTNAME";
       String hostnameCmd = PUBLIC_HOSTNAME_VAR + "=" + customScriptSnippetForHostnameRetrieval(node);
       
       PropertiesContext propsCopy = new PropertiesContext(nodeProperties);
       
       // TODO Not using SocketAddress.getConstructionString, because then substitution of PUBLIC_HOSTNAME doesn't work  
       //final SocketAddress address = new SocketAddress(new InetSocketAddress(PUBLIC_HOSTNAME_VAR, resourceManagerConf.montereyNodePort));
       String address = PUBLIC_HOSTNAME_VAR+":"+resourceProvisionerConf.getMontereyNodePort();
       propsCopy.getProperties().add(ProvisioningConstants.PREFERRED_SOCKET_ADDRESS_PROPERTY,address);
       propsCopy.getProperties().add(ProvisioningConstants.LPP_HUB_LISTENER_PORT_PROPERTY, ""+resourceProvisionerConf.getMontereyHubLppPort());
       propsCopy.getProperties().add(ProvisioningConstants.NODE_LOCATION_PROPERTY, location.getId());
       propsCopy.getProperties().add(ProvisioningConstants.NODE_ACCOUNT_PROPERTY, account.getAccount().getId());
       propsCopy.getProperties().add(ProvisioningConstants.NODE_CREATION_UID_PROPERTY, creationId);
       propsCopy.getProperties().add(ProvisioningConstants.PREFERRED_HOSTNAME_PROPERTY, PUBLIC_HOSTNAME_VAR);
       propsCopy.getProperties().add(ProvisioningConstants.PREFERRED_HOSTNAME_PROPERTY, PUBLIC_HOSTNAME_VAR);
	   if (resourceProvisionerConf.getMontereyWebApiSslKeystore() != null)
	       propsCopy.getProperties().add(ProvisioningConstants.JAVAX_NET_SSL_TRUSTSTORE, JCloudsAccountConfig.MONTEREY_NETWORK_HOME+"/"+JCloudsAccountConfig.NETWORK_NODE_SSL_TRUSTSTORE_RELATIVE_PATH);

       String escapedProps = StringEscapeHelper.wrapBash(StringUtils.join(propsCopy.getProperties(), "\n"));
       String escapedSubstitutedProps = escapedProps.replaceAll(PUBLIC_HOSTNAME_VAR, "\\$"+PUBLIC_HOSTNAME_VAR);
       
       // Note: need to soruce /etc/profile so that the script can find java on its path
       String startCmd = 
               ". /etc/profile; "+
               JCloudsAccountConfig.NETWORK_NODE_START_SCRIPT_ABSOLUTE_PATH+" "+
               "-key "+creationId+" "+
               escapedSubstitutedProps+" "+
               " > "+JCloudsAccountConfig.MONTEREY_NETWORK_HOME+"/log/remote-launch.log";

       return Statements.newStatementList(
               createStatementToAddConfigFiles(),
               JCloudsUtils.authorizePortInIpTables(resourceProvisionerConf.getMontereyNodePort()),
               JCloudsUtils.authorizePortInIpTables(resourceProvisionerConf.getMontereyHubLppPort()),
               exec(hostnameCmd),
               exec(startCmd));
    }

    protected JCloudsAccountConfig.ImagePatternType getImagePatternType() {
        return ImagePatternType.NAME;
    }
    
    public Template buildTemplate(ComputeService computeService, MontereyLocation location) {
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

    private boolean isSshableMachineReady(SshableMachine machine) {                  
        return machine.waitForSshable(START_SSHABLE_TIMEOUT);
    }
    


    protected void customizeTemplate(TemplateBuilder templateBuilder, TemplateOptions options, JCloudsConfiguration jcloudsConf) {
        // no-op; for overriding
    }
    
    private Statement createStatementToAddConfigFiles() throws IOException {
        List<Statement> statements = Lists.newArrayList();
        if (resourceProvisionerConf.getLoggingFileOverride() != null) {
            statements.add(Statements.appendFile(JCloudsAccountConfig.NETWORK_NODE_SIDE_LOGGING_FILE_ABSOLUTE_PATH,
                    Files.readLines(resourceProvisionerConf.getLoggingFileOverride(), Charsets.UTF_8)));
        }
 
        Statement addConfigurationFiles = Statements.newStatementList(statements.toArray(new Statement[0]));
        return addConfigurationFiles;
    }

   @Override
   final public boolean releaseNode(ResourceHandle handle) {
      return doRelease(handle, true);
   }

   @Override
   final public void onNodeDown(ResourceHandle handle) {
      doRelease(handle, false);
   }

   private boolean doRelease(ResourceHandle rawHandle, boolean graceful) {
      if (!(rawHandle instanceof JCloudsResourceHandle))
         throw new IllegalArgumentException("Passed resource handle is not a JCloudsResourceHandle");

      JCloudsResourceHandle handle = (JCloudsResourceHandle) rawHandle;
      ComputeService computeService = null;
      SshClient sshClient = null;
      try {
         computeService = JCloudsUtils.buildComputeService(jcloudsConf);
         sshClient = computeService.getContext().utils().sshForNode()
               .apply(computeService.getNodeMetadata(handle.getInstanceId()));

         if (graceful) {
            LOG.info("Releasing node, first attempting to kill network-node process: creationId="
                  + handle.getCreationId() + "; instance=" + handle.getInstanceId() + "; machine="
                  + handle.getHostname());
            String creationId = handle.getCreationId();
            try {
                sshClient.connect();
                sshClient.exec(JCloudsAccountConfig.NETWORK_NODE_START_SCRIPT_ABSOLUTE_PATH+ " " + creationId);
            } catch (RuntimeInterruptedException e) {
               throw e;
            } catch (RuntimeException e) {
               LOG.log(Level.INFO, "Failed to kill network-node process; continuing with release of " + handle, e);
            }

            LOG.info("Releasing Node, terminating VM: " + handle);

         } else {
            LOG.info("Releasing node ungracefully, terminating VM: creationId=" + handle.getCreationId()
                  + "; instance=" + handle.getInstanceId() + "; machine=" + handle.getHostname());
         }

         computeService.destroyNode(handle.getInstanceId());
         return true;
         
      } finally {
         if (computeService != null) {
            try {
               computeService.getContext().close();
            } catch (Exception e) {
                LOG.log(Level.INFO, "Problem closing compute-service's context; continuing...", e);
            }
         }
         if (sshClient != null) {
            try {
               sshClient.disconnect();
            } catch (Exception e) {
                LOG.log(Level.INFO, "Problem disconnecting ssh-client; continuing...", e);
            }
         }
         changeLocationUsageCount(handle.getMontereyLocation(), -1);

      }
   }
   
   private void changeLocationUsageCount(MontereyLocation location, int change) {
       MontereyActiveLocationImpl activeLocation = getActiveLocation(location.getId());
       if (activeLocation == null) {
           LOG.warning("Resource provisioner encountered unknown location: location="+location.getId());
           return;
       }
       
       synchronized (activeLocation) {
           String oldUsage = (String) activeLocation.getMetaData().get(Dmn1NetworkInfo.LOCATION_CURRENT_USAGE_KEY);
           int newUsage = (oldUsage != null ? Integer.parseInt(oldUsage) : 0) + change;
           if (newUsage < 0) {
               LOG.warning("Resource provisioner has invalid usage count: location="+location.getId()+"; oldUsage="+oldUsage+"; newUsage="+newUsage+"; defaulting to zero");
               newUsage = 0;
           }
           activeLocation.getMutableMetaData().put(Dmn1NetworkInfo.LOCATION_CURRENT_USAGE_KEY, ""+newUsage);
       }
   }

	@Override
	public JCloudsResourceHandle getResourceHandle( CloudProviderAccountAndLocationId accountAndLocationId, String creationId) {
		final MontereyLocation location = getActiveLocation(accountAndLocationId.getLocationId()).getLocation();
        return new JCloudsResourceHandle(creationId, location);
	}
	
	@Override
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
	
	@Override
	public void createNode(ResourceHandle handle, Machine machine, PropertiesContext nodeProperties) {
		final MontereyLocation location = handle.getMontereyLocation();
		JCloudsMachine jcloudsMachine = (JCloudsMachine)machine;
		NodeMetadata node = jcloudsMachine.getNode();
		String vmHostname = machine.getSshAddress();
		String instanceId = node.getId();
		ComputeService computeService = JCloudsUtils.buildComputeService(jcloudsConf);
		try {
			ExecResponse scriptResult = JCloudsUtils.runScriptOnNode(computeService, node,
                buildRunAppStatement(node, nodeProperties, location, handle.getCreationId(), networkId),
                "start-monterey-network-node");
        if (scriptResult.getExitCode() != 0) {
            if (scriptResult.getOutput().contains("start-monterey-network-node did not start")) {
                // FIXME Hack to get around strange vcloud behaviour
                // Pretend successful!
            } else {
                throw new StartupException("Problem executing network-node-start script: exitCode="+scriptResult.getExitCode()+"; machine=["+node+"]; stderr="+scriptResult.getError()+"; stdout="+scriptResult.getOutput());
            }
        }
        
        LOG.info("Network-node instance " + instanceId + " is now running, starting Monterey node");
        JCloudsResourceHandle jcloudsHandle = (JCloudsResourceHandle)handle;
        jcloudsHandle.finishInitialisation(instanceId, vmHostname);
        changeLocationUsageCount(location, 1);
		
		} catch (RunScriptOnNodesException e) {
			LOG.log(Level.INFO, "Failed to run script on node", e);
			throw ExceptionUtils.throwRuntime(e);
		} catch (IOException e) {
			LOG.log(Level.INFO, "Failed to buildRunAppStatement for node", e);
			throw ExceptionUtils.throwRuntime(e);
		} finally {
			computeService.getContext().close();
		}
	}
}
