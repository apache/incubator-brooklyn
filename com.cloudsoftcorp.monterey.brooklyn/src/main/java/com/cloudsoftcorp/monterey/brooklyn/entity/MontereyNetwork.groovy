package com.cloudsoftcorp.monterey.brooklyn.entity

import java.util.Map;

import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.AttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.BrooklynSystemProperties
import brooklyn.util.internal.EntityStartUtils

import com.cloudsoftcorp.monterey.clouds.NetworkId
import com.cloudsoftcorp.monterey.clouds.basic.DeploymentUtils
import com.cloudsoftcorp.monterey.clouds.dto.CloudEnvironmentDto
import com.cloudsoftcorp.monterey.control.api.SegmentSummary
import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.location.api.MontereyLocation
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.DeploymentWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.Dmn1NetworkInfoWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.PingWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.deployment.MontereyDeploymentDescriptor
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediationWorkrateItemNames
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
import com.google.gson.Gson

/**
 * Represents a Monterey network.
 * 
 * @author aled
 */
public class MontereyNetwork extends AbstractEntity implements Startable { // FIXME , AbstractGroup

    private final Logger LOG = Loggers.getLogger(MontereyNetwork.class);
    /*
     * FIXME, work in progress
     * 
     * Poll for application name.
     * Poll for status.
     * Poll for workrates.
     * Add/remove nodes/segments as they are created/deleted.
     * Add/remove nodes as their type changes.
     */

    private static final Logger logger = Loggers.getLogger(MontereyNetwork.class);

    public static final AttributeSensor<Integer> MANAGEMENT_URL = [ "ManagementUrl", "monterey.management-url", URL.class ]
    public static final AttributeSensor<String> NETWORK_ID = [ "NetworkId", "monterey.network-id", String.class ]
    public static final AttributeSensor<String> APPLICTION_NAME = [ "ApplicationName", "monterey.application-name", String.class ]

    /** up, down, etc? */
    public static final AttributeSensor<String> STATUS = [ "Status", "monterey.status", String.class ]

    private final Gson gson;

    private String installDir;
    private MontereyNetworkConfig config;
    private Collection<UserCredentialsConfig> webUsersCredentials;
    private CredentialsConfig webAdminCredential;
    private NetworkId networkId = NetworkId.Factory.newId();

    private SshMachineLocation host;
    private URL managementUrl;
    private MontereyNetworkConnectionDetails connectionDetails;
    private String applicationName;

    private final Map<NodeId,MontereyContainerNode> nodes = new ConcurrentHashMap<NodeId,MontereyContainerNode>();
    private final Map<String,Segment> segments = new ConcurrentHashMap<String,Segment>();

    public MontereyNetwork() {
        classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();
    }

    public void setInstallDir(String val) {
        this.installDir = val;
    }

    public void setConfig(MontereyNetworkConfig val) {
        this.config = val;
    }

    public void setWebUsersCredentials(Collection<UserCredentialsConfig> val) {
        this.webUsersCredentials = val;
        this.webAdminCredential = DeploymentUtils.findWebApiAdminCredential(webUsersCredentials);
    }

    public void setWebAdminCredential(CredentialsConfig val) {
        this.webAdminCredential = val;
    }

    public void setNetworkId(NetworkId val) {
        networkId = val;
    }

    // FIXME Use attributes instead of getters
    public String getManagementUrl() {
        return managementUrl;
    }

    public Collection<MontereyContainerNode> getNodes() {
        return nodes.values();
    }

    public Collection<Segment> getSegments() {
        return nodes.values();
    }

    public void start(Map properties=[:], Group parent=null, Location location=null) {
        // FIXME Work in progress...
        EntityStartUtils.startEntity properties, this, parent, location
        log.debug "Monterey network started... management-url is {}", this.properties['ManagementUrl']
    }

    public void startOnHost(SshMachineLocation host) {
        /*
         * TODO: Assumes the following are already set on SshMachineLocation:
         * sshAddress
         * sshPort
         * sshUsername
         * sshKey/sshKeyFile
         * HostKeyChecking hostKeyChecking = HostKeyChecking.NO;
         */

        log.info("Creating new monterey network "+networkId+" on "+host.getName());

        File webUsersConfFile = DeploymentUtils.toEncryptedWebUsersConfFile(webUsersCredentials);
        String username = System.getenv("USER");

        WebConfig web = new WebConfig(true, config.getMontereyWebApiPort(), config.getMontereyWebApiProtocol(), null);
        web.setSslKeystore(installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH);
        web.setSslKeystorePassword(config.getMontereyWebApiSslKeystorePassword());
        web.setSslKeyPassword(config.getMontereyWebApiSslKeyPassword());
        File webConf = DeploymentUtils.toWebConfFile(web);

        try {
            host.copyTo(webUsersConfFile, installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEBUSERS_FILE_RELATIVE_PATH);

            if (config.getLoggingFileOverride() != null) {
                host.copyTo(config.getLoggingFileOverride(), installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_LOGGING_FILE_OVERRIDE_RELATIVE_PATH);
                host.copyTo(config.getLoggingFileOverride(), installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_LOGGING_FILE_RELATIVE_PATH);
            }

            host.copyTo(webConf, installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH);
            if (config.getMontereyWebApiProtocol().equals(WebServer.HTTPS)) {
                host.copyTo(config.getMontereyWebApiSslKeystore(), installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH);
            }

            this.managementUrl = new URL(config.getMontereyWebApiProtocol()+"://"+host.getHost()+":"+config.getMontereyWebApiPort());
            this.connectionDetails = new MontereyNetworkConnectionDetails(networkId, managementUrl, webAdminCredential);
            this.host = host;

            // Convenient for testing: create the management-node directly in-memory, rather than starting it in a separate process
            // Please leave this commented out code here, to make subsequent debugging easier!
            // Or you could refactor to have a private static final constant that switches the behaviour?
            //            MainArguments mainArgs = new MainArguments(new File(installDir), null, null, null, null, null, networkId.getId());
            //            new ManagementNodeStarter(mainArgs).start();

            host.run(out: System.out,
                    installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_START_SCRIPT_RELATIVE_PATH+
                    " -address "+host.getHost()+
                    " -port "+Integer.toString(config.getMontereyNodePort())+
                    " -networkId "+networkId.getId()+
                    " -key "+networkId.getId()+
                    " -webConfig "+installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH+";"+
                    "exit");

            PingWebProxy pingWebProxy = new PingWebProxy(managementUrl.toString(), webAdminCredential,
                    (config.getMontereyWebApiSslKeystore() != null ? config.getMontereyWebApiSslKeystore().getPath() : null),
                    config.getMontereyWebApiSslKeystorePassword());
            boolean reachable = pingWebProxy.waitForReachable(MontereyNetworkConfig.TIMEOUT_FOR_NEW_NETWORK_ON_HOST);
            if (!reachable) {
                throw new IllegalStateException("Management plane not reachable via web-api within "+TimeUtils.makeTimeString(MontereyNetworkConfig.TIMEOUT_FOR_NEW_NETWORK_ON_HOST)+": url="+managementUrl);
            }

            activity.update MANAGEMENT_URL, managementUrl
            activity.update NETWORK_ID, networkId.getId()

            monitoringTask = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay({ updateAll() }, 1000, 1000, TimeUnit.MILLISECONDS)

            LOG.info("Created new monterey network: "+connectionDetails);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error creating monterey network", e);

            if (BrooklynSystemProperties.DEBUG.isEnabled()) {
                // Not releasing failed instance, because that would make debugging hard!
                LOG.log(Level.WARNING, "Error creating monterey network; leaving failed instance "+host, e);
            } else {
                LOG.log(Level.WARNING, "Error creating monterey network; terminating failed instance "+host, e);
                try {
                    shutdownManagementNodeProcess(config, host, networkId);
                } catch (ProcessExecutionFailureException e2) {
                    LOG.log(Level.WARNING, "Error cleaning up monterey network after failure to start: machine="+host, e2);
                }
            }

            throw new RuntimeWrappedException("Error creating monterey network on "+host, e);
        }
    }

    public void deployCloudEnvironment(CloudEnvironmentDto cloudEnvironmentDto) {
        int DEPLOY_TIMEOUT = 5*60*1000;
        DeploymentWebProxy deployer = new DeploymentWebProxy(managementUrl, gson, webAdminCredential, DEPLOY_TIMEOUT);
        deployer.deployCloudEnvironment(cloudEnvironmentDto);
    }

    public void deployApplication(MontereyDeploymentDescriptor descriptor, BundleSet bundles) {
        int DEPLOY_TIMEOUT = 5*60*1000;
        DeploymentWebProxy deployer = new DeploymentWebProxy(managementUrl, gson, webAdminCredential, DEPLOY_TIMEOUT);
        boolean result = deployer.deployApplication(descriptor, bundles);
    }

    public void stop() {
        // TODO Guard so can only shutdown if network nodes are not running?
        if (host == null) {
            throw new IllegalStateException("Monterey network is not running; cannot stop");
        }
        shutdownManagementNodeProcess(this.config, host, networkId)
        host = null;
        managementUrl = null;
        connectionDetails = null;
        applicationName = null;
    }

    private void shutdownManagementNodeProcess(MontereyNetworkConfig config, SshMachineLocation host, NetworkId networkId) {
        String killScript = installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_KILL_SCRIPT_RELATIVE_PATH;
        try {
            LOG.info("Releasing management node on "+toString());
            host.run(out: System.out,
                    killScript+" -key "+networkId.getId()+";"+
                    "exit");

        } catch (IllegalStateException e) {
            if (e.toString().contains("No such process")) {
                // the process hadn't started or was killed externally? Our work is done.
                LOG.info("Management node process not running; termination is a no-op: networkId="+networkId+"; machine="+host);
            } else {
                LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+host, e);
            }
        } catch (ProcessExecutionFailureException e) {
            LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+host, e);

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+host, e);
        }
    }

    private void updateAll() {
        updateTopology();
        updateWorkrates();
    }

    private void updateStatus() {
        DeploymentWebProxy deployer = new DeploymentWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        MontereyDeploymentDescriptor currentApp = deployer.getApplicationDeploymentDescriptor();
        String currentAppName = currentApp?.getName();
        if (!(applicationName != null ? applicationName.equals(currentAppName) : currentAppName == null)) {
            applicationName = currentAppName;
            activity.update(APPLICTION_NAME, applicationName);
        }
    }
    
    private void updateTopology() {
        Dmn1NetworkInfo networkInfo = new Dmn1NetworkInfoWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        Map<NodeId, NodeSummary> nodeSummaries = networkInfo.getNodeSummaries();
        Map<String, SegmentSummary> segmentSummaries = networkInfo.getSegmentSummaries();

        // Create/destroy nodes that have been added/removed
        Collection<NodeId> newNodes = []
        Collection<NodeId> removedNodes = []
        newNodes.addAll(nodeSummaries.keySet()); newNodes.removeAll(nodes.keySet());
        removedNodes.addAll(nodes.keySet()); newNodes.removeAll(nodeSummaries.keySet());

        newNodes.each {
            MontereyLocation montereyLocation = nodeSummaries.get(it).getMontereyLocation();
            Location location = null; // FIXME create brooklyn location
            nodes.put(it, new MontereyContainerNode(connectionDetails, it, location));
        }

        removedNodes.each {
            nodes.get(it)?.dispose();
        }


        // Create/destroy segments
        Collection<NodeId> newSegments = []
        Collection<NodeId> removedSegments = []
        newSegments.addAll(segmentSummaries.keySet()); newSegments.removeAll(segments.keySet());
        removedSegments.addAll(segments.keySet()); newSegments.removeAll(segmentSummaries.keySet());

        newSegments.each {
            segments.put(it, new Segment(connectionDetails, it));
        }

        removedSegments.each {
            segments.get(it)?.dispose();
        }


        // Notify "container nodes" (i.e. BasicNode in monterey classes jargon) of what node-types are running there
        nodeSummaries.values().each {
            nodes.get(it.getKey())?.updateContents(it);
        }
    }

    private void updateWorkrates() {
        Dmn1NetworkInfo networkInfo = new Dmn1NetworkInfoWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        Map<NodeId, WorkrateReport> workrates = networkInfo.getActivityModel().getAllWorkrateReports();

        workrates.entrySet().each {
            WorkrateReport report = it.getValue();

            // Update this node's workrate
            nodes.get(it.getKey())?.updateWorkrate(report);

            // Update each segment's workrate (if a mediator's segment-workrate item is contained here)
            report.getWorkrateItems().each {
                String itemName = it.getName()
                if (MediationWorkrateItemNames.isNameForSegment(itemName)) {
                    String segmentId = MediationWorkrateItemNames.segmentFromName(itemName);
                    segments.get(segmentId)?.updateWorkrate(report);
                }
            }
        }
    }
}
