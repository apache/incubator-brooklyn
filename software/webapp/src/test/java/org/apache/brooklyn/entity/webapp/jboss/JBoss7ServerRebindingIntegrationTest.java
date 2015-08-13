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
package org.apache.brooklyn.entity.webapp.jboss;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.apache.brooklyn.test.WebAppMonitor;
import org.apache.brooklyn.test.entity.TestApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.rebind.RebindOptions;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class JBoss7ServerRebindingIntegrationTest extends RebindTestFixtureWithApp {
    private static final Logger LOG = LoggerFactory.getLogger(JBoss7ServerRebindingIntegrationTest.class);
    
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication newApp;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
    private ExecutorService executor;
    
    @BeforeMethod(groups = "Integration")
    @Override
    public void setUp() throws Exception {
        super.setUp();
        executor = Executors.newCachedThreadPool();
        localhostProvisioningLocation = (LocalhostMachineProvisioningLocation) origManagementContext.getLocationRegistry().resolve("localhost");
    }

    @Override
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
            monitor.terminate();
        }
        if (executor != null) executor.shutdownNow();
        super.tearDown();
    }

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

    private WebAppMonitor newWebAppMonitor(String url) {
        WebAppMonitor monitor = new WebAppMonitor(url)
//                .delayMillis(0)
                .logFailures(LOG);
        webAppMonitors.add(monitor);
        executor.execute(monitor);
        return monitor;
    }
    
    @Test(groups = "Integration")
    public void testRebindsToRunningServer() throws Exception {
        // Start an app-server, and wait for it to be fully up
        JBoss7Server origServer = origApp.createAndManageChild(EntitySpec.create(JBoss7Server.class)
                    .configure("war", getTestWar()));
        
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(origServer.getAttribute(JBoss7Server.ROOT_URL), 200);
        WebAppMonitor monitor = newWebAppMonitor(origServer.getAttribute(JBoss7Server.ROOT_URL));
        
        // Rebind
        newApp = rebind(RebindOptions.create().terminateOrigManagementContext(true));
        JBoss7Server newServer = (JBoss7Server) Iterables.find(newApp.getChildren(), Predicates.instanceOf(JBoss7Server.class));
        String newRootUrl = newServer.getAttribute(JBoss7Server.ROOT_URL);
        
        assertEquals(newRootUrl, origServer.getAttribute(JBoss7Server.ROOT_URL));
        assertEquals(newServer.getAttribute(JBoss7Server.MANAGEMENT_HTTP_PORT), origServer.getAttribute(JBoss7Server.MANAGEMENT_HTTP_PORT));
        assertEquals(newServer.getAttribute(JBoss7Server.DEPLOYED_WARS), origServer.getAttribute(JBoss7Server.DEPLOYED_WARS));
        
        EntityTestUtils.assertAttributeEqualsEventually(newServer, SoftwareProcess.SERVICE_UP, true);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(newRootUrl, 200);

        // confirm that deploy() effector affects the correct jboss server 
        newServer.deploy(getTestWar(), "myhello.war");
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(newRootUrl+"myhello", 200);
        
        // check we see evidence of the enrichers and sensor-feeds having an effect.
        // Relying on WebAppMonitor to cause these to change.
        EntityTestUtils.assertAttributeChangesEventually(newServer, JBoss7Server.REQUEST_COUNT);
        EntityTestUtils.assertAttributeChangesEventually(newServer, JBoss7Server.REQUESTS_PER_SECOND_IN_WINDOW);
        EntityTestUtils.assertAttributeChangesEventually(newServer, JBoss7Server.REQUESTS_PER_SECOND_IN_WINDOW);
        EntityTestUtils.assertAttributeChangesEventually(newServer, JBoss7Server.PROCESSING_TIME_FRACTION_IN_WINDOW);
        
        assertEquals(monitor.getFailures(), 0);
    }
}
