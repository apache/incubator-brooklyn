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

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation

import com.cloudsoftcorp.monterey.clouds.NetworkId
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
import com.cloudsoftcorp.monterey.network.deployment.MontereyDeploymentDescriptor
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediationWorkrateItemNames
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.monterey.provisioning.noop.NoopCloudProvider
import com.cloudsoftcorp.monterey.provisioning.noop.NoopResourceProvisionerFactory
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.exception.ExceptionUtils
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.javalang.OsgiClassLoadingContextFromBundle
import com.cloudsoftcorp.util.osgi.BundleSet
import com.cloudsoftcorp.util.web.client.CredentialsConfig
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

    public static final BasicConfigKey<Collection<String>> APP_BUNDLES = [ Collection, "monterey.app.bundles", "Application bundles" ]
    public static final BasicConfigKey<String> APP_DESCRIPTOR_URL = [ String, "monterey.app.descriptorUrl", "Application descriptor URL" ]
    public static final BasicConfigKey<Map<String,Integer>> INITIAL_TOPOLOGY_PER_LOCATION =
            [ Map, "monterey.cluster.initialTopology", "Initial topology per cluster" ]
    public static final BasicConfigKey<Integer> MAX_CONCURRENT_PROVISIONINGS_PER_LOCATION = 
            [ Integer, "monterey.provisioning.maxConcurrentProvisioningsPerLocation", 
            "The maximum number of nodes that can be concurrently provisioned per location", Integer.MAX_VALUE ]
    
    public static final BasicAttributeSensor<String> MANAGEMENT_URL = [ String, "monterey.management-url", "Management URL" ]
    public static final BasicAttributeSensor<String> NETWORK_ID = [ String, "monterey.network-id", "Network id" ]
    public static final BasicAttributeSensor<String> APPLICATION_NAME = [ String, "monterey.application-name", "Application name" ]
    public static final BasicAttributeSensor<CredentialsConfig> CLIENT_CREDENTIAL =
            [ CredentialsConfig, "monterey.management.clientCredential", "Client credentials for connecting to web-api" ]

    /** up, down, etc? */
    public static final BasicAttributeSensor<String> STATUS = [ String, "monterey.status", "Status" ]

    private static final int POLL_PERIOD = 1000;
    
    private final NetworkId networkId = NetworkId.Factory.newId();
    
    private MontereyDeploymentDescriptor appDescriptor;
    private CloudEnvironmentDto cloudEnvironmentDto;
    private MontereyNetworkConfig config = new MontereyNetworkConfig();
    
    private MontereyManagementNode managementNode;
    private MontereyProvisioner montereyProvisioner;
    private MontereyNetworkConnectionDetails connectionDetails;
    private String applicationName;
    
    private final Map<String,MontereyContainerNode> nodesByCreationId = new ConcurrentHashMap<String,MontereyContainerNode>();
    private final Map<String,Segment> segments = new ConcurrentHashMap<String,Segment>();
    private final Map<Location,Map<Dmn1NodeType,MontereyNodeGroup>> clustersByLocationAndType = new ConcurrentHashMap<Location,Map<Dmn1NodeType,MontereyNodeGroup>>();
    private final Map<Dmn1NodeType,MontereyNodeGroup> typedFabrics = [:];
    private ScheduledExecutorService scheduledExecutor;
    private ScheduledFuture<?> monitoringTask;
    
    public MontereyNetwork(Map props=[:], Entity owner=null) {
        super(props, owner);
        
        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, getClass().classLoader);
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);
        
        setConfigIfValNonNull(APP_BUNDLES, props.appBundles)
        setConfigIfValNonNull(APP_DESCRIPTOR_URL, props.appDescriptor)
        setConfigIfValNonNull(INITIAL_TOPOLOGY_PER_LOCATION, props.initialTopologyPerLocation)
        setConfigIfValNonNull(MAX_CONCURRENT_PROVISIONINGS_PER_LOCATION, props.maxConcurrentProvisioningsPerLocation)
        setConfigIfValNonNull(MontereyManagementNode.WEB_USERS_CREDENTIAL, props.webUsersCredential)
        setConfigIfValNonNull(MontereyManagementNode.MANAGEMENT_NODE_INSTALL_DIR, props.managementNodeInstallDir)
        setConfigIfValNonNull(MontereyManagementNode.WEB_API_PORT, props.webApiPort)
        setConfigIfValNonNull(MontereyContainerNode.NETWORK_NODE_INSTALL_DIR, props.networkNodeInstallDir)
    }

    public String getDisplayName() {
        return "Monterey network ("+(getAttribute(APPLICATION_NAME) ?: "no-app")+")"
    }
    
    // Programmatically set the application descriptor (rather than supplying a config file)
    public void setAppDescriptor(MontereyDeploymentDescriptor val) {
        this.appDescriptor = val
    }
    
    // Programmatically set the cloud environment (rather than using the default, which 
    // pushes the start-locs to the monterey-management-node)
    public void setCloudEnvironment(CloudEnvironmentDto val) {
        cloudEnvironmentDto = val
    }
        
    public void setConfig(MontereyNetworkConfig val) {
        this.config = val;
    }

    public CredentialsConfig getClientCredential() {
        return connectionDetails.webApiClientCredential
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

    public MontereyNodeGroup getFabric(Dmn1NodeType nodeType) {
        return typedFabrics.get(nodeType);
    }

    public Map<Location, MontereyNodeGroup> getClusters(Dmn1NodeType nodeType) {
        Map<Location, MontereyNodeGroup> result = [:]
        clustersByLocationAndType.each {
            MontereyNodeGroup cluster = it.getValue().getAt(nodeType)
            if (cluster != null) {
                result.put(it.getKey(), cluster)
            }
        }
        return result
    }
    
    public Map<Dmn1NodeType, MontereyNodeGroup> getClusters(Location loc) {
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
            managementNode.config = config
            
            managementNode.start([provisioningLoc])
            
            connectionDetails = managementNode.connectionDetails
            setAttribute NETWORK_ID, networkId
            mirrorManagementNodeAttributes()
            
            montereyProvisioner = new MontereyProvisioner(connectionDetails, this, getConfig(MAX_CONCURRENT_PROVISIONINGS_PER_LOCATION))
            
            // TODO want to call executionContext.scheduleAtFixedRate or some such
            Thread.sleep(1000)
            LOG.info("Scheduling poller: "+connectionDetails.managementUrl+"; "+connectionDetails.webApiAdminCredential.getUsername()+"; "+connectionDetails.webApiAdminCredential.getPassword())
            scheduledExecutor = Executors.newScheduledThreadPool(1, {return new Thread(it, "monterey-network-poller")} as ThreadFactory)
            monitoringTask = scheduledExecutor.scheduleAtFixedRate({ updateAll() } as Runnable, POLL_PERIOD, POLL_PERIOD, TimeUnit.MILLISECONDS)

            if (!cloudEnvironmentDto) {
                String cloudEnvironmentId = networkId;
                CloudAccountDto cloudAccountDto = new CloudAccountDto(
                        new CloudAccountIdImpl("accid"), NoopCloudProvider.PROVIDER_ID);
                Collection<MontereyLocation> montereyLocations = locations.collect {
                    return new MontereyLocationBuilder(it.id)
                            .withProvider(NoopCloudProvider.PROVIDER_ID)
                            .withAbbr(it.findLocationProperty("abbreviatedName") ?: "")
                            .withTimeZone(it.findLocationProperty("timeZone") ?: "")
                            .withMontereyProvisionerId(NoopResourceProvisionerFactory.MONTEREY_PROVISIONER_ID)
                            .withDisplayName(it.findLocationProperty("displayName") ?: "")
                            .withIso3166Codes(it.findLocationProperty("iso3166") ?: [])
                            .build();
                }
                ProvisioningConfigDto provisioningConfig = new ProvisioningConfigDto(new Properties());
                CloudProviderSelectionDto providerSelectionDto = new CloudProviderSelectionDto(
                        cloudAccountDto, montereyLocations, provisioningConfig)
                
                cloudEnvironmentDto = new CloudEnvironmentDto(cloudEnvironmentId, Collections.singleton(providerSelectionDto));
            }
            managementNode.deployCloudEnvironment(cloudEnvironmentDto);
            
            if (!appDescriptor) {
                String appDescriptorUrl = getConfig(APP_DESCRIPTOR_URL)
                if (appDescriptorUrl) {
                    appDescriptor = DescriptorLoader.loadDescriptor(appDescriptorUrl);
                }
            }
            if (appDescriptor) {
                Collection<String> bundleUrls = getConfig(APP_BUNDLES)
                BundleSet bundleSet = (bundleUrls) ? BundleSet.fromUrls(bundleUrls.collect { String url -> new File(url).toURI().toURL() }) : BundleSet.EMPTY;
                managementNode.deployApplication(appDescriptor, bundleSet);
            }
            
            // Create fabrics and clusters for each node-type
            Map<String,Integer> initial = getConfig(INITIAL_TOPOLOGY_PER_LOCATION) ?: [:]
            startFabricLayers()
            locations.each {
                startClusterLayersInLocation(it, initial.collectEntries { String key, Integer value -> [ Dmn1NodeType.valueOf(key), value ] })
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
        setAttribute CLIENT_CREDENTIAL, managementNode.connectionDetails.webApiClientCredential
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
        if (result) mirrorManagementNodeAttributes()
        return result
    }
    
    private void updateAppName() {
        MontereyDeploymentDescriptor currentApp = managementNode.getDeploymentProxy().getApplicationDeploymentDescriptor();
        String currentAppName = currentApp?.getName();
        if (!(applicationName != null ? applicationName.equals(currentAppName) : currentAppName == null)) {
            try {
	            URL configLocation = new URL(currentAppName)
	            File configFile = new File(configLocation.path)
	            applicationName = configFile.name
            } catch (Exception e) {
	            applicationName = currentAppName;
            }
            setAttribute(APPLICATION_NAME, applicationName);
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
        typedFabrics.each { key, fabric ->
            fabric.refreshLocations(locations);
        }
    }
    
    private void startFabricLayers() {
        typedFabrics.put(Dmn1NodeType.LPP, MontereyNodeGroup.newAllLocationsInstance([displayName:"LPP fabric"], connectionDetails, montereyProvisioner, Dmn1NodeType.LPP, locations));
        typedFabrics.put(Dmn1NodeType.MR, MontereyNodeGroup.newAllLocationsInstance([displayName:"MR fabric"], connectionDetails, montereyProvisioner, Dmn1NodeType.MR, locations));
        typedFabrics.put(Dmn1NodeType.M, MediatorGroup.newAllLocationsInstance([displayName:"Mediator fabric"], connectionDetails, montereyProvisioner, locations));
        typedFabrics.put(Dmn1NodeType.TP, MontereyNodeGroup.newAllLocationsInstance([displayName:"TP fabric"], connectionDetails, montereyProvisioner, Dmn1NodeType.TP, locations));
        
        typedFabrics.values().each { 
            addOwnedChild(it)
            getManagementContext().manage(it)
        }
    }
    
    private void startClusterLayersInLocation(Location loc, Map initialTopologyPerLocation) {
        // Instantiate clusters
        Map<Dmn1NodeType,MontereyNodeGroup> clustersByType = [:]
        clustersByLocationAndType.put(loc, clustersByType)

        String abbreviatedLoc = loc.findLocationProperty("abbreviatedName") ?: loc.getName()
        clustersByType.put(Dmn1NodeType.LPP, MontereyNodeGroup.newSingleLocationInstance([displayName:"LPP cluster ("+abbreviatedLoc+")"], connectionDetails, montereyProvisioner, Dmn1NodeType.LPP, loc));
        clustersByType.put(Dmn1NodeType.MR, MontereyNodeGroup.newSingleLocationInstance([displayName:"MR cluster ("+abbreviatedLoc+")"], connectionDetails, montereyProvisioner, Dmn1NodeType.MR, loc));
        clustersByType.put(Dmn1NodeType.M, MediatorGroup.newSingleLocationInstance([displayName:"Mediator cluster ("+abbreviatedLoc+")"], connectionDetails, montereyProvisioner, loc));
        clustersByType.put(Dmn1NodeType.TP, MontereyNodeGroup.newSingleLocationInstance([displayName:"TP cluster ("+abbreviatedLoc+")"], connectionDetails, montereyProvisioner, Dmn1NodeType.TP, loc));
        
        clustersByType.values().each {
            it.setOwner(typedFabrics.get(it.nodeType))
            getManagementContext().manage(it)
        }

        // Kick-off provisioning of spare nodes immediately
        int totalNumNodes = 0
        initialTopologyPerLocation.values().each { totalNumNodes += (it ?: 0) }
        
        montereyProvisioner.addSpareNodesAsync(loc, totalNumNodes)
        
        // Rollout the required nodes of each type (via the clusters)
        [Dmn1NodeType.TP, Dmn1NodeType.M, Dmn1NodeType.MR, Dmn1NodeType.LPP].each {
            if (initialTopologyPerLocation.get(it) != null) {
                clustersByType.get(it).resize(initialTopologyPerLocation.get(it))
            }
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
            if (it != null && type == it.nodeType) result.add(it)
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
