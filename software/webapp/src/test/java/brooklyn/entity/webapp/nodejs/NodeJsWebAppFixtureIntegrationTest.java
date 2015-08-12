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
package brooklyn.entity.webapp.nodejs;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.HttpTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for NodeJS.
 * 
 * Only works on Linux (including Ubuntu and CentOS); not on OS X
 */
public class NodeJsWebAppFixtureIntegrationTest {

    // TODO Remove duplication from AbstractWebAppFixtureIntegrationTest. Those tests are geared towards Java-based
    // tests (e.g. deploying WAR), so not extending it.

    // TODO Test deploy and undeploy; see AbstractWebAppFixtureIntegrationTest#testWarDeployAndUndeploy

    // TODO Does not set WebAppService.REQUEST_COUNT, WebAppService.ERROR_COUNT, REQUESTS_PER_SECOND_IN_WINDOW etc
    // See AbstractWebAppFixtureIntegrationTest#testPublishesRequestAndErrorCountMetrics and
    // testPublishesRequestsPerSecondMetric for example tests.
    

    private static final Logger LOG = LoggerFactory.getLogger(NodeJsWebAppFixtureIntegrationTest.class);
    
    // Don't use 8080 since that is commonly used by testing software
    public static final String DEFAULT_HTTP_PORT = "7880+";
    
    public static final String GIT_REPO_URL = "https://github.com/grkvlt/node-hello-world.git";
    public static final String APP_FILE = "app.js";
    public static final String APP_NAME = "node-hello-world";

    // The parent application entity for these tests
    private ManagementContext mgmt;
    private TestApplication app;
    private Location loc;
    private NodeJsWebAppService entity;
    
    public static void main(String ...args) throws Exception {
        NodeJsWebAppFixtureIntegrationTest t = new NodeJsWebAppFixtureIntegrationTest();
        try {
            t.setUp();
            t.testReportsServiceDownWhenKilled();
        } finally {
            t.tearDown();
        }
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        mgmt = app.getManagementContext();
        loc = app.newLocalhostProvisioningLocation();

        entity = app.createAndManageChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure(NodeJsWebAppService.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT))
                .configure("gitRepoUrl", GIT_REPO_URL)
                .configure("appFileName", APP_FILE)
                .configure("appName", APP_NAME));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups = "Integration")
    public void testCanStartAndStop() {
        LOG.info("test=canStartAndStop; entity="+entity+"; app="+entity.getApplication());

        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        Asserts.succeedsEventually(MutableMap.of("timeout", 120*1000), new Runnable() {
            public void run() {
                assertTrue(entity.getAttribute(Startable.SERVICE_UP));
            }});
        
        entity.stop();
        assertFalse(entity.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups = "Integration")
    public void testReportsServiceDownWhenKilled() throws Exception {
        LOG.info("test=testReportsServiceDownWithKilled; entity="+entity+"; app="+entity.getApplication());
        
        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", Duration.minutes(2)), entity, Startable.SERVICE_UP, true);

        // Stop the underlying entity, but without our entity instance being told!
        killEntityBehindBack(entity);
        LOG.info("Killed {} behind mgmt's back, waiting for service up false in mgmt context", entity);
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, false);
        
        LOG.info("success getting service up false in primary mgmt universe");
    }

    /**
     * Stop the given underlying entity, but without our entity instance being told!
     */
    protected void killEntityBehindBack(Entity tokill) throws Exception {
        ((SoftwareProcessDriver)((DriverDependentEntity<?>) Entities.deproxy(tokill)).getDriver()).stop();
        // old method of doing this did some dodgy legacy rebind and failed due to too many dangling refs; above is better in any case
        // but TODO we should have some rebind tests for these!
    }

    @Test(groups = "Integration")
    public void testInitialNamedDeployments() {
        final String urlSubPathToWebApp = APP_NAME;
        final String urlSubPathToPageToQuery = "";
        LOG.info("test=testInitialNamedDeployments; entity="+entity+"; app="+entity.getApplication());
        
        Entities.start(entity.getApplication(), ImmutableList.of(loc));

        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.minutes(1)), new Runnable() {
            public void run() {
                // TODO get this URL from a web-app entity of some kind?
                String url = Urls.mergePaths(entity.getAttribute(WebAppService.ROOT_URL), urlSubPathToWebApp, urlSubPathToPageToQuery);
                HttpTestUtils.assertHttpStatusCodeEquals(url, 200);
            }});
    }
}
