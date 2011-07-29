package com.cloudsoftcorp.monterey.brooklyn.entity

import com.cloudsoftcorp.util.Loggers;

import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Collection
import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.BrooklynSystemProperties

import com.cloudsoftcorp.monterey.clouds.NetworkId
import com.cloudsoftcorp.monterey.clouds.basic.DeploymentUtils
import com.cloudsoftcorp.monterey.clouds.dto.CloudEnvironmentDto
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.DeploymentWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.Dmn1NetworkInfoWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.PingWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.deployment.MontereyDeploymentDescriptor
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.TimeUtils
import com.cloudsoftcorp.util.exception.RuntimeWrappedException
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.osgi.BundleSet
import com.cloudsoftcorp.util.proc.ProcessExecutionFailureException
import com.cloudsoftcorp.util.web.client.CredentialsConfig
import com.cloudsoftcorp.util.web.server.WebConfig
import com.cloudsoftcorp.util.web.server.WebServer
import com.google.common.annotations.VisibleForTesting
import com.google.gson.Gson

/**
 * Represents a Monterey network.
 * 
 * @author aled
 */
public class MontereyManagementNode extends AbstractEntity implements Startable {
    private static final Logger LOG = Loggers.getLogger(MontereyManagementNode.class);

    public static final BasicConfigKey<String> SUGGESTED_MANAGEMENT_NODE_INSTALL_DIR = [String.class, "monterey.managementnode.installdir", "Monterey management node installation directory" ]
    public static final BasicConfigKey<Collection> SUGGESTED_WEB_USERS_CREDENTIAL = [Collection.class, "monterey.managementnode.webusers", "Monterey management node web-user credentials" ]

    public static final BasicAttributeSensor<URL> MANAGEMENT_URL = [ URL.class, "monterey.management-url", "Management URL" ]

    /** up, down, etc? */
    public static final BasicAttributeSensor<String> STATUS = [ String, "monterey.status", "Status" ]

    private static final String DEFAULT_MANAGEMENT_NODE_INSTALL_DIR = "/home/monterey/monterey-management-node"
    
    private final Gson gson;

    private MontereyNetworkConfig config = new MontereyNetworkConfig();
    
    private MachineProvisioningLocation machineProvisioner;
    private SshMachineLocation machine;
    private MontereyNetworkConnectionDetails connectionDetails;
    private NetworkId networkId;
    
    public MontereyManagementNode(Map props=[:], owner=null) {
        super(props, owner);
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();
    }

    public void setConfig(MontereyNetworkConfig val) {
        this.config = val;
    }

    public void setNetworkId(NetworkId val) {
        this.@networkId = val;
    }

    // TODO Use attribute? Method is used to guard call to stop. Or should stop be idempotent?
    public boolean isRunning() {
        return machine != null;
    }
    
    @VisibleForTesting    
    LocationRegistry getLocationRegistry() {
        return locationRegistry;
    }

    public void dispose() {
        if (monitoringTask != null) monitoringTask.cancel(true);
    }
    
    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        startInLocation locs
    }

    public void startInLocation(Collection<Location> locs) {
        // TODO how do we deal with different types of location?
        
        SshMachineLocation machineLoc = locs.find({ it instanceof SshMachineLocation });
        MachineProvisioningLocation provisioningLoc = locs.find({ it instanceof MachineProvisioningLocation });
        if (machineLoc) {
            startInLocation(machineLoc)
        } else if (provisioningLoc) {
            startInLocation(provisioningLoc)
        } else {
            throw new IllegalArgumentException("Unsupported location types creating monterey network, $locations")
        }
    }

    public void startInLocation(MachineProvisioningLocation loc) {
        Map<String,Object> flags = loc.getProvisioningFlags([MontereyManagementNode.class.getName()])
        SshMachineLocation machine = loc.obtain(flags)
        if (machine == null) throw new NoMachinesAvailableException(loc)
        startInLocation(machine)
        machineProvisioner = loc
    }

    public void startInLocation(SshMachineLocation machine) {
        /*
         * TODO: Assumes the following are already set on SshMachine:
         * sshAddress
         * sshPort
         * sshUsername
         * sshKey/sshKeyFile
         * HostKeyChecking hostKeyChecking = HostKeyChecking.NO;
         */

        LOG.info("Creating new monterey network "+networkId+" on "+machine);

        locations << machine
        
        Collection<UserCredentialsConfig> webUsersCredentials = getConfig(SUGGESTED_WEB_USERS_CREDENTIAL) ?: []
        CredentialsConfig webAdminCredential = DeploymentUtils.findWebApiAdminCredential(webUsersCredentials);
    
        File webUsersConfFile = DeploymentUtils.toEncryptedWebUsersConfFile(webUsersCredentials);
        String username = System.getenv("USER");

        String managementNodeInstallDir = getConfig(SUGGESTED_MANAGEMENT_NODE_INSTALL_DIR) ?: DEFAULT_MANAGEMENT_NODE_INSTALL_DIR
        
        WebConfig web = new WebConfig(true, config.getMontereyWebApiPort(), config.getMontereyWebApiProtocol(), null);
        web.setSslKeystore(managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH);
        web.setSslKeystorePassword(config.getMontereyWebApiSslKeystorePassword());
        web.setSslKeyPassword(config.getMontereyWebApiSslKeyPassword());
        File webConf = DeploymentUtils.toWebConfFile(web);

        try {
            machine.copyTo(webUsersConfFile, managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEBUSERS_FILE_RELATIVE_PATH);

            if (config.getLoggingFileOverride() != null) {
                machine.copyTo(config.getLoggingFileOverride(), managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_LOGGING_FILE_OVERRIDE_RELATIVE_PATH);
                machine.copyTo(config.getLoggingFileOverride(), managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_LOGGING_FILE_RELATIVE_PATH);
            }

            machine.copyTo(webConf, managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH);
            if (config.getMontereyWebApiProtocol().equals(WebServer.HTTPS)) {
                machine.copyTo(config.getMontereyWebApiSslKeystore(), managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH);
            }

            URL managementUrl = new URL(config.getMontereyWebApiProtocol()+"://"+machine.getAddress().getHostName()+":"+config.getMontereyWebApiPort());
            this.machine = machine;

            // Convenient for testing: create the management-node directly in-memory, rather than starting it in a separate process
            // Please leave this commented out code here, to make subsequent debugging easier!
            // Or you could refactor to have a private static final constant that switches the behaviour?
            //            MainArguments mainArgs = new MainArguments(new File(managementNodeInstallDir), null, null, null, null, null, networkId.getId());
            //            new ManagementNodeStarter(mainArgs).start();

            machine.run(out: System.out,
                    managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_START_SCRIPT_RELATIVE_PATH+
                    " -address "+machine.getAddress().getHostName()+
                    " -port "+Integer.toString(config.getMontereyNodePort())+
                    " -networkId "+networkId.getId()+
                    " -key "+networkId.getId()+
                    " -webConfig "+managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH+";"+
                    "exit");

            // TODO Use repeat...until...?
            PingWebProxy pingWebProxy = new PingWebProxy(managementUrl.toString(), webAdminCredential,
                    (config.getMontereyWebApiSslKeystore() != null ? config.getMontereyWebApiSslKeystore().getPath() : null),
                    config.getMontereyWebApiSslKeystorePassword());
            boolean reachable = pingWebProxy.waitForReachable(MontereyNetworkConfig.TIMEOUT_FOR_NEW_NETWORK_ON_HOST);
            if (!reachable) {
                throw new IllegalStateException("Management plane not reachable via web-api within "+TimeUtils.makeTimeString(MontereyNetworkConfig.TIMEOUT_FOR_NEW_NETWORK_ON_HOST)+": url="+managementUrl);
            }

            PlumberWebProxy plumberProxy = new PlumberWebProxy(managementUrl, gson, webAdminCredential);
            NodeId controlNodeId = plumberProxy.getControlNodeId();
            
            this.connectionDetails = new MontereyNetworkConnectionDetails(networkId, managementUrl, webAdminCredential, controlNodeId, controlNodeId);
            
            setAttribute MANAGEMENT_URL, managementUrl

            LOG.info("Created new monterey management node: "+connectionDetails);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error creating monterey management node", e);

            if (BrooklynSystemProperties.DEBUG.isEnabled()) {
                // Not releasing failed instance, because that would make debugging hard!
                LOG.log(Level.WARNING, "Error creating monterey management node; leaving failed instance "+machine, e);
            } else {
                LOG.log(Level.WARNING, "Error creating monterey management node; terminating failed instance "+machine, e);
                try {
                    shutdownManagementNodeProcess(config, machine, networkId);
                } catch (ProcessExecutionFailureException e2) {
                    LOG.log(Level.WARNING, "Error cleaning up monterey management node after failure to start: machine="+machine, e2);
                }
            }

            throw new RuntimeWrappedException("Error creating monterey management node on "+machine, e);
        }
    }

    public void deployCloudEnvironment(CloudEnvironmentDto cloudEnvironmentDto) {
        int DEPLOY_TIMEOUT = 5*60*1000;
        DeploymentWebProxy deployer = new DeploymentWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential, DEPLOY_TIMEOUT);
        deployer.deployCloudEnvironment(cloudEnvironmentDto);
    }

    public void deployApplication(MontereyDeploymentDescriptor descriptor, BundleSet bundles) {
        int DEPLOY_TIMEOUT = 5*60*1000;
        DeploymentWebProxy deployer = new DeploymentWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential, DEPLOY_TIMEOUT);
        boolean result = deployer.deployApplication(descriptor, bundles);
    }

    @Override
    public void stop() {
        // TODO Guard so can only shutdown if network nodes are not running?
        if (machine == null) {
            LOG.warning("stop() doing nothing, because monterey management node is not running")
            return
        }
        shutdownManagementNodeProcess(this.config, machine, networkId)
        
        machineProvisioner?.release(machine)
        machine = null;
    }

    private void shutdownManagementNodeProcess(MontereyNetworkConfig config, SshMachineLocation machine, NetworkId networkId) {
        String managementNodeInstallDir = getConfig(SUGGESTED_MANAGEMENT_NODE_INSTALL_DIR) ?: DEFAULT_MANAGEMENT_NODE_INSTALL_DIR
        String killScript = managementNodeInstallDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_KILL_SCRIPT_RELATIVE_PATH;
        try {
            LOG.info("Releasing management node on "+toString());
            machine.run(out: System.out,
                    killScript+" -key "+networkId.getId()+";"+
                    "exit");

        } catch (IllegalStateException e) {
            if (e.toString().contains("No such process")) {
                // the process hadn't started or was killed externally? Our work is done.
                LOG.info("Management node process not running; termination is a no-op: networkId="+networkId+"; machine="+machine);
            } else {
                LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+machine, e);
            }
        } catch (ProcessExecutionFailureException e) {
            LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+machine, e);

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+machine, e);
        }
    }

    private boolean updateStatus() {
        PingWebProxy pinger = new PingWebProxy(connectionDetails.managementUrl, connectionDetails.webApiAdminCredential);
        boolean isup = pinger.ping();
        String status = (isup) ? "UP" : "DOWN";
        setAttribute(STATUS, status);
        return isup;
    }
    
    DeploymentWebProxy getDeploymentProxy() {
        return new DeploymentWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential);
    }
    
    Dmn1NetworkInfo getNetworkInfo() {
        return new Dmn1NetworkInfoWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential);
    }
    
    MontereyNetworkConnectionDetails getConnectionDetails() {
        return connectionDetails
    }
}
