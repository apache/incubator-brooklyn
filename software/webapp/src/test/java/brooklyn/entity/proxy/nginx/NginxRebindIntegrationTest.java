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
package brooklyn.entity.proxy.nginx;

import static brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEquals;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals;
import static org.testng.Assert.assertEquals;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.WebAppMonitor;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxRebindIntegrationTest extends RebindTestFixtureWithApp {

    private static final Logger LOG = LoggerFactory.getLogger(NginxRebindIntegrationTest.class);

    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
	private ExecutorService executor;
    
	@Override
	protected boolean useLiveManagementContext() {
	    // For Aled, the test failed without own ~/.brooklyn/brooklyn.properties.
	    // Suspect that was caused by local environment, with custom brooklyn.ssh.config.scriptHeader
	    // to set things like correct Java on path.
	    return true;
	}
	
    @BeforeMethod(groups = "Integration")
    public void setUp() throws Exception {
        super.setUp();
        warUrl = getClass().getClassLoader().getResource("hello-world.war");
    	localhostProvisioningLocation = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
        	monitor.terminate();
        }
        if (executor != null) executor.shutdownNow();
        super.tearDown();
    }

    private WebAppMonitor newWebAppMonitor(String url, int expectedResponseCode) {
    	WebAppMonitor monitor = new WebAppMonitor(url)
//    	        .delayMillis(0) FIXME Re-enable to fast polling
    			.expectedResponseCode(expectedResponseCode)
		    	.logFailures(LOG);
    	webAppMonitors.add(monitor);
    	executor.execute(monitor);
    	return monitor;
    }
    
    /**
     * Test can rebind to the simplest possible nginx configuration (i.e. no server pool).
     */
    @Test(groups = "Integration")
    public void testRebindsWithEmptyServerPool() throws Exception {
    	
        // Set up nginx with a server pool
        DynamicCluster origServerPool = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 0));
        
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", origServerPool)
                .configure("domain", "localhost"));
        
        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String rootUrl = origNginx.getAttribute(NginxController.ROOT_URL);
        int nginxPort = origNginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        assertHttpStatusCodeEventuallyEquals(rootUrl, 404);
        WebAppMonitor monitor = newWebAppMonitor(rootUrl, 404);
        final String origConfigFile = origNginx.getConfigFile();
        
        newApp = rebind(false, true);
        final NginxController newNginx = (NginxController) Iterables.find(newApp.getChildren(), Predicates.instanceOf(NginxController.class));

        assertEquals(newNginx.getConfigFile(), origConfigFile);
        
        EntityTestUtils.assertAttributeEqualsEventually(newNginx, NginxController.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertEquals(newNginx.getAttribute(NginxController.PROXY_HTTP_PORT), (Integer)nginxPort);
        assertEquals(newNginx.getAttribute(NginxController.ROOT_URL), rootUrl);
        assertEquals(newNginx.getAttribute(NginxController.PROXY_HTTP_PORT), origNginx.getAttribute(NginxController.PROXY_HTTP_PORT));
        assertEquals(newNginx.getConfig(NginxController.STICKY), origNginx.getConfig(NginxController.STICKY));
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(rootUrl, 404);
        
        assertEquals(monitor.getFailures(), 0);
    }
    
    /**
     * Test can rebind with an active server pool.
     */
    @Test(groups = "Integration")
    public void testRebindsWithoutLosingServerPool() throws Exception {
        
        // Set up nginx with a server pool
        DynamicCluster origServerPool = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("war", warUrl.toString()))
                .configure("initialSize", 1));
        
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", origServerPool)
                .configure("domain", "localhost"));
        
        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String rootUrl = origNginx.getAttribute(NginxController.ROOT_URL);
        JBoss7Server origJboss = (JBoss7Server) Iterables.getOnlyElement(origServerPool.getMembers());
        assertEquals(origNginx.getAttribute(NginxController.SERVER_POOL_TARGETS).keySet(), ImmutableSet.of(origJboss));
        
        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(rootUrl, 200);
        final String origConfigFile = origNginx.getConfigFile();
        
        // Rebind
        newApp = rebind(false, true);
        ManagementContext newManagementContext = newApp.getManagementContext();
        final NginxController newNginx = (NginxController) Iterables.find(newApp.getChildren(), Predicates.instanceOf(NginxController.class));
        final DynamicCluster newServerPool = (DynamicCluster) newManagementContext.getEntityManager().getEntity(origServerPool.getId());
        final JBoss7Server newJboss = (JBoss7Server) Iterables.getOnlyElement(newServerPool.getMembers());

        // Expect continually to have same nginx members; should not lose them temporarily!
        Asserts.succeedsContinually(new Runnable() {
            public void run() {
                Map<Entity, String> newNginxMemebers = newNginx.getAttribute(NginxController.SERVER_POOL_TARGETS);
                assertEquals(newNginxMemebers.keySet(), ImmutableSet.of(newJboss));
            }});
        
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);

        assertEquals(newNginx.getConfigFile(), origConfigFile);
        
        // Check that an update doesn't break things
        newNginx.update();

        assertHttpStatusCodeEquals(rootUrl, 200);

        // Resize new cluster, and confirm change takes affect.
        //  - Increase size
        //  - wait for nginx to definitely be updates (TODO nicer way to wait for updated?)
        //  - terminate old server
        //  - confirm can still route messages
        newServerPool.resize(2);
        
        Thread.sleep(10*1000);
        
        newJboss.stop();

        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);

        // Check that URLs have been constantly reachable
        assertEquals(monitor.getFailures(), 0);
    }
    
    
    /**
     * Test can rebind to the with server pool and URL remappings.
     * NOTE: This requires a redirection from localhost1 to 127.0.0.1 in your /etc/hosts file
     */
    @Test(groups = "Integration")
    public void testRebindsWithoutLosingUrlMappings() throws Exception {
        
        // Set up nginx with a url-mapping
        Group origUrlMappingsGroup = origApp.createAndManageChild(EntitySpec.create(BasicGroup.class)
                .configure("childrenAsMembers", true));
        
        DynamicCluster origServerPool = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("war", warUrl.toString()))
                .configure("initialSize", 1)); 

        UrlMapping origMapping = origApp.getManagementContext().getEntityManager().createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost1")
                .configure("target", origServerPool)
                .configure("rewrites", ImmutableList.of(new UrlRewriteRule("/foo/(.*)", "/$1")))
                .parent(origUrlMappingsGroup));
        Entities.manage(origMapping);
        
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("domain", "localhost")
                .configure("urlMappings", origUrlMappingsGroup));

        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String mappingGroupUrl = "http://localhost1:"+origNginx.getAttribute(NginxController.PROXY_HTTP_PORT)+"/foo/";

        assertHttpStatusCodeEventuallyEquals(mappingGroupUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(mappingGroupUrl, 200);
        final String origConfigFile = origNginx.getConfigFile();
        
        // Create a rebinding
        newApp = rebind(false, true);
        ManagementContext newManagementContext = newApp.getManagementContext();
        final NginxController newNginx = (NginxController) Iterables.find(newApp.getChildren(), Predicates.instanceOf(NginxController.class));
        DynamicCluster newServerPool = (DynamicCluster) newManagementContext.getEntityManager().getEntity(origServerPool.getId());
        JBoss7Server newJboss = (JBoss7Server) Iterables.getOnlyElement(newServerPool.getMembers());
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(mappingGroupUrl, 200);
        
        assertEquals(newNginx.getConfigFile(), origConfigFile);
        
        // Check that an update doesn't break things
        newNginx.update();

        assertHttpStatusCodeEquals(mappingGroupUrl, 200);

        // Resize new cluster, and confirm change takes affect.
        //  - Increase size
        //  - wait for nginx to definitely be updates (TODO nicer way to wait for updated?)
        //  - terminate old server
        //  - confirm can still route messages
        newServerPool.resize(2);
        
        Thread.sleep(10*1000);
        
        newJboss.stop();

        assertHttpStatusCodeEquals(mappingGroupUrl, 200);

        // Check that URLs have been constantly reachable
        assertEquals(monitor.getFailures(), 0);
    }
}
