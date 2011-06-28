package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.testng.After;
import org.testng.Assert;
import org.testng.Before;
import org.testng.Test;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.location.basic.SshMachineLocation;

import com.cloudsoftcorp.monterey.CloudsoftThreadMonitoringTestFixture;
import com.cloudsoftcorp.monterey.clouds.dto.CloudAccountDto;
import com.cloudsoftcorp.monterey.clouds.dto.CloudEnvironmentDto;
import com.cloudsoftcorp.monterey.clouds.dto.CloudProviderSelectionDto;
import com.cloudsoftcorp.monterey.clouds.dto.ProvisioningConfigDto;
import com.cloudsoftcorp.monterey.clouds.simulator.SimulatorAccountConfig;
import com.cloudsoftcorp.monterey.clouds.simulator.SimulatorProvider;
import com.cloudsoftcorp.monterey.control.api.SegmentSummary;
import com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceLocator;
import com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceLocatorImpl;
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation;
import com.cloudsoftcorp.monterey.location.api.MontereyLocation;
import com.cloudsoftcorp.monterey.location.dsl.MontereyLocationsDsl;
import com.cloudsoftcorp.monterey.location.temp.impl.CloudAccountIdImpl;
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType;
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary;
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer;
import com.cloudsoftcorp.monterey.network.control.plane.web.DeploymentWebProxy;
import com.cloudsoftcorp.monterey.network.control.plane.web.Dmn1NetworkInfoWebProxy;
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy;
import com.cloudsoftcorp.monterey.network.control.plane.web.ProvisionerWebProxy;
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig;
import com.cloudsoftcorp.monterey.network.control.plane.web.api.ControlPlaneWebConstants.HTTP_AUTH;
import com.cloudsoftcorp.monterey.network.control.wipapi.CloudProviderAccountAndLocationId;
import com.cloudsoftcorp.monterey.network.control.wipapi.DmnFuture;
import com.cloudsoftcorp.monterey.network.control.wipapi.LocationUtils;
import com.cloudsoftcorp.monterey.network.control.wipapi.NodesRolloutConfiguration;
import com.cloudsoftcorp.monterey.network.deployment.MontereyApplicationDescriptor;
import com.cloudsoftcorp.monterey.network.deployment.MontereyDeploymentDescriptor;
import com.cloudsoftcorp.monterey.node.api.NodeId;
import com.cloudsoftcorp.monterey.servicebean.access.api.MontereyNetworkEndpointImpl;
import com.cloudsoftcorp.util.Loggers;
import com.cloudsoftcorp.util.TimeUtils;
import com.cloudsoftcorp.util.condition.Conditions.ConditionWithMessage;
import com.cloudsoftcorp.util.condition.Filter;
import com.cloudsoftcorp.util.condition.Filters;
import com.cloudsoftcorp.util.exception.ExceptionUtils;
import com.cloudsoftcorp.util.exception.RuntimeInterruptedException;
import com.cloudsoftcorp.util.javalang.ClassLoadingContext;
import com.cloudsoftcorp.util.javalang.OsgiClassLoadingContextFromBundle;
import com.cloudsoftcorp.util.osgi.BundleSet;
import com.cloudsoftcorp.util.web.client.CredentialsConfig;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

public class MontereyBrooklynProvisioningTest extends CloudsoftThreadMonitoringTestFixture {

    private static final Logger LOG = Loggers.getLogger(MontereyBrooklynProvisioningTest.class);

    private static final String SIMULATOR_LOCATIONS_CONF_PATH = "/locations-simulator.conf";
    
    private static final String MONTEREY_MANAGEMENT_NODE_PATH = "~/monterey-management-node";
    private static final String SSH_HOST_NAME = "localhost";
    private static final String SSH_USERNAME = "aled";
    
    private static final String APP_BUNDLE_RESOURCE_PATH = "com.cloudsoftcorp.monterey.example.noapisimple.jar";
    private static final String HELLO_CLOUD_BUNDLE_NAME = "com.cloudsoftcorp.monterey.example.noapisimple";
    private static final String HELLO_CLOUD_CLIENT_FACTORY_NAME = "com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudClientFactory";
    private static final String HELLO_CLOUD_SERVICE_FACTORY_NAME = "com.cloudsoftcorp.monterey.example.noapisimple.HelloCloudServiceFactory";
    private static final URL HELLO_CLOUD_BUNDLE_URL = MontereyBrooklynProvisioningTest.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
    
    private static final long TIMEOUT = 30*1000;
    
    private Gson gson;
    private SshMachineLocation localhost;
    private MontereyNetwork montereyNetwork;
    private UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
    
    private ClassLoadingContext originalClassLoadingContext;
    
    @Before
    public void setUp() throws Exception {
        originalClassLoadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext(); 
        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereyBrooklynProvisioningTest.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);
        GsonSerializer gsonSerializer = new GsonSerializer(classLoadingContext);
        gson = gsonSerializer.getGson();

        localhost = new SshMachineLocation();
        localhost.setName(SSH_HOST_NAME);
        localhost.setUser(SSH_USERNAME);
        localhost.setHost(SSH_HOST_NAME);

        montereyNetwork = new MontereyNetwork();
        montereyNetwork.setInstallDir(MONTEREY_MANAGEMENT_NODE_PATH);
        MontereyNetworkConfig config = new MontereyNetworkConfig();
        montereyNetwork.setConfig(config);
        montereyNetwork.setWebUsersCredentials(Collections.singleton(adminCredential));
    }
    
    @After
    public void tearDown() throws Exception {
        try {
            if (montereyNetwork != null) montereyNetwork .stop();
        } finally {
            if (originalClassLoadingContext != null) {
                ClassLoadingContext.Defaults.setDefaultClassLoadingContext(originalClassLoadingContext);
            }
        }
    }
    
    @Test
    public void testStartMontereyManagementNodeAndDeployApp() throws Exception {
        montereyNetwork.startOnHost(localhost);
        
        montereyNetwork.deployCloudEnvironment(newSimulatorCloudEnvironment());
        montereyNetwork.deployApplication(newDummyMontereyDeploymentDescriptor(), BundleSet.EMPTY);
        
        assertMontereyRunningWithApp(montereyNetwork);
    }

    @Test
    public void testAddingSegmentsCreatesBrooklynEntities() throws Throwable {
        montereyNetwork.startOnHost(localhost);
        montereyNetwork.deployCloudEnvironment(newSimulatorCloudEnvironment());
        montereyNetwork.deployApplication(newDummyMontereyDeploymentDescriptor(), BundleSet.EMPTY);

        Map<String,SegmentSummary> expectedSegments = ImmutableMap.<String,SegmentSummary>builder()
                .put("a", SegmentSummary.Factory.newInstance("a"))
                .put("b", SegmentSummary.Factory.newInstance("b"))
                .build();
        
        PlumberWebProxy plumber = newMontereyPlumber();
        DmnFuture<Object> future = plumber.addSegments(expectedSegments.values());
        future.get(TIMEOUT, TimeUnit.MILLISECONDS);

        assertBrooklynEventuallyHasSegments(expectedSegments);
    }

    @Test
    public void testRolloutNodesCreatesBrooklynEntities() throws Throwable {
        // Start the management plane (with "simulator" embedded network nodes)
        montereyNetwork.startOnHost(localhost);
        montereyNetwork.deployCloudEnvironment(newSimulatorCloudEnvironment());
        
        // Deploy a real app
        BundleSet appBundleSet = BundleSet.fromUrls(Collections.singleton(HELLO_CLOUD_BUNDLE_URL));
        montereyNetwork.deployApplication(newHelloCloudMontereyDeploymentDescriptor(), appBundleSet);

        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        ProvisionerWebProxy provisioner = newMontereyProvisioner();
        PlumberWebProxy plumber = newMontereyPlumber();
        
        // Create 4 nodes, and assert brooklyn entities knows about spares
        MontereyActiveLocation aMontereyLocation = networkInfo.getActiveLocations().iterator().next();
        CloudProviderAccountAndLocationId aMontereLocationId = LocationUtils.toAccountAndLocationId(aMontereyLocation);
        DmnFuture<Collection<NodeId>> provisioningFuture = provisioner.createNodesAt(4, aMontereLocationId);
        Collection<NodeId> provisionedNodes = provisioningFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());
        
        // Rollout a simple 1-1-1-1 network, and assert brooklyn entities know about it 
        DmnFuture<Collection<NodeId>> rolloutingFuture = plumber.rolloutNodes(new NodesRolloutConfiguration.Builder()
                .nodesToUse(provisionedNodes)
                .lpps(1)
                .mrs(1)
                .ms(1)
                .tps(1)
                .build());
        
        rolloutingFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());
    }
    
    @Test
    public void testNodesAndSegmentsReportWorkrate() throws Throwable {
        // Create management plane and deploy app
        montereyNetwork.startOnHost(localhost);
        montereyNetwork.deployCloudEnvironment(newSimulatorCloudEnvironment());
        BundleSet appBundleSet = BundleSet.fromUrls(Collections.singleton(HELLO_CLOUD_BUNDLE_URL));
        montereyNetwork.deployApplication(newHelloCloudMontereyDeploymentDescriptor(), appBundleSet);

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
        NodeId mediatorId = findNodesOfType(Dmn1NodeType.M).iterator().next();
        assertBrooklynEventuallyHasExpectedNodeAttributeValues(ImmutableMap.<NodeId, Map<AttributeSensor,Filter>>builder()
                .put(mediatorId, ImmutableMap.<AttributeSensor,Filter>builder()
                        .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.equal(0d))
                        .build())
                .build());
        
        // Apply load, and assert workrate goes up
        // TODO Can I use DmnAssertionUtils?
        // It takes a ManagementNode, and it currently doesn't do workload for no-api
        final double desiredWorkratePerSecond = 10;
        final HelloCloudServiceLocator serviceLocator = newHelloCloudServiceLocator();
        Thread t = new Thread() {
            public void run() {
                int i = 0;
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        serviceLocator.getService("a").hello(""+(i++));
                        Thread.sleep(1000/(int)desiredWorkratePerSecond);
                    }
                } catch (InterruptedException e) {
                    LOG.info("Interrupted loader-thread; terminating thread gracefully");
                    Thread.currentThread().interrupt();
                }
            }
        };
        try {
            t.start();
            
            assertBrooklynEventuallyHasExpectedNodeAttributeValues(ImmutableMap.<NodeId, Map<AttributeSensor,Filter>>builder()
                    .put(mediatorId, ImmutableMap.<AttributeSensor,Filter>builder()
                            .put(MediatorNode.WORKRATE_MSGS_PER_SEC, Filters.between(0d, desiredWorkratePerSecond))
                            .build())
                    .build());
            
        } finally {
            t.interrupt();
        }
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
                .build();
    }
    
    private Dmn1NetworkInfoWebProxy newMontereyNetworkInfo() {
        return new Dmn1NetworkInfoWebProxy(montereyNetwork.getManagementUrl(), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
    }
    
    private PlumberWebProxy newMontereyPlumber() {
        return new PlumberWebProxy(montereyNetwork.getManagementUrl(), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
    }
    
    private ProvisionerWebProxy newMontereyProvisioner() {
        return new ProvisionerWebProxy(montereyNetwork.getManagementUrl(), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
    }
    
    private HelloCloudServiceLocator newHelloCloudServiceLocator() throws Exception {
        MontereyNetworkEndpointImpl networkEndpoint = new MontereyNetworkEndpointImpl();
        networkEndpoint.setManagementNodeUrl(montereyNetwork.getManagementUrl());
        networkEndpoint.setLocation("GB-EDH");
        networkEndpoint.setUsername("myname");
        networkEndpoint.setPassword("mypass");
        networkEndpoint.setHasPrivateIp(true);
        networkEndpoint.start();
        
        return new HelloCloudServiceLocatorImpl(networkEndpoint);
    }
    
    private Collection<NodeId> findNodesOfType(Dmn1NodeType type) {
        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        Collection<NodeId> result = new ArrayList<NodeId>();
        for (NodeSummary contender : networkInfo.getNodeSummaries().values()) {
            if (contender.getType() == type) {
                result.add(contender.getNodeId());
            }
        }
        return result;
    }
    
    private void assertMontereyRunningWithApp(MontereyNetwork montereyNetwork) {
        DeploymentWebProxy deploymentWebProxy = new DeploymentWebProxy(montereyNetwork.getManagementUrl(), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
        Assert.assertTrue(deploymentWebProxy.isApplicationUndeployable());
    }
    
    private void assertBrooklynEventuallyHasNodes(final Map<NodeId,NodeSummary> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<NodeId, MontereyContainerNode> actual = montereyNetwork.getContainerNodes();
                Assert.assertEquals(expected.keySet(), actual.keySet());
                for (Map.Entry<NodeId,NodeSummary> e : expected.entrySet()) {
                    NodeSummary expectedNodeSummary = e.getValue();
                    MontereyContainerNode actualContainerNode = actual.get(e.getKey());
                    
                    Assert.assertEquals(expectedNodeSummary.getNodeId(), actualContainerNode.getNodeId());
                    
                    AbstractMontereyNode montereyNetworkNode = actualContainerNode.getContainedMontereyNode();
                    Assert.assertNotNull(montereyNetworkNode);
                    Assert.assertEquals(expectedNodeSummary.getType(), montereyNetworkNode.getNodeType());
                }
                return null;
            }}, TIMEOUT);
    }
    
    private void assertBrooklynEventuallyHasSegments(final Map<String,SegmentSummary> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Segment> actual = montereyNetwork.getSegments();
                Assert.assertEquals(expected.keySet(), actual.keySet());
                return null;
            }}, TIMEOUT);
    }
    
    @SuppressWarnings("rawtypes")
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
                LOG.info("Sensor values: "+valsForLogging);
                return null;
            }}, TIMEOUT);
    }
    
    @SuppressWarnings("rawtypes")
    private void assertBrooklynEventuallyHasExpectedSegmentAttributeValues(final Map<String,Map<AttributeSensor,Filter>> expectedSegments) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Segment> actualSegments = montereyNetwork.getSegments();
                
                for (Map.Entry<String,Map<AttributeSensor,Filter>> entry : expectedSegments.entrySet()) {
                    String segmentId = entry.getKey();
                    Segment actualSegment = actualSegments.get(segmentId);
                    assertEntityAttributes(actualSegment, entry.getValue());
                }
                return null;
            }}, TIMEOUT);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<AttributeSensor,Object> assertEntityAttributes(EntityLocal entity, Map<AttributeSensor,Filter> expectations) throws Exception {
        Map<AttributeSensor,Object> result = new LinkedHashMap<AttributeSensor, Object>();
        for (Map.Entry<AttributeSensor,Filter> entry : expectations.entrySet()) {
            AttributeSensor sensor = entry.getKey();
            Filter filter = entry.getValue();
            Object actualVal = entity.getAttribute(sensor);
            Assert.assertTrue("entity="+entity+"; sensor="+sensor+", val="+actualVal, filter.accept(actualVal));
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
}
