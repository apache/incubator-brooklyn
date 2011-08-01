package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.net.URL
import java.util.ArrayList
import java.util.Arrays
import java.util.Collection
import java.util.Collections
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Map
import java.util.Set
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import com.cloudsoftcorp.monterey.CloudsoftThreadMonitoringTestFixture
import com.cloudsoftcorp.monterey.clouds.dto.CloudAccountDto
import com.cloudsoftcorp.monterey.clouds.dto.CloudEnvironmentDto
import com.cloudsoftcorp.monterey.clouds.dto.CloudProviderSelectionDto
import com.cloudsoftcorp.monterey.clouds.dto.ProvisioningConfigDto
import com.cloudsoftcorp.monterey.clouds.simulator.SimulatorAccountConfig
import com.cloudsoftcorp.monterey.clouds.simulator.SimulatorProvider
import com.cloudsoftcorp.monterey.control.api.SegmentSummary
import com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceLocator
import com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceLocatorImpl
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation
import com.cloudsoftcorp.monterey.location.api.MontereyLocation
import com.cloudsoftcorp.monterey.location.dsl.MontereyLocationsDsl
import com.cloudsoftcorp.monterey.location.temp.impl.CloudAccountIdImpl
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.DeploymentWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.Dmn1NetworkInfoWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.ProvisionerWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.control.plane.web.api.ControlPlaneWebConstants.HTTP_AUTH
import com.cloudsoftcorp.monterey.network.control.wipapi.CloudProviderAccountAndLocationId
import com.cloudsoftcorp.monterey.network.control.wipapi.DmnFuture
import com.cloudsoftcorp.monterey.network.control.wipapi.LocationUtils
import com.cloudsoftcorp.monterey.network.control.wipapi.NodesRolloutConfiguration
import com.cloudsoftcorp.monterey.network.deployment.MontereyApplicationDescriptor
import com.cloudsoftcorp.monterey.network.deployment.MontereyDeploymentDescriptor
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.monterey.servicebean.access.api.MontereyNetworkEndpointImpl
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.TimeUtils
import com.cloudsoftcorp.util.condition.Filter
import com.cloudsoftcorp.util.condition.Filters
import com.cloudsoftcorp.util.condition.Conditions.ConditionWithMessage
import com.cloudsoftcorp.util.exception.ExceptionUtils
import com.cloudsoftcorp.util.exception.RuntimeInterruptedException
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.javalang.OsgiClassLoadingContextFromBundle
import com.cloudsoftcorp.util.web.client.CredentialsConfig
import com.google.common.collect.ImmutableMap
import com.google.gson.Gson

public class MontereyBrooklynProvisioningTest extends CloudsoftThreadMonitoringTestFixture {

    private static final Logger LOG = Loggers.getLogger(MontereyBrooklynProvisioningTest.class);

    private static final String SIMULATOR_LOCATIONS_CONF_PATH = "/locations-simulator.conf";
    
    private static final String MONTEREY_NETWORK_NODE_PATH = "~/monterey-management-node-copy1";
    private static final String MONTEREY_MANAGEMENT_NODE_PATH = "~/monterey-management-node";
    private static final String SSH_HOST_NAME = "localhost";
    private static final String SSH_USERNAME = "aled";
    
    private static final String APP_BUNDLE_RESOURCE_PATH = "com.cloudsoftcorp.monterey.example.noapisimple.jar";
    private static final String HELLO_CLOUD_BUNDLE_NAME = "com.cloudsoftcorp.monterey.example.noapisimple";
    private static final String HELLO_CLOUD_CLIENT_FACTORY_NAME = "com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudClientFactory";
    private static final String HELLO_CLOUD_SERVICE_FACTORY_NAME = "com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceFactory";
    private static final URL HELLO_CLOUD_BUNDLE_URL = MontereyBrooklynProvisioningTest.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
    static {
        if (HELLO_CLOUD_BUNDLE_URL == null) {
            throw new RuntimeException("Hello cloud bundle not found: "+APP_BUNDLE_RESOURCE_PATH);
        }
    }

    private static final Collection<SegmentSummary> HELLO_CLOUD_SEGMENTS = Arrays.asList(
            SegmentSummary.Factory.newInstance("a"),
            SegmentSummary.Factory.newInstance("b"),
            SegmentSummary.Factory.newInstance("c"))

    // I'm re-using my ~/monterey-network-node directory, so can only start one at a time (otherwise risk zip corrupted exceptions)
    private static final int MAX_CONCURRENT_PROVISIONINGS_PER_LOCATION_VAL = 1
    
    private static final long TIMEOUT = 15*1000;
    
    private Gson gson;
    private FixedListMachineProvisioningLocation localhostProvisioner;
    private AbstractApplication app;
    private MontereyNetwork montereyNetwork;
    private UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
    private ScheduledExecutorService workloadExecutor;
    
    private ClassLoadingContext originalClassLoadingContext;

    
    @BeforeMethod
    public void setUp() throws Exception {
        originalClassLoadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext(); 
        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereyBrooklynProvisioningTest.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);
        GsonSerializer gsonSerializer = new GsonSerializer(classLoadingContext);
        gson = gsonSerializer.getGson();

        workloadExecutor = Executors.newScheduledThreadPool(10);
        
        localhostProvisioner = new FixedListMachineProvisioningLocation<SshMachineLocation>(
            [machines:[], name:"localhost-microcloud"])
        for (i in 1..10) {
            new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]).setParentLocation(localhostProvisioner)
        }
        
        app = new SimpleApp();
        montereyNetwork = new MontereyNetwork();
        montereyNetwork.setOwner(app);
        montereyNetwork.setConfig(MontereyContainerNode.NETWORK_NODE_INSTALL_DIR, MONTEREY_NETWORK_NODE_PATH);
        montereyNetwork.setConfig(MontereyManagementNode.MANAGEMENT_NODE_INSTALL_DIR, MONTEREY_MANAGEMENT_NODE_PATH);
        //montereyNetwork.setConfig(new MontereyNetworkConfig()); // using defaults; TODO externalize as configKeys
        montereyNetwork.setConfig(MontereyManagementNode.WEB_USERS_CREDENTIAL, Collections.singleton(adminCredential));
        montereyNetwork.setConfig(MontereyNetwork.MAX_CONCURRENT_PROVISIONINGS_PER_LOCATION, MAX_CONCURRENT_PROVISIONINGS_PER_LOCATION_VAL)
        app.getManagementContext().manage(app)
    }
    
    @AfterMethod
    public void tearDown() throws Exception {
        try {
            workloadExecutor.shutdownNow();
            if (montereyNetwork != null && montereyNetwork.isRunning()) {
                try {
                    montereyNetwork.releaseAllNodes();
                } finally {
                    montereyNetwork.stop();
                }
            }
        } finally {
            if (originalClassLoadingContext != null) {
                ClassLoadingContext.Defaults.setDefaultClassLoadingContext(originalClassLoadingContext);
            }
        }
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testStartMontereyManagementNodeAndDeployApp() throws Exception {
        rolloutManagementPlane();
        assertMontereyRunningWithApp(montereyNetwork);
    }

    @Test(groups = [ "Integration", "Live" ])
    public void testStartMontereyNetworkNode() throws Throwable {
        rolloutManagementPlane();
        
        montereyNetwork.provisionNode(localhostProvisioner);
        
        assertBrooklynEventuallyHasNodes(0,0,0,0,1);
    }

    @Test(groups = [ "Integration", "Live" ])
    public void testInitialClusterSizeStartsNodes() throws Throwable {
        rolloutManagementPlane([(MontereyNetwork.INITIAL_TOPOLOGY_PER_LOCATION):
                [(Dmn1NodeType.LPP):1, (Dmn1NodeType.M):1, (Dmn1NodeType.MR):1, (Dmn1NodeType.TP):1, (Dmn1NodeType.SPARE):1]]);
        assertBrooklynEventuallyHasNodes(1,1,1,1,1);
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testNetworkNodesAddedToClusterGroups() throws Throwable {
        rolloutManagementPlane();
        rolloutNodes(1,1,1,1,0);
        assertBrooklynEventuallyHasNodes(1,1,1,1,0);
        
        montereyNetwork.getClusters(localhostProvisioner).values().each { it.rescanEntities() }
        assertBrooklynEventuallyHasNodesInExpectedClusters()
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testStartMontereyNetworkNodeOfEachType() throws Throwable {
        rolloutManagementPlane();
        Map<NodeId, NodeSummary> nodes = rolloutNodes(1,1,1,1,1);
        assertBrooklynEventuallyHasNodes(1,1,1,1,1);
        assertBrooklynEventuallyHasNodes(nodes);
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testBrooklynEntityRolloutEffector() throws Throwable {
        rolloutManagementPlane();
        MontereyContainerNode node = montereyNetwork.provisionNode(localhostProvisioner);
        
        node.rollout(Dmn1NodeType.MR);
        
        assertMontereyEventuallyHasTologology(0,1,0,0,0);
        assertBrooklynEventuallyHasExpectedNodes();

        // Call revert, and assert monterey + brooklyn see the change
        node.revert();
        
        assertMontereyEventuallyHasTologology(0,0,0,0,1);
        assertBrooklynEventuallyHasExpectedNodes();
    }

    @Test(groups = [ "Integration", "Live" ])
    public void testBrooklynEntitiesHaveSegmentsDefinedInDescriptor() throws Throwable {
        rolloutManagementPlane();

        Map<String,SegmentSummary> expectedSegments = [:]
        HELLO_CLOUD_SEGMENTS.each {
            expectedSegments.put(it.getUid(), it)
        }

        assertBrooklynEventuallyHasSegments(expectedSegments);
    }

    @Test(groups = [ "Integration", "Live" ])
    public void testLppRouterSwitchover() throws Throwable {
        rolloutManagementPlane();
        rolloutNodes(1,2,1,1,0);
        assertBrooklynEventuallyHasNodes(1,2,1,1,0);
        
        // Get LPP, and rewire it
        NodeId lppId = findNodesMatching(Dmn1NodeType.LPP).iterator().next();
        LppNode lppNode = (LppNode) montereyNetwork.getMontereyNodes().get(lppId);
        Collection<NodeId> mrIds = findNodesMatching(Dmn1NodeType.MR);
        NodeId oldMrId = lppNode.getAttribute(MontereyAttributes.DOWNSTREAM_ROUTER);
        NodeId newMrId = findRelativeComplement(mrIds, Collections.singleton(oldMrId)).iterator().next();
        MrNode newMr = (MrNode) montereyNetwork.getMontereyNodes().get(newMrId);
        
        lppNode.routerSwitchover(newMr);
        
        assertBrooklynEventuallyHasDownstreamRouters(Collections.singletonMap(lppId, newMrId));
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testMediatorRouterSwitchover() throws Throwable {
        rolloutManagementPlane();
        rolloutNodes(1,1,1,2,0);
        assertBrooklynEventuallyHasNodes(1,1,1,2,0);
        
        // Get M, and rewire it
        NodeId lppId = findNodesMatching(Dmn1NodeType.M).iterator().next();
        MediatorNode mNode = (MediatorNode) montereyNetwork.getMontereyNodes().get(lppId);
        Collection<NodeId> tpIds = findNodesMatching(Dmn1NodeType.TP);
        NodeId oldTpId = mNode.getAttribute(MontereyAttributes.DOWNSTREAM_ROUTER);
        NodeId newTpId = findRelativeComplement(tpIds, Collections.singleton(oldTpId)).iterator().next();
        TpNode newTp = (TpNode) montereyNetwork.getMontereyNodes().get(newTpId);
        
        mNode.routerSwitchover(newTp);
        
        assertBrooklynEventuallyHasDownstreamRouters(Collections.singletonMap(lppId, newTpId));
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testClustersAndFabricsCreatedPerEmptyLocation() throws Throwable {
        rolloutManagementPlane();

        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        assertBrooklynEventuallyHasFabrics([localhostProvisioner]);
        assertBrooklynEventuallyHasClusters([localhostProvisioner]);
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testClustersAndFabricsCreatedPerLocationIncludeNodes() throws Throwable {
        rolloutManagementPlane();
        
        rolloutNodes(localhostProvisioner, 1, 1, 1, 1, 0);

        assertBrooklynEventuallyHasFabrics([localhostProvisioner]);
        assertBrooklynEventuallyHasClusters([localhostProvisioner]);
        assertBrooklynEventuallyHasNodesInCluster(localhostProvisioner, [(Dmn1NodeType.LPP):1, (Dmn1NodeType.MR):1, 
                (Dmn1NodeType.M):1, (Dmn1NodeType.TP):1]);
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testMediatorGroupMigrateSegmentEffector() throws Throwable {
        rolloutManagementPlane();
        
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        
        // One of each lpp, mr, tp; plus 2 mediators
        rolloutNodes(localhostProvisioner, 1,1,2,1,0);

        assertBrooklynEventuallyHasNodesInCluster(localhostProvisioner, [(Dmn1NodeType.LPP):1, (Dmn1NodeType.MR):1,
                (Dmn1NodeType.M):2, (Dmn1NodeType.TP):1]);

        // Pick a group; allocate a segment there; try to move the segment
        MediatorGroup mediatorGroup = montereyNetwork.getMediatorClusters().values().iterator().next();
        MediatorNode mediator1 = (MediatorNode) getAt(mediatorGroup.getMembers(), 0);
        MediatorNode mediator2 = (MediatorNode) getAt(mediatorGroup.getMembers(), 1);
        
        PlumberWebProxy plumber = newMontereyPlumber();
        plumber.migrateSegment("a", mediator1.getNodeId()).get(TIMEOUT, TimeUnit.MILLISECONDS);
        
        mediatorGroup.moveSegment("a", mediator2);
        
        assertMontereyEventuallyHasSegmentAllocation(Collections.singletonMap("a", mediator2.getNodeId()));
        assertBrooklynEventuallyHasSegmentAllocation(Collections.singletonMap("a", mediator2));
    }
    
    @Test(groups = [ "Integration", "Live" ])
    public void testNodesAndSegmentsReportWorkrate() throws Throwable {
        rolloutManagementPlane();
        rolloutNodes(localhostProvisioner, 1,1,1,1,0);
        
        // Check workrates are zero
        // TODO Check for other things, other than just mediator and segments
        NodeId mediatorId = findNodesMatching(Dmn1NodeType.M).iterator().next();
        assertBrooklynEventuallyHasExpectedNodeAttributeValues(ImmutableMap.<NodeId, Map<AttributeSensor,Filter>>builder()
                .put(mediatorId, ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.equal(0d))
                        .build())
                .build());
        assertBrooklynEventuallyHasExpectedSegmentAttributeValues(ImmutableMap.<String, Map<AttributeSensor,Filter>>builder()
                .put("a", ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.equal(0d))
                        .build())
                .put("b", ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.equal(0d))
                        .build())
                .build());
        
        // Apply load, and assert workrate goes up
        // TODO Can I use DmnAssertionUtils? It takes a ManagementNode, and it currently doesn't do workload for no-api
        final double desiredWorkratePerSecond = 10;
        createHelloCloudLoad("a", desiredWorkratePerSecond);
        
        assertBrooklynEventuallyHasExpectedNodeAttributeValues(ImmutableMap.<NodeId, Map<AttributeSensor,Filter>>builder()
                .put(mediatorId, ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.between(0.1d, desiredWorkratePerSecond))
                        .build())
                .build());
        assertBrooklynEventuallyHasExpectedSegmentAttributeValues(ImmutableMap.<String, Map<AttributeSensor,Filter>>builder()
                .put("a", ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.between(0.1d, desiredWorkratePerSecond))
                        .build())
                .put("b", ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.equal(0d))
                        .build())
                .build());
    }

    private void rolloutManagementPlane(Map<? extends ConfigKey, ? extends Object> config=[:]) throws Throwable {
        montereyNetwork.setAppDescriptor(newHelloCloudMontereyDeploymentDescriptor());
        montereyNetwork.setConfig(MontereyNetwork.APP_BUNDLES, Collections.singleton(HELLO_CLOUD_BUNDLE_URL));
        for (Map.Entry<? extends ConfigKey, ? extends Object> entry in config.entrySet()) {
            montereyNetwork.setConfig(entry.key, entry.value)
        }
        
        montereyNetwork.start([localhostProvisioner]);
    }
    
    private Map<NodeId, NodeSummary> rolloutNodes(int lpp, int mr, int m, int tp, int spare) throws Throwable {
        return rolloutNodes(localhostProvisioner, lpp, mr, m, tp, spare)
    }
    
    private Map<NodeId, NodeSummary> rolloutNodes(Location loc, int lpp, int mr, int m, int tp, int spare) throws Throwable {
        montereyNetwork.rolloutNodes(loc, [(Dmn1NodeType.LPP):lpp, (Dmn1NodeType.MR):mr, (Dmn1NodeType.M):m, (Dmn1NodeType.TP):tp, (Dmn1NodeType.SPARE):spare]);
        
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        return networkInfo.getNodeSummaries();
    }

    private Map<NodeId, NodeSummary> rolloutNodes(MontereyActiveLocation loc, int lpp, int mr, int m, int tp, int spare) throws Throwable {
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        ProvisionerWebProxy provisioner = newMontereyProvisioner();
        PlumberWebProxy plumber = newMontereyPlumber();
        
        // Create nodes
        int numNodes = lpp+mr+m+tp+spare;
        CloudProviderAccountAndLocationId locId = LocationUtils.toAccountAndLocationId(loc);
        DmnFuture<Collection<NodeId>> provisioningFuture = provisioner.createNodesAt(numNodes, locId);
        Collection<NodeId> provisionedNodes = provisioningFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);
        
        DmnFuture<Collection<NodeId>> future = plumber.rolloutNodes(new NodesRolloutConfiguration.Builder()
                .nodesToUse(provisionedNodes).lpps(lpp).mrs(mr).ms(m).tps(tp).build());
        future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        
        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());
        return networkInfo.getNodeSummaries();
    }
    
    private ScheduledFuture<?> createHelloCloudLoad(final String segment, double msgsPerSec) throws Exception {
        final HelloCloudServiceLocator serviceLocator = newHelloCloudServiceLocator();
        long period = (long)(1000/msgsPerSec);
        final AtomicInteger i = new AtomicInteger();
        return workloadExecutor.scheduleAtFixedRate(
                new Runnable() {
                    @Override public void run() {
                        serviceLocator.getService(segment).hello(""+i.incrementAndGet());
                    }
                },
                0,
                period,
                TimeUnit.MILLISECONDS);
    }
    
    private CloudEnvironmentDto newSimulatorCloudEnvironment() throws Exception {
        SimulatorAccountConfig accountConfig = new SimulatorAccountConfig();
        CloudAccountDto cloudAccountDto = new CloudAccountDto(new CloudAccountIdImpl("mycloudaccid"), SimulatorProvider.EMBEDDED_SIMULATOR_PROVIDER_ID);
        Collection<MontereyLocation> montereyLocations = MontereyLocationsDsl.parse(SimulatorProvider.class.getResource(SIMULATOR_LOCATIONS_CONF_PATH)).values();
        ProvisioningConfigDto provisioningConfigDto = new ProvisioningConfigDto(accountConfig.generateResourceProvisionerConf());
        CloudProviderSelectionDto cloudProviderDto = new CloudProviderSelectionDto(cloudAccountDto, montereyLocations, provisioningConfigDto);
        return new CloudEnvironmentDto("mycloudenvid", Collections.singleton(cloudProviderDto));
    }

    private MontereyDeploymentDescriptor newDummyMontereyDeploymentDescriptor() {
        return new MontereyDeploymentDescriptor.Builder()
                .appDescriptor(new MontereyApplicationDescriptor.Builder()
                        .name("dummy.appname")
                        .clientGateway("dummy.ClientFactory")
                        .segmentService("dummy.ServiceFactory")
                        .build())
                .build();
    }
    
    private MontereyDeploymentDescriptor newHelloCloudMontereyDeploymentDescriptor() {
        return new MontereyDeploymentDescriptor.Builder()
                .appDescriptor(new MontereyApplicationDescriptor.Builder()
                        .name("simple.appname")
                        .bundles(Collections.singleton(HELLO_CLOUD_BUNDLE_NAME))
                        .clientGateway(HELLO_CLOUD_CLIENT_FACTORY_NAME)
                        .segmentService(HELLO_CLOUD_SERVICE_FACTORY_NAME)
                        .build())
                .segments(HELLO_CLOUD_SEGMENTS)
                .managementBundles(Collections.singleton(HELLO_CLOUD_BUNDLE_NAME))
                .build();
    }
    
    private Dmn1NetworkInfoWebProxy newMontereyNetworkInfo() {
        return new Dmn1NetworkInfoWebProxy(montereyNetwork.getAttribute(MontereyNetwork.MANAGEMENT_URL), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
    }
    
    private PlumberWebProxy newMontereyPlumber() {
        return new PlumberWebProxy(montereyNetwork.getAttribute(MontereyNetwork.MANAGEMENT_URL), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
    }
    
    private ProvisionerWebProxy newMontereyProvisioner() {
        return new ProvisionerWebProxy(montereyNetwork.getAttribute(MontereyNetwork.MANAGEMENT_URL), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
    }
    
    private HelloCloudServiceLocator newHelloCloudServiceLocator() throws Exception {
        MontereyNetworkEndpointImpl networkEndpoint = new MontereyNetworkEndpointImpl();
        networkEndpoint.setManagementNodeUrl(montereyNetwork.getAttribute(MontereyNetwork.MANAGEMENT_URL));
        networkEndpoint.setLocation("GB-EDH");
        networkEndpoint.setUsername("myname");
        networkEndpoint.setPassword("mypass");
        networkEndpoint.setHasPrivateIp(true);
        networkEndpoint.start();
        
        return new HelloCloudServiceLocatorImpl(networkEndpoint);
    }

    private Set<NodeId> findNodesMatching(Dmn1NodeType type, MontereyActiveLocation loc) {
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        Set<NodeId> result = new LinkedHashSet<NodeId>();
        for (NodeSummary contender : networkInfo.getNodeSummaries().values()) {
            if (contender.getType() == type && contender.getMontereyActiveLocation().equals(loc)) {
                result.add(contender.getNodeId());
            }
        }
        return result;
    }
    
    private Collection<NodeId> findNodesMatching(Dmn1NodeType type) {
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        Collection<NodeId> result = new ArrayList<NodeId>();
        for (NodeSummary contender : networkInfo.getNodeSummaries().values()) {
            if (contender.getType() == type) {
                result.add(contender.getNodeId());
            }
        }
        return result;
    }
    
    private int countNodesOfType(Collection<NodeSummary> nodes, Dmn1NodeType type) {
        int result = 0;
        for (NodeSummary node : nodes) {
            if (node.getType() == type) result++;
        }
        return result;
    }

    private Set<NodeId> findContainerNodeIds() {
        Set<NodeId> result = new LinkedHashSet<NodeId>();
        for (MontereyContainerNode node : montereyNetwork.getContainerNodes()) {
            if (node.getNodeId() != null) result.add(node.getNodeId());
        }
        return result;
    }
    
    private MontereyContainerNode findContainerNode(NodeId nodeId) {
        for (MontereyContainerNode node : montereyNetwork.getContainerNodes()) {
            if (nodeId.equals(node.getNodeId())) return node;
        }
        throw new IllegalArgumentException("Node id not found, "+nodeId);
    }
    
    private Location toBrooklynLocation(MontereyActiveLocation loc) {
        return montereyNetwork.getLocationRegistry().getConvertedLocation(loc);
    }

    private Set<Location> toBrooklynLocations(Collection<MontereyActiveLocation> locs) {
        final Set<Location> result = new LinkedHashSet<Location>();
        for (MontereyActiveLocation loc : locs) result.add(toBrooklynLocation(loc));
        return result;
    }

    private void assertMontereyRunningWithApp(MontereyNetwork montereyNetwork) {
        DeploymentWebProxy deploymentWebProxy = new DeploymentWebProxy(montereyNetwork.getAttribute(MontereyNetwork.MANAGEMENT_URL), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
        Assert.assertTrue(deploymentWebProxy.isApplicationUndeployable());
    }
    
    private void assertMontereyEventuallyHasTologology(final int lpps, final int mrs, final int ms, final int tps, final int spares) throws Throwable {
        final Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<NodeId, NodeSummary> actual = networkInfo.getNodeSummaries();
                Assert.assertEquals(countNodesOfType(actual.values(), Dmn1NodeType.LPP), lpps);
                Assert.assertEquals(countNodesOfType(actual.values(), Dmn1NodeType.MR), mrs);
                Assert.assertEquals(countNodesOfType(actual.values(), Dmn1NodeType.M), ms);
                Assert.assertEquals(countNodesOfType(actual.values(), Dmn1NodeType.TP), tps);
                Assert.assertEquals(countNodesOfType(actual.values(), Dmn1NodeType.SPARE), spares);
                return null;
            }}, TIMEOUT);
    }
    
    private void assertMontereyEventuallyHasSegmentAllocation(final Map<String,NodeId> expected) throws Throwable {
        final Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<String,NodeId> actual = networkInfo.getSegmentAllocations();
                Assert.assertTrue(actual.keySet().containsAll(expected.keySet()), "actual="+actual+"; exepcted="+expected);
                
                // note it's ok to have additional segments...
                for (String segment in expected.keySet()) {
                    Assert.assertEquals(actual.get(segment), expected.get(segment), "segment="+segment);
                }
                return null;
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasSegmentAllocation(final Map<String,MediatorNode> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Segment> actualSegments = montereyNetwork.getSegments();
                Assert.assertTrue(actualSegments.keySet().containsAll(expected.keySet()), "actual="+actualSegments+"; exepcted="+expected);
                for (Map.Entry<String, MediatorNode> entry : expected.entrySet()) {
                    String segment = entry.getKey();
                    NodeId expectedMediator = entry.getValue().getNodeId();
                    NodeId actualMediator = actualSegments.get(segment)?.getAttribute(Segment.MEDIATOR);
                    Assert.assertEquals(actualMediator, expectedMediator, "segment="+segment);
                }
                return null;
            }}, TIMEOUT);
    }

    private void assertBrooklynEventuallyHasNodes(int lpp, int mr, int m, int tp, int spare) throws Throwable {
        final Map<Dmn1NodeType, Integer> expectedCounts = new ImmutableMap.Builder<Dmn1NodeType, Integer>()
                .put(Dmn1NodeType.LPP, lpp)
                .put(Dmn1NodeType.MR, mr)
                .put(Dmn1NodeType.M, m)
                .put(Dmn1NodeType.TP, tp)
                .put(Dmn1NodeType.SPARE, spare)
                .build();
        
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                for (Dmn1NodeType nodeType : Arrays.asList(Dmn1NodeType.LPP, Dmn1NodeType.MR, Dmn1NodeType.M, Dmn1NodeType.TP, Dmn1NodeType.SPARE)) {
                    Collection<NodeId> nodesOfType = findNodesMatching(nodeType);
                    Assert.assertEquals((Integer)nodesOfType.size(), expectedCounts.get(nodeType));
                    
                    for (NodeId nodeId : nodesOfType) {
                        MontereyContainerNode actualContainerNode = findContainerNode(nodeId);
                        
                        Assert.assertEquals(actualContainerNode.getNodeId(), nodeId);
                        AbstractMontereyNode montereyNetworkNode = actualContainerNode.getContainedMontereyNode();
                        Assert.assertNotNull(montereyNetworkNode);
                        Assert.assertEquals(montereyNetworkNode.getNodeType(), nodeType);
                    }
                }
                return null;
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasNodesInExpectedClusters() {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                montereyNetwork.getMontereyNodes().values().each {
                    if (it) {
                        // expect to be a member of "cluster" and "fabric"
                        Assert.assertEquals(it.groups.size(), 2)
                        it.groups.each { Group group ->
                            Assert.assertEquals(it.nodeType, group.nodeType)
                            assertContainsLocation(group.locations, it.locations.iterator().next())
                        }
                    }
                }
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasExpectedNodes() throws Throwable {
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries())
    }
    
    private void assertBrooklynEventuallyHasNodes(final Map<NodeId,NodeSummary> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Assert.assertEquals(findContainerNodeIds(), expected.keySet());
                for (Map.Entry<NodeId,NodeSummary> e : expected.entrySet()) {
                    NodeSummary expectedNodeSummary = e.getValue();
                    MontereyContainerNode actualContainerNode = findContainerNode(e.getKey());
                    
                    Assert.assertEquals(actualContainerNode.getNodeId(), expectedNodeSummary.getNodeId());

                    AbstractMontereyNode montereyNetworkNode = actualContainerNode.getContainedMontereyNode();
                    Assert.assertNotNull(montereyNetworkNode);
                    Assert.assertEquals(montereyNetworkNode.getNodeType(), expectedNodeSummary.getType());
                }
                return null;
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasSegments(final Map<String,SegmentSummary> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Segment> actual = montereyNetwork.getSegments();
                Assert.assertEquals(actual.keySet(), expected.keySet());
                return null;
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasDownstreamRouters(final Map<NodeId,NodeId> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                for (Map.Entry<NodeId,NodeId> entry : expected.entrySet()) {
                    NodeId sourceId = entry.getKey();
                    NodeId expectedDownstreamId = entry.getValue();
                    AbstractMontereyNode montereyNetworkNode = montereyNetwork.getMontereyNodes().get(sourceId);
                    Assert.assertEquals(montereyNetworkNode.getAttribute(LppNode.DOWNSTREAM_ROUTER), expectedDownstreamId);
                }
                return null;
            }}, TIMEOUT);
    }

    private void assertBrooklynEventuallyHasFabrics(Collection<Location> expectedLocs) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                for (Dmn1NodeType nodeType : Arrays.asList(Dmn1NodeType.LPP, Dmn1NodeType.MR, Dmn1NodeType.M, Dmn1NodeType.TP)) {
                    MontereyNodeGroup fabric = montereyNetwork.getFabric(nodeType);
                    Assert.assertEquals(new LinkedHashSet<Location>(fabric.getLocations()), expectedLocs);
                    Assert.assertEquals(fabric.getNodeType(), nodeType);
                }
                return null;
            }}, TIMEOUT);
    }

    private void assertBrooklynEventuallyHasClusters(Collection<Location> expectedLocs) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                for (Dmn1NodeType nodeType : Arrays.asList(Dmn1NodeType.LPP, Dmn1NodeType.MR, Dmn1NodeType.M, Dmn1NodeType.TP)) {
                    Map<Location,MontereyNodeGroup> clusters = montereyNetwork.getClusters(nodeType);
                    Assert.assertEquals(expectedLocs, clusters.keySet(), "type="+nodeType);
                    
                    for (Location loc : expectedLocs) {
                        MontereyNodeGroup cluster = clusters.get(loc);
                        Assert.assertEquals(new HashSet<Location>(cluster.getLocations()), Collections.singleton(loc), "loc="+loc+",type="+nodeType);
                    }
                }
                return null;
            }}, TIMEOUT);
    }

    private void assertBrooklynEventuallyHasNodesInCluster(final Location loc, final Map<Dmn1NodeType,Integer> expectedNodesPerCluster) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                for (Map.Entry<Dmn1NodeType,Integer> entry in expectedNodesPerCluster.entrySet()) {
                    Dmn1NodeType nodeType = entry.key
                    Integer numExpected = entry.value
                    MontereyNodeGroup cluster = montereyNetwork.getClusters(nodeType).get(loc);
                    Collection<AbstractMontereyNode> preScanMembers = cluster.getMembers();
                    
                    cluster.rescanEntities()
                    Collection<AbstractMontereyNode> members = cluster.getMembers();
                    Assert.assertEquals(cluster.getNodeType(), nodeType, "loc="+loc+",type="+nodeType);
                    Assert.assertEquals(members.size(), numExpected, ""+nodeType+" has "+members);
                    for (AbstractMontereyNode member in members) {
                        Assert.assertEquals(member.getNodeType(), nodeType, ""+member+" of type "+member.getNodeType())
                    }
                    // TODO Could check no duplicates; could compare with monterey-management-node's opinion
                }
                return null;
            }}, TIMEOUT);
    }

    private void assertBrooklynEventuallyHasExpectedNodeAttributeValues(final Map<NodeId,Map<AttributeSensor,Filter>> expectedNodes) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<NodeId, AbstractMontereyNode> actualNodes = montereyNetwork.getMontereyNodes();
                Map<NodeId,Map<AttributeSensor,Object>> valsForLogging = new LinkedHashMap<NodeId, Map<AttributeSensor,Object>>();
                
                for (Map.Entry<NodeId,Map<AttributeSensor,Filter>> entry : expectedNodes.entrySet()) {
                    NodeId nodeId = entry.getKey();
                    AbstractMontereyNode actualNode = actualNodes.get(nodeId);
                    Map<AttributeSensor, Object> sensorVals = assertEntityAttributes(actualNode, entry.getValue());
                    valsForLogging.put(nodeId, sensorVals);
                }
                LOG.info("Node sensor values: "+valsForLogging);
                return null;
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasExpectedSegmentAttributeValues(final Map<String,Map<AttributeSensor,Filter>> expectedSegments) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Segment> actualSegments = montereyNetwork.getSegments();
                Map<String,Map<AttributeSensor,Object>> valsForLogging = new LinkedHashMap<String, Map<AttributeSensor,Object>>();
                
                for (Map.Entry<String,Map<AttributeSensor,Filter>> entry : expectedSegments.entrySet()) {
                    String segmentId = entry.getKey();
                    Segment actualSegment = actualSegments.get(segmentId);
                    Map<AttributeSensor, Object> sensorVals = assertEntityAttributes(actualSegment, entry.getValue());
                    valsForLogging.put(segmentId, sensorVals);
                }
                LOG.info("Segment sensor values: "+valsForLogging);
                return null;
            }}, TIMEOUT);
    }
    
    private Map<AttributeSensor,Object> assertEntityAttributes(EntityLocal entity, Map<AttributeSensor,Filter> expectations) throws Exception {
        Map<AttributeSensor,Object> result = new LinkedHashMap<AttributeSensor, Object>();
        for (Map.Entry<AttributeSensor,Filter> entry : expectations.entrySet()) {
            AttributeSensor sensor = entry.getKey();
            Filter filter = entry.getValue();
            Object actualVal = entity.getAttribute(sensor);
            Assert.assertTrue(filter.accept(actualVal), "entity="+entity+"; sensor="+sensor+", val="+actualVal);
            result.put(sensor, actualVal);
        }
        return result;
    }
    
    private void assertContainsLocation(Collection<Location> containers, Location sub) {
        for (Location container : containers) {
            Location sub2 = sub
            while (sub2 != null) {
                if (container == sub2) return
                sub2 = sub2.getParentLocation()
            }
        }
        Assert.fail("Location $sub is not contained within $containers")
    }
            
    private <T> T assertSuccessWithin(final Callable<T> callable, final long timeout) throws Throwable {
        final AtomicReference<T> result = new AtomicReference<T>();
        final AtomicReference<Throwable> lastError = new AtomicReference<Throwable>();
        final AtomicInteger count = new AtomicInteger();
        try {
            boolean success = waitUtils.waitFor(timeout, new ConditionWithMessage() {
                public Boolean evaluate() {
                    try {
                        try {
                            result.set(callable.call());
                            return true;
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    } catch (RuntimeInterruptedException e) {
                        lastError.set(e);
                        throw e;
                    } catch (Throwable t) {
                        lastError.set(t);
                        count.incrementAndGet();
                        if (LOG.isLoggable(Level.FINER)) LOG.log(Level.FINER, "Waiting "+TimeUtils.makeTimeString(timeout)+" for "+callable+"; failed "+count+" times", t);
                        return false;
                    }
                }
                public String getMessage() {
                    Object val = result.get();
                    return callable+(val!=null ? "->"+val : "");
                }
            });
        
            if (!success) {
                if (lastError.get() != null) {
                    Throwable unwrapped = ExceptionUtils.unwrapThrowable(lastError.get());
                    throw new RuntimeException("Condition not be satisfied within"+TimeUtils.makeTimeString(timeout)+": "+unwrapped, unwrapped);
                } else {
                    Assert.fail("Did not return within "+TimeUtils.makeTimeString(timeout));
                }
            }
            return result.get();
        } catch (InterruptedException e) {
            throw ExceptionUtils.throwRuntime(e);
        }
    }
    
    /**
     * The relative complement of A with respect to a set B, is the set of elements in B but not in A.
     * Therefore, returns the elements that are in col but that are not in other.
     */
    private static <T> Collection<T> findRelativeComplement(Collection<T> col, Collection<?> other) {
        Set<T> result = new LinkedHashSet<T>(col);
        result.removeAll(other);
        return Collections.unmodifiableSet(result);
    }
    
    private static <T> T getAt(Collection<T> col, int index) {
        int i = 0;
        for (T val : col) {
            if (i == index) return val;
            i++;
        }
        throw new IndexOutOfBoundsException("index "+index+" out of range for collection of size "+col.size()+"; col="+col);
    }
    
}
