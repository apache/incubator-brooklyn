package com.cloudsoftcorp.monterey.brooklyn.entity

import java.net.URL
import java.util.Collection
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Map
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation

import com.cloudsoftcorp.monterey.clouds.NetworkId
import com.cloudsoftcorp.monterey.clouds.basic.DeploymentUtils
import com.cloudsoftcorp.monterey.clouds.dto.CloudAccountDto
import com.cloudsoftcorp.monterey.clouds.dto.CloudEnvironmentDto
import com.cloudsoftcorp.monterey.clouds.dto.CloudProviderSelectionDto
import com.cloudsoftcorp.monterey.clouds.dto.ProvisioningConfigDto
import com.cloudsoftcorp.monterey.control.api.SegmentSummary
import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation
import com.cloudsoftcorp.monterey.location.api.MontereyLocation
import com.cloudsoftcorp.monterey.location.impl.MontereyLocationBuilder
import com.cloudsoftcorp.monterey.location.temp.impl.CloudAccountIdImpl
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.deployment.DescriptorLoader
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.deployment.MontereyDeploymentDescriptor
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediationWorkrateItemNames
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.exception.ExceptionUtils
import com.cloudsoftcorp.util.osgi.BundleSet
import com.cloudsoftcorp.util.web.client.CredentialsConfig
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet

/**
 * Represents a Monterey network.
 * 
 * @author aled
 */
public class MontereyNetwork extends AbstractEntity implements Startable { // FIXME , AbstractGroup

    /*
     * FIXME Deal with converting from monterey location to Brooklyn location properly
     * FIXME Declare things as effectors
     * FIXME How will this entity be moved? How will its sub-entities be wired back up?
     * TODO  Should this be called MontereyManagementPlane?
     *       Currently starting in a list of locations is confusing - what if some locations 
     *       are machines and others are ProvisioningLocatinos?
     * FIXME Should the provisioning script create and add the cluster? Rather than creating one
     *       per known location?
     */
    
    private static final Logger LOG = Loggers.getLogger(MontereyNetwork.class);

    public static final BasicAttributeSensor<URL> MANAGEMENT_URL = [ URL.class, "monterey.management-url", "Management URL" ]
    public static final BasicAttributeSensor<String> NETWORK_ID = [ String.class, "monterey.network-id", "Network id" ]
    public static final BasicAttributeSensor<String> APPLICTION_NAME = [ String.class, "monterey.application-name", "Application name" ]

    /** up, down, etc? */
    public static final BasicAttributeSensor<String> STATUS = [ String, "monterey.status", "Status" ]

    private static final int POLL_PERIOD = 1000;
    
    private final NetworkId networkId = NetworkId.Factory.newId();
    
    private String managementNodeInstallDir;
    private String name;
    private Collection<URL> appBundles;
    private URL appDescriptorUrl;
    private MontereyDeploymentDescriptor appDescriptor;
    private CloudEnvironmentDto cloudEnvironmentDto;
    private MontereyNetworkConfig config = new MontereyNetworkConfig();
    private Collection<UserCredentialsConfig> webUsersCredentials;
    private CredentialsConfig webAdminCredential;
    private initialTopologyPerLocation = [(Dmn1NodeType.LPP):0,(Dmn1NodeType.MR):0,(Dmn1NodeType.M):0,(Dmn1NodeType.TP):0,(Dmn1NodeType.SPARE):0]
    
    private MontereyManagementNode managementNode;
    private MontereyProvisioner montereyProvisioner;
    private MontereyNetworkConnectionDetails connectionDetails;
    private String applicationName;
    
    private final Map<String,MontereyContainerNode> nodesByCreationId = new ConcurrentHashMap<String,MontereyContainerNode>();
    private final Map<String,Segment> segments = new ConcurrentHashMap<String,Segment>();
    private final Map<Location,Map<Dmn1NodeType,MontereyTypedGroup>> clustersByLocationAndType = new ConcurrentHashMap<Location,Map<Dmn1NodeType,MontereyTypedGroup>>();
    private final Map<Dmn1NodeType,MontereyTypedGroup> typedFabrics = [:];
    private ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> monitoringTask;
    
    public static final BasicConfigKey<Collection<URL>> SUGGESTED_APP_BUNDLES = [Collection.class, "monterey.app.bundles", "Application bundles" ]
    public static final BasicConfigKey<URL> SUGGESTED_APP_DESCRIPTOR_URL = [URL.class, "monterey.app.descriptorUrl", "Application descriptor URL" ]
    public static final BasicConfigKey<Collection> SUGGESTED_WEB_USERS_CREDENTIAL = [Collection.class, "monterey.managementnode.webusers", "Monterey management node web-user credentials" ]
    public static final BasicConfigKey<String> SUGGESTED_MANAGEMENT_NODE_INSTALL_DIR = [String.class, "monterey.managementnode.installdir", "Monterey management node installation directory" ]
    
    public MontereyNetwork() {
    }

    public void setName(String val) {
        this.name = name
    }
    
    public void setAppBundles(Collection<URL> val) {
        this.appBundles = new ArrayList<String>(val)
    }
    
    public void setAppDescriptorUrl(URL val) {
        this.appDescriptorUrl = val
    }
    
    public void setAppDescriptor(MontereyDeploymentDescriptor val) {
        this.appDescriptor = val
    }
    
    public void setCloudEnvironment(CloudEnvironmentDto val) {
        cloudEnvironmentDto = val
    }
        
    public void setManagementNodeInstallDir(String val) {
        this.managementNodeInstallDir = val;
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

    public Collection<MontereyContainerNode> getContainerNodes() {
        return ImmutableSet.copyOf(nodesByCreationId.values());
    }

    public Map<NodeId,AbstractMontereyNode> getMontereyNodes() {
        Map<NodeId,AbstractMontereyNode> result = [:]
        nodesByCreationId.values().each {
            result.put(it.getNodeId(), it.getContainedMontereyNode());
        }
        return Collections.unmodifiableMap(result);
    }

    public MontereyTypedGroup getFabric(Dmn1NodeType nodeType) {
        return typedFabrics.get(nodeType);
    }

    public Map<Location, MontereyTypedGroup> getClusters(Dmn1NodeType nodeType) {
        Map<Location, MontereyTypedGroup> result = [:]
        clustersByLocationAndType.each {
            MontereyTypedGroup cluster = it.getValue().getAt(nodeType)
            if (cluster != null) {
                result.put(it.getKey(), cluster)
            }
        }
        return result
    }
    
    public Map<Dmn1NodeType, MontereyTypedGroup> getClusters(Location loc) {
        return clustersByLocationAndType.get(loc)?.asImmutable() ?: [:];
    }
    
    public MediatorGroup getMediatorFabric() {
        return typedFabrics.get(Dmn1NodeType.M);
    }
    
    public Map<Location, MediatorGroup> getMediatorClusters() {
        return getClusters(Dmn1NodeType.M);
    }
    
    public Map<String,Segment> getSegments() {
        return ImmutableMap.copyOf(this.@segments);
    }

    // TODO Use attribute? Method is used to guard call to stop. Or should stop be idempotent?
    public boolean isRunning() {
        return managementNode?.isRunning() ?: false
    }
    
    public void dispose() {
        monitoringTask?.cancel(true);
        scheduledExecutor?.shutdownNow();
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
        locations.clear()
        locations.addAll(locs)
        
        MachineProvisioningLocation provisioningLoc = locs.find({ it instanceof MachineProvisioningLocation });
        if (!provisioningLoc) {
            throw new IllegalArgumentException("Unsupported location types creating monterey network, $locations")
        }
        
        try {
            managementNode = new MontereyManagementNode([owner:this])
            managementNode.networkId = networkId
            managementNode.managementNodeInstallDir = managementNodeInstallDir
            managementNode.config = config
            managementNode.webUsersCredentials = webUsersCredentials
            managementNode.webAdminCredential = webAdminCredential
            
            managementNode.start([provisioningLoc])
            
            connectionDetails = managementNode.connectionDetails
            setAttribute NETWORK_ID, networkId
            mirrorManagementNodeAttributes()
            
            montereyProvisioner = new MontereyProvisioner(connectionDetails, this)
            
            // TODO want to call executionContext.scheduleAtFixedRate or some such
            scheduledExecutor = Executors.newScheduledThreadPool(1, {return new Thread(it, "monterey-network-poller")} as ThreadFactory)
            monitoringTask = scheduledExecutor.scheduleAtFixedRate({ updateAll() } as Runnable, POLL_PERIOD, POLL_PERIOD, TimeUnit.MILLISECONDS)

            if (!cloudEnvironmentDto) {
                String cloudEnvironmentId = networkId;
                CloudAccountDto cloudAccountDto = new CloudAccountDto(
                        new CloudAccountIdImpl("accid"), NoopCloudProvider.PROVIDER_ID);
                Collection<MontereyLocation> montereyLocations = locations.collect {
                    return new MontereyLocationBuilder(it.name) // TODO use an id?
                            .withProvider(NoopCloudProvider.PROVIDER_ID)
                            .withAbbr(it.findLocationProperty("abbreviatedName") ?: "")
                            .withTimeZone(it.findLocationProperty("timeZone") ?: "")
                            .withMontereyProvisionerId(NoopResourceProvisionerFactory.MONTEREY_PROVISIONER_ID)
                            .withDisplayName(it.findLocationProperty("displayName") ?: "")
                            .withIso3166Codes(it.findLocationProperty("iso3166") ?: "")
                            .build();
                }
                ProvisioningConfigDto provisioningConfig = new ProvisioningConfigDto(new Properties());
                CloudProviderSelectionDto providerSelectionDto = new CloudProviderSelectionDto(
                        cloudAccountDto, montereyLocations, provisioningConfig)
                
                cloudEnvironmentDto = new CloudEnvironmentDto(cloudEnvironmentId, Collections.singleton(providerSelectionDto));
            }
            managementNode.deployCloudEnvironment(cloudEnvironmentDto);
            
            if (!appDescriptor) {
                if (appDescriptorUrl) {
                    appDescriptor = DescriptorLoader.loadDescriptor(appDescriptorUrl);
                }
            }
            if (appDescriptor) {
                BundleSet bundleSet = (appBundles) ? BundleSet.fromUrls(appBundles) : BundleSet.EMPTY;
                managementNode.deployApplication(appDescriptor, bundleSet);
            }
            
            // Create fabrics and clusters for each node-type
            startFabricLayers()
            locations.each {
                startClusterLayersInLocation(it, initialTopologyPerLocation)
            }
            
            LOG.info("Created new monterey network: "+networkId);

        } catch (Exception e) {
            // TODO If successfully created management node, but then get exception, do we want to shut it down?
            LOG.log(Level.WARNING, "Error creating monterey network; stopping management node and rethrowing", e);
            managementNode?.stop()
            throw Throwables.propagate(e)
        }
    }

    private void mirrorManagementNodeAttributes() {
        setAttribute MANAGEMENT_URL, managementNode.getAttribute(MontereyManagementNode.MANAGEMENT_URL)
        setAttribute STATUS, managementNode.getAttribute(MontereyManagementNode.STATUS)
    }
    
    MontereyContainerNode provisionNode(Location loc) {
        MontereyContainerNode node = new MontereyContainerNode(connectionDetails);
        nodesByCreationId.put(node.creationId, node);
        addOwnedChild(node);
        node.start([loc]);
        node
    }
    
    Collection<MontereyContainerNode> rolloutNodes(Location loc, Map<Dmn1NodeType,Integer> nums) {
        int totalNum = 0;
        nums.values().each { totalNum += (it ?: 0) }
        
        Collection<MontereyContainerNode> nodes = montereyProvisioner.requestNodes(loc, totalNum)
        Collection<MontereyContainerNode> unusedNodes = new ArrayList(nodes)
        
        Collection<Dmn1NodeType> orderToRollout = [Dmn1NodeType.TP, Dmn1NodeType.M, Dmn1NodeType.MR, Dmn1NodeType.LPP]
        orderToRollout.each {
            Dmn1NodeType type = it
            Integer numOfType = nums.get(type)
            if (numOfType != null) {
                for (int i = 0; i < numOfType; i++) {
                    MontereyContainerNode node = unusedNodes.remove(0)
                    node.rollout(type)
                }
            }
        }
    }
    
    void releaseNode(MontereyContainerNode node) {
        node.stop();
        nodesByCreationId.remove(node.creationId);
    }

    public void releaseAllNodes() {
        // TODO Releasing in the right order; but what if revert/rollout is happening concurrently?
        //      Can we delegate to management node, or have brooklyn more aware of what's going on?
        // TODO Release is currently being done sequentially...
        
        List<MontereyContainerNode> torelease = []
        findNodesOfType(Dmn1NodeType.SATELLITE_BOT).each { torelease.add(it.owner) }
        findNodesOfType(Dmn1NodeType.LPP).each { torelease.add(it.owner) }
        findNodesOfType(Dmn1NodeType.M).each { torelease.add(it.owner) }
        findNodesOfType(Dmn1NodeType.MR).each { torelease.add(it.owner) }
        findNodesOfType(Dmn1NodeType.TP).each { torelease.add(it.owner) }
        findNodesOfType(Dmn1NodeType.SPARE).each { torelease.add(it.owner) }
        relativeComplement(nodesByCreationId.values(), torelease).each { torelease.add(torelease) }
        
        for (MontereyContainerNode node : torelease) {
            node.release();
        }
    }

    @Override
    public void stop() {
        managementNode?.stop()
        
        // TODO Race: monitoringTask could still be executing, and could get NPE when it tries to get connectionDetails
        monitoringTask?.cancel(true);
    }

    private void updateAll() {
        try {
            LOG.info("Polling monterey management node for current state, on "+connectionDetails.managementUrl)
            
            boolean isup = updateStatus();
            if (isup) {
                updateAppName();
                updateTopology();
                updateWorkrates();
            }
        } catch (Throwable t) {
            LOG.log Level.WARNING, "Error updating brooklyn entities of Monterey Network "+connectionDetails.managementUrl, t
            ExceptionUtils.throwRuntime t
        }
    }

    private boolean updateStatus() {
        boolean result = managementNode.updateStatus()
        mirrorManagementNodeAttributes()
        return result
    }
    
    private void updateAppName() {
        MontereyDeploymentDescriptor currentApp = managementNode.getDeploymentProxy().getApplicationDeploymentDescriptor();
        String currentAppName = currentApp?.getName();
        if (!(applicationName != null ? applicationName.equals(currentAppName) : currentAppName == null)) {
            applicationName = currentAppName;
            setAttribute(APPLICTION_NAME, applicationName);
        }
    }
    
//    private NodeSummary 
    private void updateTopology() {
        // TODO What if a location is added or removed? Currently we don't add/remove the cluster(s)
        
        Dmn1NetworkInfo networkInfo = managementNode.getNetworkInfo()
        
        updateFabricTopologies();
        updateNodeTopologies();
        updateSegmentTopologies();
    }

    private void updateFabricTopologies() {
        typedFabrics.values().each {
            it.refreshLocations(locations);
        }
    }
    
    private void startFabricLayers() {
        typedFabrics.put(Dmn1NodeType.LPP, MontereyTypedGroup.newAllLocationsInstance(connectionDetails, montereyProvisioner, Dmn1NodeType.LPP, locations));
        typedFabrics.put(Dmn1NodeType.MR, MontereyTypedGroup.newAllLocationsInstance(connectionDetails, montereyProvisioner, Dmn1NodeType.MR, locations));
        typedFabrics.put(Dmn1NodeType.M, MediatorGroup.newAllLocationsInstance(connectionDetails, montereyProvisioner, locations));
        typedFabrics.put(Dmn1NodeType.TP, MontereyTypedGroup.newAllLocationsInstance(connectionDetails, montereyProvisioner, Dmn1NodeType.TP, locations));
        
        typedFabrics.values().each { addOwnedChild(it) }
    }
    
    private void startClusterLayersInLocation(Location loc, Map initialTopologyPerLocation) {
        // Instantiate clusters
        Map<Dmn1NodeType,MontereyTypedGroup> clustersByType = [:]
        clustersByLocationAndType.put(loc, clustersByType)

        clustersByType.put(Dmn1NodeType.LPP, MontereyTypedGroup.newSingleLocationInstance(connectionDetails, montereyProvisioner, Dmn1NodeType.LPP, loc));
        clustersByType.put(Dmn1NodeType.MR, MontereyTypedGroup.newSingleLocationInstance(connectionDetails, montereyProvisioner, Dmn1NodeType.MR, loc));
        clustersByType.put(Dmn1NodeType.M, MediatorGroup.newSingleLocationInstance(connectionDetails, montereyProvisioner, loc));
        clustersByType.put(Dmn1NodeType.TP, MontereyTypedGroup.newSingleLocationInstance(connectionDetails, montereyProvisioner, Dmn1NodeType.TP, loc));
        
        clustersByType.values().each { it.setOwner(typedFabrics.get(it.nodeType)) }
        
        // Start the required nodes in each
        [Dmn1NodeType.TP, Dmn1NodeType.M, Dmn1NodeType.MR, Dmn1NodeType.LPP].each {
            if (initialTopologyPerLocation.get(it) != null) {
                clustersByType.get(it).resize(initialTopologyPerLocation.get(it))
            }
        }
        if (initialTopologyPerLocation.get(Dmn1NodeType.SPARE) != null) {
            montereyProvisioner.addSpareNodes(loc, initialTopologyPerLocation.get(Dmn1NodeType.SPARE))
        }
    }

    private void updateNodeTopologies() {
        Dmn1NetworkInfo networkInfo = managementNode.getNetworkInfo();
        Map<NodeId, NodeSummary> nodeSummaries = networkInfo.getNodeSummaries();
        Map<NodeId,Collection<NodeId>> downstreamNodes = networkInfo.getTopology().getAllTargets();
        
        // Create/destroy nodes that have been added/removed
        Collection<NodeSummary> newNodes = []
        Collection<String> removedNodes = []
        nodeSummaries.values().each {
            if (!nodesByCreationId.containsKey(it.creationUid)) {
                newNodes.add(it)
            }
            removedNodes.remove(it.creationUid)
        }

        newNodes.each {
            // Node started externally
            MontereyActiveLocation montereyLocation = it.getMontereyActiveLocation();
            Location location = locations.find { montereyLocation.getLocation().getId().equals(it.getName()) }
            MontereyContainerNode containerNode = new MontereyContainerNode(connectionDetails, it.creationUid);
            containerNode.connectToExisting(it, location)
            addOwnedChild(containerNode);
            nodesByCreationId.put(it.creationUid, containerNode);
        }

        removedNodes.each {
            MontereyContainerNode node = nodesByCreationId.remove(it)
            if (node != null) {
                node.dispose();
                removeOwnedChild(node);
            }
        }
        
        // Notify "container nodes" (i.e. BasicNode in monterey classes jargon) of what node-types are running there
        nodeSummaries.values().each {
            nodesByCreationId.get(it.creationUid)?.updateContents(it, downstreamNodes.get(it.getNodeId()));
        }
    }
    
    private void updateSegmentTopologies() {
        Dmn1NetworkInfo networkInfo = managementNode.getNetworkInfo();
        Map<String, SegmentSummary> segmentSummaries = networkInfo.getSegmentSummaries();
        Map<String, NodeId> segmentAllocations = networkInfo.getSegmentAllocations();
        
        // Create/destroy segments
        Collection<String> newSegments = []
        Collection<String> removedSegments = []
        newSegments.addAll(segmentSummaries.keySet()); newSegments.removeAll(segments.keySet());
        removedSegments.addAll(segments.keySet()); removedSegments.removeAll(segmentSummaries.keySet());

        newSegments.each {
            Segment segment = new Segment(connectionDetails, it);
            addOwnedChild(segment);
            this.@segments.put(it, segment);
        }

        removedSegments.each {
            Segment segment = this.@segments.remove(it);
            if (segment != null) {
                segment.dispose();
                removeOwnedChild(segment);
            }
        }

        // Notify segments of their mediator
        segments.values().each {
            String segmentId = it.segmentId()
            SegmentSummary summary = segmentSummaries.get(segmentId);
            NodeId mediator = segmentAllocations.get(segmentId);
            it.updateTopology(summary, mediator);
        }
    }
    
    private void updateWorkrates() {
        Dmn1NetworkInfo networkInfo = managementNode.getNetworkInfo();
        Map<NodeId, WorkrateReport> workrates = networkInfo.getActivityModel().getAllWorkrateReports();

        workrates.entrySet().each {
            WorkrateReport report = it.getValue();

            // Update this node's workrate
            findNode(it.getKey())?.updateWorkrate(report);

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
    
    private Collection<AbstractMontereyNode> findNodesOfType(Dmn1NodeType type) {
        Collection<AbstractMontereyNode> result = []
        montereyNodes.values().each {
            if (type == it.nodeType) result.add(it)
        }
        return result
    }

    private MontereyContainerNode findNode(NodeId nodeId) {
        MontereyContainerNode result
        nodesByCreationId.values().each { if (nodeId.equals(it.nodeId)) result = it }
        return result
    }

    private static <T> Set<T> union(Collection<T> col1, Collection<T> col2) {
        Set<T> result = new LinkedHashSet<T>(col1);
        result.addAll(col2);
        return result;
    }
    
    /**
     * The relative complement of A with respect to a set B, is the set of elements in B but not in A.
     * Therefore, returns the elements that are in col but that are not in other.
     */
    private static <T> Collection<T> relativeComplement(Collection<T> col, Collection<?> other) {
        Set<T> result = new LinkedHashSet<T>(col);
        result.removeAll(other);
        return Collections.unmodifiableSet(result);
    }
    
}
