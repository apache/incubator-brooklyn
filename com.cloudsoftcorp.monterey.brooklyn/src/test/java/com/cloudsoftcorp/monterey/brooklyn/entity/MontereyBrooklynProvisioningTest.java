package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import brooklyn.location.basic.SshMachineLocation;

import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig;
import com.cloudsoftcorp.monterey.network.control.plane.web.api.ControlPlaneWebConstants.HTTP_AUTH;

public class MontereyBrooklynProvisioningTest {

    private SshMachineLocation localhost;
    private MontereyNetwork montereyNetwork;
    private UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
    
    @Before
    public void setUp() throws Exception {
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
        
    }
    
    @Test
    public void testSomething() throws Exception {
        montereyNetwork.startOnHost(localhost);
        
        montereyNetwork.deployCloudEnvironment(cloudEnvironmentDto);
    }

}
