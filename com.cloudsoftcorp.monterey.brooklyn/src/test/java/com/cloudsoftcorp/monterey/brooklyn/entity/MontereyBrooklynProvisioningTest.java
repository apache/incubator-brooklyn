package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import brooklyn.location.basic.SshMachineLocation;

import com.cloudsoftcorp.monterey.CloudsoftThreadMonitoringTestFixture;
import com.cloudsoftcorp.monterey.clouds.dto.CloudAccountDto;
import com.cloudsoftcorp.monterey.clouds.dto.CloudEnvironmentDto;
import com.cloudsoftcorp.monterey.clouds.dto.CloudProviderSelectionDto;
import com.cloudsoftcorp.monterey.clouds.dto.ProvisioningConfigDto;
import com.cloudsoftcorp.monterey.clouds.simulator.SimulatorAccountConfig;
import com.cloudsoftcorp.monterey.clouds.simulator.SimulatorProvider;
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation;
import com.cloudsoftcorp.monterey.location.api.MontereyLocation;
import com.cloudsoftcorp.monterey.location.dsl.MontereyLocationsDsl;
import com.cloudsoftcorp.monterey.location.temp.impl.CloudAccountIdImpl;
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
import com.cloudsoftcorp.util.Loggers;
import com.cloudsoftcorp.util.TimeUtils;
import com.cloudsoftcorp.util.condition.Conditions.ConditionWithMessage;
import com.cloudsoftcorp.util.exception.ExceptionUtils;
import com.cloudsoftcorp.util.exception.RuntimeInterruptedException;
import com.cloudsoftcorp.util.javalang.ClassLoadingContext;
import com.cloudsoftcorp.util.javalang.OsgiClassLoadingContextFromBundle;
import com.cloudsoftcorp.util.osgi.BundleSet;
import com.cloudsoftcorp.util.web.client.CredentialsConfig;
import com.google.gson.Gson;

public class MontereyBrooklynProvisioningTest extends CloudsoftThreadMonitoringTestFixture {

    private static final Logger LOG = Loggers.getLogger(MontereyBrooklynProvisioningTest.class);
    
    private static final String SIMULATOR_LOCATIONS_CONF_PATH = "/locations-simulator.conf";

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
        localhost.setName("localhost");
        localhost.setUser("aled");
        localhost.setHost("localhost");

        montereyNetwork = new MontereyNetwork();
        montereyNetwork.setInstallDir("~/monterey-management-node");
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
        montereyNetwork.deployApplication(newEmptyMontreyDeploymentDescriptor(), BundleSet.EMPTY);
        
        assertMontereyRunningWithApp(montereyNetwork);
    }

    @Test
    public void testRolloutNodesCreatesBrooklynEntities() throws Throwable {
        montereyNetwork.startOnHost(localhost);
        montereyNetwork.deployCloudEnvironment(newSimulatorCloudEnvironment());
        montereyNetwork.deployApplication(newEmptyMontreyDeploymentDescriptor(), BundleSet.EMPTY);

        Dmn1NetworkInfoWebProxy networkInfo = newMontereyNetworkInfo();
        ProvisionerWebProxy provisioner = newMontereyProvisioner();
        PlumberWebProxy plumber = newMontereyPlumber();
        
        // Create 4 nodes
        MontereyActiveLocation aMontereyLocation = networkInfo.getActiveLocations().iterator().next();
        CloudProviderAccountAndLocationId aMontereLocationId = LocationUtils.toAccountAndLocationId(aMontereyLocation);
        DmnFuture<Collection<NodeId>> provisioningFuture = provisioner.createNodesAt(4, aMontereLocationId);
        Collection<NodeId> provisionedNodes = provisioningFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

        // Assert brooklyn knows about spare nodes
        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());
        
        // Rollout a simple app
        DmnFuture<Collection<NodeId>> rolloutingFuture = plumber.rolloutNodes(new NodesRolloutConfiguration.Builder()
                .nodesToUse(provisionedNodes)
                .lpps(1)
                .mrs(1)
                .ms(1)
                .tps(1)
                .build());
        
        rolloutingFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

        // Assert brooklyn knows about expected nodes
        assertBrooklynEventuallyHasNodes(networkInfo.getNodeSummaries());
    }
    
    private CloudEnvironmentDto newSimulatorCloudEnvironment() throws Exception {
        SimulatorAccountConfig accountConfig = new SimulatorAccountConfig();
        CloudAccountDto cloudAccountDto = new CloudAccountDto(new CloudAccountIdImpl("mycloudaccid"), SimulatorProvider.EMBEDDED_SIMULATOR_PROVIDER_ID);
        Collection<MontereyLocation> montereyLocations = MontereyLocationsDsl.parse(SimulatorProvider.class.getResource(SIMULATOR_LOCATIONS_CONF_PATH)).values();
        ProvisioningConfigDto provisioningConfigDto = new ProvisioningConfigDto(accountConfig.generateResourceProvisionerConf());
        CloudProviderSelectionDto cloudProviderDto = new CloudProviderSelectionDto(cloudAccountDto, montereyLocations, provisioningConfigDto);
        return new CloudEnvironmentDto("mycloudenvid", Collections.singleton(cloudProviderDto));
    }

    private MontereyDeploymentDescriptor newEmptyMontreyDeploymentDescriptor() {
        return new MontereyDeploymentDescriptor.Builder()
                .appDescriptor(new MontereyApplicationDescriptor.Builder()
                        .name("dummy.appname")
                        .clientGateway("dummy.clientgateway")
                        .segmentService("dummy.segmentservice")
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
    
    private void assertMontereyRunningWithApp(MontereyNetwork montereyNetwork) {
        DeploymentWebProxy deploymentWebProxy = new DeploymentWebProxy(montereyNetwork.getManagementUrl(), gson, new CredentialsConfig("myname", "mypass", HTTP_AUTH.REALM, HTTP_AUTH.METHOD));
        Assert.assertTrue(deploymentWebProxy.isApplicationUndeployable());
    }
    
    private void assertBrooklynEventuallyHasNodes(final Map<NodeId,NodeSummary> expected) throws Throwable {
        assertSuccessWithin(new Callable<Object>() {
            public Object call() throws Exception {
                Map<NodeId, MontereyContainerNode> actual = montereyNetwork.getNodes();
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
