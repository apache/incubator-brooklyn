package brooklyn.entity.proxy.nginx;

import static brooklyn.entity.rebind.RebindTestUtils.serializeRebindAndManage;
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
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.test.TestUtils;
import brooklyn.test.WebAppMonitor;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.internal.TimeExtras;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(NginxRebindIntegrationTest.class);

    static { TimeExtras.init(); }

    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication origApp;
    private TestApplication newApp;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
	private ExecutorService executor;
    
    @BeforeMethod(groups = "Integration")
    public void setUp() {
        warUrl = getClass().getClassLoader().getResource("hello-world.war");

    	localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        origApp = new TestApplication();
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
        	monitor.terminate();
        }
        if (executor != null) executor.shutdownNow();
        if (newApp != null) newApp.stop();
        if (origApp != null) origApp.stop();
    }

    private WebAppMonitor newWebAppMonitor(String url, int expectedResponseCode) {
    	WebAppMonitor monitor = new WebAppMonitor(url)
//    			.delayMillis(0)
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
        DynamicCluster origServerPool = new DynamicCluster(MutableMap.of("factory", new JBoss7ServerFactory(), "initialSize", 0), origApp);
        
        NginxController origNginx = new NginxController(MutableMap.builder()
                .put("owner", origApp)
                .put("serverPool", origServerPool)
                .put("domain", "localhost")
                .build());
        
        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String rootUrl = origNginx.getAttribute(NginxController.ROOT_URL);

        assertHttpStatusCodeEventuallyEquals(rootUrl, 404);
        WebAppMonitor monitor = newWebAppMonitor(rootUrl, 404);
        
        newApp = (TestApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        NginxController newNginx = (NginxController) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(NginxController.class));

        assertEquals(newNginx.getAttribute(NginxController.ROOT_URL), rootUrl);
        assertEquals(newNginx.getAttribute(NginxController.PROXY_HTTP_PORT), origNginx.getAttribute(NginxController.PROXY_HTTP_PORT));
        assertEquals(newNginx.getConfig(NginxController.STICKY), origNginx.getConfig(NginxController.STICKY));
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcessEntity.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(rootUrl, 404);
        
        assertEquals(monitor.getFailures(), 0);
    }
    
    /**
     * Test can rebind to the simplest possible nginx configuration (i.e. no server pool).
     */
    @Test(groups = "Integration")
    public void testRebindsWithoutLosingServerPool() throws Exception {
        
        // Set up nginx with a server pool
        DynamicCluster origServerPool = new DynamicCluster(
                MutableMap.of("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString())), "initialSize", 1), 
                origApp);
        
        final NginxController origNginx = new NginxController(MutableMap.builder()
                .put("owner", origApp)
                .put("domain", "localhost")
                .put("serverPool", origServerPool)
                .build());
        
        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String rootUrl = origNginx.getAttribute(NginxController.ROOT_URL);
        
        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(rootUrl, 200);
        
        // Create a rebinding
        newApp = (TestApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        AbstractManagementContext newManagementContext = newApp.getManagementContext();
        final NginxController newNginx = (NginxController) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(NginxController.class));
        DynamicCluster newServerPool = (DynamicCluster) newManagementContext.getEntity(origServerPool.getId());
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcessEntity.SERVICE_UP, true);
        assertHttpStatusCodeEquals(rootUrl, 200);

        // Check has same config contents as before
        // FIXME Need to be able to assert this immediately!
        TestUtils.executeUntilSucceeds(new Runnable() {
            public void run() {
                assertEquals(newNginx.getConfigFile(), origNginx.getConfigFile());
            }});
        
        // Check that an update doesn't break things
        newNginx.update();

        assertHttpStatusCodeEquals(rootUrl, 200);

        // Resize new cluster, and confirm change takes affect.
        //  - Increase size
        //  - wait for nginx to definitely be updates (TODO nicer way to wait for updated?)
        //  - terminate old servers (through origApp so looks like failure)
        //  - confirm can still route messages
        newServerPool.resize(2);
        
        Thread.sleep(10*1000);
        
        ((JBoss7Server)Iterables.getOnlyElement(origServerPool.getMembers())).stop();

        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);

        // Check that URLs have been constantly reachable
        assertEquals(monitor.getFailures(), 0);
    }
    
    
    /**
     * Test can rebind to the simplest possible nginx configuration (i.e. no server pool).
     */
    @Test(groups = "Integration")
    public void testRebindsWithoutLosingUrlMappings() throws Exception {
        
        // Set up nginx with a url-mapping
        Group origUrlMappingsGroup = new BasicGroup(MutableMap.of("childrenAsMembers", true), origApp);

        DynamicCluster origMappingPool = new DynamicCluster(
                MutableMap.of("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString())), "initialSize", 1), 
                origApp);
        UrlMapping origMapping = new UrlMapping(
                MutableMap.builder()
                        .put("domain", "localhost1")
                        .put("target", origMappingPool)
                        .put("rewrites", ImmutableList.of(new UrlRewriteRule("foo/(.*)", "$1")))
                        .build(),
                origUrlMappingsGroup);

        NginxController origNginx = new NginxController(MutableMap.builder()
                .put("owner", origApp)
                .put("domain", "localhost")
                .put("urlMappings", origUrlMappingsGroup)
                .build());
        
        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String mappingGroupUrl = "http://localhost1:"+origNginx.getAttribute(NginxController.PROXY_HTTP_PORT)+"/foo/";
        
        assertHttpStatusCodeEventuallyEquals(mappingGroupUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(mappingGroupUrl, 200);
        
        // Create a rebinding
        newApp = (TestApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        AbstractManagementContext newManagementContext = newApp.getManagementContext();
        NginxController newNginx = (NginxController) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(NginxController.class));
        DynamicCluster newMappingPool = (DynamicCluster) newManagementContext.getEntity(origMappingPool.getId());
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcessEntity.SERVICE_UP, true);
        assertHttpStatusCodeEquals(mappingGroupUrl, 200);

        // Check has same config contents as before
        assertEquals(newNginx.getConfigFile(), origNginx.getConfigFile());
        
        // Check that an update doesn't break things
        newNginx.update();

        assertHttpStatusCodeEquals(mappingGroupUrl, 200);

        // Resize new cluster, and confirm change takes affect.
        //  - Increase size
        //  - wait for nginx to definitely be updates (TODO nicer way to wait for updated?)
        //  - terminate old servers (through origApp so looks like failure)
        //  - confirm can still route messages
        newMappingPool.resize(2);
        
        Thread.sleep(10*1000);
        
        ((JBoss7Server)Iterables.getOnlyElement(origMappingPool.getMembers())).stop();

        assertHttpStatusCodeEquals(mappingGroupUrl, 200);

        // Check that URLs have been constantly reachable
        assertEquals(monitor.getFailures(), 0);
    }
    

    
    
    
    
    
    
    
    
    
    

//    /**
//     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
//     */
//    @Test(groups = "Integration")
//    public void testCanStartupAndShutdown() {
//        def template = { Map properties -> new JBoss7Server(properties) }
//        URL war = getClass().getClassLoader().getResource("hello-world.war")
//        Preconditions.checkState war != null, "Unable to locate resource $war"
//        
//        cluster = new DynamicCluster(owner:origApp, factory:template, initialSize:1)
//        cluster.setConfig(JavaWebAppService.ROOT_WAR, war.path)
//        
//        nginx = new NginxController([
//	            "owner" : app,
//	            "cluster" : cluster,
//	            "domain" : "localhost",
//	            "portNumberSensor" : WebAppService.HTTP_PORT,
//            ])
//        
//        app.start([ new LocalhostMachineProvisioningLocation() ])
//        
//        // App-servers and nginx has started
//        assertAttributeEventually(cluster, SoftwareProcessEntity.SERVICE_UP, true);
//        cluster.members.each {
//            assertAttributeEventually(it, SoftwareProcessEntity.SERVICE_UP, true);
//        }
//        assertAttributeEventually(nginx, SoftwareProcessEntity.SERVICE_UP, true);
//
//        // URLs reachable        
//        assertUrlStatusCodeEventually(nginx.getAttribute(NginxController.ROOT_URL), 200);
//        cluster.members.each {
//            assertUrlStatusCodeEventually(it.getAttribute(WebAppService.ROOT_URL), 200);
//        }
//
//        app.stop();
//
//        // Services have stopped
//        assertFalse(nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP));
//        assertFalse(cluster.getAttribute(SoftwareProcessEntity.SERVICE_UP));
//        cluster.members.each {
//            assertFalse(it.getAttribute(SoftwareProcessEntity.SERVICE_UP));
//        }
//    }
//    
//    @Test(groups = "Integration")
//    public void testTwoNginxesGetDifferentPorts() {
//        def serverFactory = { throw new UnsupportedOperationException(); }
//        cluster = new DynamicCluster(owner:origApp, factory:serverFactory, initialSize:0)
//        
//        def nginx1 = new NginxController([
//                "owner" : origApp,
//                "cluster" : cluster,
//                "domain" : "localhost",
//                "port" : "14000+"
//            ]);
//        def nginx2 = new NginxController([
//            "owner" : app,
//            "cluster" : cluster,
//            "domain" : "localhost",
//            "port" : "14000+"
//        ])
//
//        app.start([ new LocalhostMachineProvisioningLocation() ])
//
//        String url1 = nginx1.getAttribute(NginxController.ROOT_URL)
//        String url2 = nginx2.getAttribute(NginxController.ROOT_URL)
//
//        assertTrue(url1.contains(":1400"), url1);
//        assertTrue(url2.contains(":1400"), url2);
//        assertNotEquals(url1, url2, "Two nginxs should listen on different ports, not both on "+url1);
//        
//        // Nginx has started
//        assertAttributeEventually(nginx1, SoftwareProcessEntity.SERVICE_UP, true);
//        assertAttributeEventually(nginx2, SoftwareProcessEntity.SERVICE_UP, true);
//
//        // Nginx reachable (returning default 404)
//        assertUrlStatusCodeEventually(url1, 404);
//        assertUrlStatusCodeEventually(url2, 404);
//    }
}
