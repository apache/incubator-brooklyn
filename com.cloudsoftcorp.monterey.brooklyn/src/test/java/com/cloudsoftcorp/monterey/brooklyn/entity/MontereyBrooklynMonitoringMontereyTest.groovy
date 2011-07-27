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

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation

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
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo
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

/**
 * Tests where brooklyn polls the monterey-management-node, and creates entities to 
 * reflect what it's told.
 * 
 * Note that provisioning (except the first management node) is done inside the old
 * monterey-management-node instead of in brooklyn...
 */
public class MontereyBrooklynMonitoringMontereyTest extends CloudsoftThreadMonitoringTestFixture {
    
    // FIXME WorkInProgress; needs tidied; much of this is superseded by MontereyBrooklynProvisioningTest

    private static final Logger LOG = Loggers.getLogger(MontereyBrooklynMonitoringMontereyTest.class);

    private static final String SIMULATOR_LOCATIONS_CONF_PATH = "/locations-simulator.conf";
    
    private static final String MONTEREY_MANAGEMENT_NODE_PATH = "~/monterey-management-node";
    private static final String SSH_HOST_NAME = "localhost";
    private static final String SSH_USERNAME = "aled";
    
    private static final String APP_BUNDLE_RESOURCE_PATH = "com.cloudsoftcorp.monterey.example.noapisimple.jar";
    private static final String HELLO_CLOUD_BUNDLE_NAME = "com.cloudsoftcorp.monterey.example.noapisimple";
    private static final String HELLO_CLOUD_CLIENT_FACTORY_NAME = "com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudClientFactory";
    private static final String HELLO_CLOUD_SERVICE_FACTORY_NAME = "com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceFactory";
    private static final URL HELLO_CLOUD_BUNDLE_URL = MontereyBrooklynMonitoringMontereyTest.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
    static {
        if (HELLO_CLOUD_BUNDLE_URL == null) {
            throw new RuntimeException("Hello cloud bundle not found: "+APP_BUNDLE_RESOURCE_PATH);
        }
    }
    
    private static final long TIMEOUT = 30*1000;
    
    private Gson gson;
    private SshMachineLocation localhost;
    private FixedListMachineProvisioningLocation localhostProvisioner;
    private AbstractApplication app;
    private MontereyNetwork montereyNetwork;
    private UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
    private ScheduledExecutorService workloadExecutor = Executors.newScheduledThreadPool(10);
    
    private ClassLoadingContext originalClassLoadingContext;

    
    @BeforeMethod
    public void setUp() throws Exception {
        originalClassLoadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext(); 
        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereyBrooklynMonitoringMontereyTest.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);
        GsonSerializer gsonSerializer = new GsonSerializer(classLoadingContext);
        gson = gsonSerializer.getGson();

        // FIXME Delete SSH_USERNAME
        localhost = new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]);

        localhostProvisioner = new FixedListMachineProvisioningLocation<SshMachineLocation>([machines:[localhost]])
        
        app = new SimpleApp();
        montereyNetwork = new MontereyNetwork();
        montereyNetwork.setOwner(app);
        montereyNetwork.setManagementNodeInstallDir(MONTEREY_MANAGEMENT_NODE_PATH);
        MontereyNetworkConfig config = new MontereyNetworkConfig();
        montereyNetwork.setConfig(config);
        montereyNetwork.setWebUsersCredentials(Collections.singleton(adminCredential));
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
    
    @Test(enabled=false)
    public void testAddingSegmentsCreatesBrooklynEntities() throws Throwable {
        rolloutManagementPlane();

        Map<String,SegmentSummary> expectedSegments = ImmutableMap.<String,SegmentSummary>builder()
                .put("a", SegmentSummary.Factory.newInstance("a"))
                .put("b", SegmentSummary.Factory.newInstance("b"))
                .build();
        
        PlumberWebProxy plumber = newMontereyPlumber();
        DmnFuture<Object> future = plumber.addSegments(expectedSegments.values());
        future.get(TIMEOUT, TimeUnit.MILLISECONDS);

        assertBrooklynEventuallyHasSegments(expectedSegments);
    }

    @Test(enabled=false)
    public void testRolloutNodesCreatesBrooklynEntities() throws Throwable {
        rolloutManagementPlane();
        Map<NodeId, NodeSummary> nodes = rolloutNodes(1,1,1,1,1);
        assertBrooklynEventuallyHasNodes(nodes);
    }
    
    @Test(enabled=false)
    public void testLppRouterSwitchover() throws Throwable {
        rolloutManagementPlane();
        rolloutNodes(1,2,1,1,0);

        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        PlumberWebProxy plumber = newMontereyPlumber();
        
        Collection<NodeId> oldTargets = networkInfo.getTopology().getTargetsOf(lppId)
        NodeId lppId = findNodesMatching(Dmn1NodeType.LPP).iterator().next();
        Collection<NodeId> mrIds = findNodesMatching(Dmn1NodeType.MR);
        NodeId newMrId = findRelativeComplement(mrIds, oldTargets).iterator().next();
        
        plumber.routerSwitchover(lppId, newMrId);
        
        assertBrooklynEventuallyHasDownstreamRouters(Collections.singletonMap(lppId, newMrId));
    }
    
    @Test(enabled=false)
    public void testMediatorRouterSwitchover() throws Throwable {
        rolloutManagementPlane();
        rolloutNodes(1,1,1,2,0);
        
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
    
    @Test(enabled=false)
    public void testClustersAndFabricsCreatedPerEmptyLocation() throws Throwable {
        rolloutManagementPlane();

        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        assertBrooklynEventuallyHasFabrics(networkInfo);
        assertBrooklynEventuallyHasClusters(networkInfo);
    }
    
    @Test(enabled=false)
    public void testClustersAndFabricsCreatedPerLocationIncludeNodes() throws Throwable {
        rolloutManagementPlane();
        
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        
        for (MontereyActiveLocation loc : networkInfo.getActiveLocations()) {
            rolloutNodes(loc, 1, 1, 1, 1, 0);
        }

        assertBrooklynEventuallyHasFabrics(networkInfo);
        assertBrooklynEventuallyHasClusters(networkInfo);
    }
    
    @Test(enabled=false)
    public void testMediatorGroupMigrateSegmentEffector() throws Throwable {
        rolloutManagementPlane();
        
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        PlumberWebProxy plumber = newMontereyPlumber();
        
        // One of each lpp, mr, tp; plus 2 mediators per location
        rolloutNodes(1,1,0,1,0);

        for (MontereyActiveLocation loc : networkInfo.getActiveLocations()) {
            rolloutNodes(loc, 0, 0, 2, 0, 0);
        }

        // Check that the mediator groups exist
        assertBrooklynEventuallyHasClusters(networkInfo);
        
        // Pick a group; allocate a segment there; try to move the segment
        MediatorGroup mediatorGroup = montereyNetwork.getMediatorClusters().values().iterator().next();
        MediatorNode mediator1 = (MediatorNode) getAt(mediatorGroup.getMembers(), 0);
        MediatorNode mediator2 = (MediatorNode) getAt(mediatorGroup.getMembers(), 1);
        
        plumber.addSegments(Collections.singleton(SegmentSummary.Factory.newInstance("a"))).get(TIMEOUT, TimeUnit.MILLISECONDS);
        plumber.migrateSegment("a", mediator1.getNodeId()).get(TIMEOUT, TimeUnit.MILLISECONDS);
        
        mediatorGroup.moveSegment("a", mediator2);
        
        assertMontereyEventuallyHasSegmentAllocation(Collections.singletonMap("a", mediator2.getNodeId()));
        assertBrooklynEventuallyHasSegmentAllocation(Collections.singletonMap("a", mediator2));
    }
    
    @Test(enabled=false)
    public void testBrooklynEntityRolloutEffector() throws Throwable {
        rolloutManagementPlane();

        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        ProvisionerWebProxy provisioner = newMontereyProvisioner();
        
        // Create 1 node
        MontereyActiveLocation aMontereyLocation = networkInfo.getActiveLocations().iterator().next();
        CloudProviderAccountAndLocationId aMontereLocationId = LocationUtils.toAccountAndLocationId(aMontereyLocation);
        DmnFuture<Collection<NodeId>> provisioningFuture = provisioner.createNodesAt(1, aMontereLocationId);
        Collection<NodeId> provisionedNodes = provisioningFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);
        NodeId provisionedNode = provisionedNodes.iterator().next();
        
        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());
        
        // Call rollout, and assert monterey + brooklyn see the change
        MontereyContainerNode node = findContainerNode(provisionedNode);
        node.rollout(Dmn1NodeType.MR);
        
        assertMontereyEventuallyHasTologology(0,1,0,0,0);
        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());

        // Call revert, and assert monterey + brooklyn see the change
        node.revert();
        
        assertMontereyEventuallyHasTologology(0,0,0,0,1);
        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());
    }

    @Test(enabled=false)
    public void testNodesAndSegmentsReportWorkrate() throws Throwable {
        rolloutManagementPlane();

        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        ProvisionerWebProxy provisioner = newMontereyProvisioner();
        PlumberWebProxy plumber = newMontereyPlumber();
        
        // Rollout a 1-1-1-1 network
        MontereyActiveLocation aMontereyLocation = networkInfo.getActiveLocations().iterator().next();
        CloudProviderAccountAndLocationId aMontereLocationId = LocationUtils.toAccountAndLocationId(aMontereyLocation);
        DmnFuture<Collection<NodeId>> provisioningFuture = provisioner.createNodesAt(4, aMontereLocationId);
        Collection<NodeId> provisionedNodes = provisioningFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

        DmnFuture<Collection<NodeId>> rolloutingFuture = plumber.rolloutNodes(new NodesRolloutConfiguration.Builder()
                .nodesToUse(provisionedNodes)
                .lpps(1)
                .mrs(1)
                .ms(1)
                .tps(1)
                .build());
        
        rolloutingFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

        // Allocate segments
        Map<String,SegmentSummary> expectedSegments = ImmutableMap.<String,SegmentSummary>builder()
                .put("a", SegmentSummary.Factory.newInstance("a"))
                .put("b", SegmentSummary.Factory.newInstance("b"))
                .build();
        DmnFuture<Object> future = plumber.addSegments(expectedSegments.values());
        future.get(TIMEOUT, TimeUnit.MILLISECONDS);

        // Finally, we're ready to do the actual test!
        // Check workrates are zero
        // TODO Check for other things, other than just mediator
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
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.between(0d, desiredWorkratePerSecond))
                        .build())
                .build());
        assertBrooklynEventuallyHasExpectedSegmentAttributeValues(ImmutableMap.<String, Map<AttributeSensor,Filter>>builder()
                .put("a", ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.between(0d, desiredWorkratePerSecond))
                        .build())
                .put("b", ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.equal(0d))
                        .build())
                .build());
    }

    private Map<NodeId, NodeSummary> rolloutNodes(int lpp, int mr, int m, int tp, int spare) throws Throwable {
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        MontereyActiveLocation loc = networkInfo.getActiveLocations().iterator().next();
        return rolloutNodes(loc, lpp, mr, m, tp, spare);
    }

    private void rolloutManagementPlane() throws Throwable {
        // Start the management plane (with "simulator" embedded network nodes)
        montereyNetwork.setCloudEnvironment(newSimulatorCloudEnvironment());
        montereyNetwork.setAppDescriptor(newHelloCloudMontereyDeploymentDescriptor());
        montereyNetwork.setAppBundles(Collections.singleton(HELLO_CLOUD_BUNDLE_URL));
        
        montereyNetwork.start([localhostProvisioner]);
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
                Assert.assertEquals(actual, expected);
                return null;
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasSegmentAllocation(final Map<String,MediatorNode> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Segment> actualSegments = montereyNetwork.getSegments();
                Assert.assertEquals(actualSegments.keySet(), expected.keySet());
                for (Map.Entry<String, Segment> entry : actualSegments.entrySet()) {
                    String segment = entry.getKey();
                    NodeId expectedMediator = expected.get(segment).getNodeId();
                    NodeId actualMediator = entry.getValue().getAttribute(Segment.MEDIATOR);
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

    private void assertBrooklynEventuallyHasFabrics(Dmn1NetworkInfo networkInfo) throws Throwable {
        final Collection<MontereyActiveLocation> expectedMontereyLocs = networkInfo.getActiveLocations();
        final Set<Location> expectedLocs = toBrooklynLocations(expectedMontereyLocs);
        
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

    private void assertBrooklynEventuallyHasClusters(Dmn1NetworkInfo networkInfo) throws Throwable {
        final Collection<MontereyActiveLocation> expectedMontereyLocs = networkInfo.getActiveLocations();
        final Set<Location> expectedLocs = toBrooklynLocations(expectedMontereyLocs);
        
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                for (Dmn1NodeType nodeType : Arrays.asList(Dmn1NodeType.LPP, Dmn1NodeType.MR, Dmn1NodeType.M, Dmn1NodeType.TP)) {
                    Map<Location,MontereyNodeGroup> clusters = montereyNetwork.getClusters(nodeType);
                    Assert.assertEquals(expectedLocs, clusters.keySet(), "type="+nodeType);
                    
                    for (MontereyActiveLocation montereyLoc : expectedMontereyLocs) {
                        Location loc = toBrooklynLocation(montereyLoc);
                        Collection<NodeId> expectedNodeIds = findNodesMatching(nodeType, montereyLoc);
                        MontereyNodeGroup cluster = clusters.get(loc);
                        Collection<NodeId> actualNodeIds = new LinkedHashSet<NodeId>();
                        for (Entity e : cluster.getMembers()) {
                            actualNodeIds.add( ((AbstractMontereyNode)e).getNodeId() );
                        }
                        Assert.assertEquals(new HashSet<Location>(cluster.getLocations()), Collections.singleton(loc), "loc="+loc+",type="+nodeType);
                        Assert.assertEquals(cluster.getNodeType(), nodeType, "loc="+loc+",type="+nodeType);
                        Assert.assertEquals(actualNodeIds, expectedNodeIds, "loc="+loc+",type="+nodeType);
                    }
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
