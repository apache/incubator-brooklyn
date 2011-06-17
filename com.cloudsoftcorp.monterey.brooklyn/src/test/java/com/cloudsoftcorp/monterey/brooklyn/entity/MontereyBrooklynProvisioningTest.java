package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import com.cloudsoftcorp.util.exception.WorkInProgressException;
import com.cloudsoftcorp.util.javalang.ClassLoadingContext;
import com.cloudsoftcorp.util.osgi.BundleSet;
import com.cloudsoftcorp.util.web.client.CredentialsConfig;
import com.google.gson.Gson;

public class MontereyBrooklynProvisioningTest extends CloudsoftThreadMonitoringTestFixture {

    private static final String SIMULATOR_LOCATIONS_CONF_PATH = "/locations-simulator.conf";

    private static final long TIMEOUT = 30*1000;
    
    private Gson gson;
    private SshMachineLocation localhost;
    private MontereyNetwork montereyNetwork;
    private UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
    
    @Before
    public void setUp() throws Exception {
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
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
        montereyNetwork.stop();
    }
    
    @Test
    public void testStartMontereyManagementNodeAndDeployApp() throws Exception {
        montereyNetwork.startOnHost(localhost);
        
        montereyNetwork.deployCloudEnvironment(newSimulatorCloudEnvironment());
        montereyNetwork.deployApplication(newEmptyMontreyDeploymentDescriptor(), BundleSet.EMPTY);
        
        assertMontereyRunningWithApp(montereyNetwork);
    }

    @Test
    public void testRolloutNodesCreatesBrooklynEntities() throws Exception {
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
    
    private void assertBrooklynEventuallyHasNodes(Map<NodeId,NodeSummary> expected) {
        Collection<MontereyContainerNode> actual = montereyNetwork.getNodes();
        throw new WorkInProgressException();
    }
}
