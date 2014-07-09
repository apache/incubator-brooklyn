/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.webapp.jboss;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.net.URL;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;

/**
 * TODO re-write this like WebAppIntegrationTest, rather than being jboss6 specific.
 */
public class Jboss6ServerIntegrationTest {
    
    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400;

    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);

        localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void testJmxmp() throws Exception {
        runTest(UsesJmx.JmxAgentModes.JMXMP);
    }

    @Test(groups = "Integration")
    public void testJmxRmi() throws Exception {
        runTest(UsesJmx.JmxAgentModes.JMX_RMI_CUSTOM_AGENT);
    }
    
    @Test(groups = "Integration")
    public void testJmxAutodetect() throws Exception {
        runTest(UsesJmx.JmxAgentModes.AUTODETECT);
    }
    
    protected void runTest(UsesJmx.JmxAgentModes jmxAgentMode) throws Exception {
        final JBoss6Server server = app.createAndManageChild(EntitySpec.create(JBoss6Server.class)
                .configure(JBoss6Server.PORT_INCREMENT, PORT_INCREMENT)
                .configure(UsesJmx.JMX_AGENT_MODE, jmxAgentMode)
                .configure("war", warUrl.toString()));

        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = "http://"+server.getAttribute(JBoss6Server.HOSTNAME)+":"+server.getAttribute(JBoss6Server.HTTP_PORT)+"/";
        
        assertEquals(server.getAttribute(JBoss6Server.ROOT_URL).toLowerCase(), httpUrl.toLowerCase());
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpUrl, 200);
        HttpTestUtils.assertContentContainsText(httpUrl, "Hello");


        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                // TODO Could test other attributes as well; see jboss7 test
                assertNotNull(server.getAttribute(JBoss6Server.REQUEST_COUNT));
                assertNotNull(server.getAttribute(JBoss6Server.ERROR_COUNT));
                assertNotNull(server.getAttribute(JBoss6Server.TOTAL_PROCESSING_TIME));
            }});
    }
}
