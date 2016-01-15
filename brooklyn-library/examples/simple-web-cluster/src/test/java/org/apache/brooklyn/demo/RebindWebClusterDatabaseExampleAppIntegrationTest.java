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
package org.apache.brooklyn.demo;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.mgmt.rebind.RebindOptions;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixture;
import org.apache.brooklyn.enricher.stock.Propagator;
import org.apache.brooklyn.entity.proxy.nginx.NginxController;
import org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.DynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.tomcat.Tomcat8Server;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.WebAppMonitor;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.java.JavaEntityMethods;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.policy.enricher.HttpLatencyDetector;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;


public class RebindWebClusterDatabaseExampleAppIntegrationTest extends RebindTestFixture<StartableApplication> {

    private static final Logger LOG = LoggerFactory.getLogger(RebindWebClusterDatabaseExampleAppIntegrationTest.class);

    private Location origLoc;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
    private ExecutorService executor;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origLoc = origManagementContext.getLocationRegistry().resolve("localhost");
        executor = Executors.newCachedThreadPool();
        webAppMonitors.clear();
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
            monitor.terminate();
        }
        if (executor != null) executor.shutdownNow();
        super.tearDown();
    }
    
    @Override
    protected StartableApplication createApp() {
        StartableApplication result = origManagementContext.getEntityManager().createEntity(EntitySpec.create(StartableApplication.class)
                .impl(WebClusterDatabaseExampleApp.class)
                .configure(DynamicCluster.INITIAL_SIZE, 2));
        return result;
    }
    
    private WebAppMonitor newWebAppMonitor(String url, int expectedResponseCode) {
        WebAppMonitor monitor = new WebAppMonitor(url)
//              .delayMillis(0) FIXME Re-enable to fast polling
                .expectedResponseCode(expectedResponseCode)
                .logFailures(LOG);
        webAppMonitors.add(monitor);
        executor.execute(monitor);
        return monitor;
    }
    
    @Test(groups="Integration")
    public void testRestoresSimpleApp() throws Exception {
        origApp.start(ImmutableList.of(origLoc));
        
        assertAppFunctional(origApp);
        
        String clusterUrl = checkNotNull(origApp.getAttribute(WebClusterDatabaseExampleApp.ROOT_URL), "cluster url");
        WebAppMonitor monitor = newWebAppMonitor(clusterUrl, 200);
        
        newApp = rebind(RebindOptions.create().terminateOrigManagementContext(true));
        assertAppFunctional(newApp);

        // expect no failures during rebind
        monitor.assertNoFailures("hitting nginx url");
        monitor.terminate();
    }
    
    private void assertAppFunctional(StartableApplication app) throws Exception {
        // expect standard config to (still) be set
        assertNotNull(app.getConfig(WebClusterDatabaseExampleApp.WAR_PATH));
        assertEquals(app.getConfig(WebClusterDatabaseExampleApp.USE_HTTPS), Boolean.FALSE);
        assertNotNull(app.getConfig(WebClusterDatabaseExampleApp.DB_SETUP_SQL_URL));

        // expect entities to be there
        MySqlNode mysql = (MySqlNode) Iterables.find(app.getChildren(), Predicates.instanceOf(MySqlNode.class));
        ControlledDynamicWebAppCluster web = (ControlledDynamicWebAppCluster) Iterables.find(app.getChildren(), Predicates.instanceOf(ControlledDynamicWebAppCluster.class));
        final NginxController nginx = (NginxController) Iterables.find(web.getChildren(), Predicates.instanceOf(NginxController.class));
        DynamicWebAppCluster webCluster = (DynamicWebAppCluster) Iterables.find(web.getChildren(), Predicates.instanceOf(DynamicWebAppCluster.class));
        Collection<Entity> appservers = web.getMembers();
        assertEquals(appservers.size(), 2);
        String clusterUrl = checkNotNull(app.getAttribute(WebClusterDatabaseExampleApp.ROOT_URL), "cluster url");
        String dbUrl = checkNotNull(mysql.getAttribute(MySqlNode.DATASTORE_URL), "database url");
        final String expectedJdbcUrl = String.format("jdbc:%s%s?user=%s\\&password=%s", dbUrl, WebClusterDatabaseExampleApp.DB_TABLE, 
                WebClusterDatabaseExampleApp.DB_USERNAME, WebClusterDatabaseExampleApp.DB_PASSWORD);

        // expect web-app to be reachable, and wired up to database
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(clusterUrl, 200);
        for (Entity appserver : appservers) {
            String appserverUrl = checkNotNull(appserver.getAttribute(Tomcat8Server.ROOT_URL), "appserver url of "+appserver);

            HttpTestUtils.assertHttpStatusCodeEventuallyEquals(appserverUrl, 200);
            assertEquals(expectedJdbcUrl, appserver.getConfig(JavaEntityMethods.javaSysProp("brooklyn.example.db.url")), "of "+appserver);
        }

        WebAppMonitor monitor = newWebAppMonitor(clusterUrl, 200);

        // expect auto-scaler policy to be there, and to be functional (e.g. can trigger resize)
        AutoScalerPolicy autoScalerPolicy = (AutoScalerPolicy) Iterables.find(webCluster.policies(), Predicates.instanceOf(AutoScalerPolicy.class));
        
        autoScalerPolicy.config().set(AutoScalerPolicy.MIN_POOL_SIZE, 3);
        EntityTestUtils.assertGroupSizeEqualsEventually(web, 3);
        final Collection<Entity> webMembersAfterGrow = web.getMembers();
        
        for (final Entity appserver : webMembersAfterGrow) {
            Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TWO_MINUTES), new Runnable() {
                @Override public void run() {
                    String appserverUrl = checkNotNull(appserver.getAttribute(Tomcat8Server.ROOT_URL), "appserver url of "+appserver);
                    HttpTestUtils.assertHttpStatusCodeEquals(appserverUrl, 200);
                    assertEquals(expectedJdbcUrl, appserver.getConfig(JavaEntityMethods.javaSysProp("brooklyn.example.db.url")), "of "+appserver);
                    Asserts.assertEqualsIgnoringOrder(nginx.getAttribute(NginxController.SERVER_POOL_TARGETS).keySet(), webMembersAfterGrow);
                }});
        }

        // expect enrichers to be there
        Iterables.find(web.enrichers(), Predicates.instanceOf(HttpLatencyDetector.class));
        Iterable<Enricher> propagatorEnrichers = Iterables.filter(web.enrichers(), Predicates.instanceOf(Propagator.class));
        assertEquals(Iterables.size(propagatorEnrichers), 3, "propagatorEnrichers="+propagatorEnrichers);

        // Check we see evidence of the enrichers having an effect.
        // Relying on WebAppMonitor to stimulate activity.
        EntityTestUtils.assertAttributeEqualsEventually(app, WebClusterDatabaseExampleApp.APPSERVERS_COUNT, 3);
        EntityTestUtils.assertAttributeChangesEventually(web, DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW);
        EntityTestUtils.assertAttributeChangesEventually(app, DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW);
        EntityTestUtils.assertAttributeChangesEventually(web, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT);
        EntityTestUtils.assertAttributeChangesEventually(web, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW);

        // Restore the web-cluster to its original size of 2
        autoScalerPolicy.config().set(AutoScalerPolicy.MIN_POOL_SIZE, 2);
        EntityTestUtils.assertGroupSizeEqualsEventually(web, 2);
        
        final Entity removedAppserver = Iterables.getOnlyElement(Sets.difference(ImmutableSet.copyOf(webMembersAfterGrow), ImmutableSet.copyOf(web.getMembers())));
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertFalse(Entities.isManaged(removedAppserver));
            }});
        
        monitor.assertNoFailures("hitting nginx url");
        monitor.terminate();
    }
}
