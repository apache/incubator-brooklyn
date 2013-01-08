package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.BasicGroup
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.HttpTestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.Iterables

/**
 * Test the operation of the {@link NginxController} class, for URL mapped groups (two different pools).
 * 
 * These tests require that /etc/hosts contains some extra entries, such as:
 *     127.0.0.1       localhost localhost1 localhost2 localhost3 localhost4
 */
public class NginxUrlMappingIntegrationTest {
    
    // TODO Make JBoss7Server.deploy wait for the web-app to actually be deployed.
    // That may simplify some of the tests, because we can assert some things immediately rather than in a succeedsEventually.
    
    private static final Logger log = LoggerFactory.getLogger(NginxUrlMappingIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private NginxController nginx
    private DynamicCluster cluster
    private Group urlMappingsGroup;
    
    private URL war;
    private static String WAR_URL = "classpath://hello-world.war";
    
    @BeforeMethod(groups = "Integration")
    public void setup() {
        war = getClass().getClassLoader().getResource("hello-world.war")
        assertNotNull(war, "Unable to locate hello-world.war resource");
        
        app = new TestApplication();
        urlMappingsGroup = new BasicGroup(app, childrenAsMembers:true);
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void shutdown() {
        app?.stop()
        
        // Confirm nginx has stopped
        if (nginx != null) assertFalse(nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP))
    }

    protected void checkExtraLocalhosts() {
        Set failedHosts = []
        ["localhost", "localhost1", "localhost2", "localhost3", "localhost4"].each { String host ->
            try {
                InetAddress i = InetAddress.getByName(host);
                byte[] b = ((Inet4Address)i).getAddress();
                if (b[0]!=127 || b[1]!=0 || b[2]!=0 || b[3]!=1) {
                    log.warn "Failed to resolve "+host+" (test will subsequently fail, but looking for more errors first; see subsequent failure for more info): wrong IP "+Arrays.asList(b)
                    failedHosts += host;
                }
            } catch (Exception e) {
                log.warn "Failed to resolve "+host+" (test will subsequently fail, but looking for more errors first; see subsequent failure for more info): "+e, e
                failedHosts += host;
            }
        }
        if (!failedHosts.isEmpty()) {
            fail("These tests (in "+this+") require special hostnames to map to 127.0.0.1, in /etc/hosts: "+failedHosts);
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingServerNameAndPath() {
        nginx = new NginxController(app, urlMappings:urlMappingsGroup);
        
        //cluster 0 mounted at localhost1 /
        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+")).
            configure(JavaWebAppService.ROOT_WAR, WAR_URL)
        UrlMapping u0 = new UrlMapping(urlMappingsGroup, domain: "localhost1", target: c0);
        
        //cluster 1 at localhost2 /hello-world/
        DynamicCluster c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+")).
            configure(JavaWebAppService.NAMED_WARS, [WAR_URL]);
        UrlMapping u1 = new UrlMapping(urlMappingsGroup, domain: "localhost2", path: '/hello-world($|/.*)', target: c1);

        // cluster 2 at localhost3 /c2/  and mapping /hello/xxx to /hello/new xxx
        DynamicCluster c2 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        UrlMapping u2 = new UrlMapping(urlMappingsGroup, domain: "localhost3", path: '/c2($|/.*)', target: c2).
//            addRewrite('^(.*/|)(hello/)(.*)$', '$1$2new$3');
            // break needed (syntax below) to prevent infinite recursion
            addRewrite(new UrlRewriteRule('(.*/|)(hello/)(.*)', '$1$2new $3').setBreak());

        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        c2.getChildren().each { it.deploy(war.toString(), "c2.war") }
    
        Entities.dumpInfo(app);
        
        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        executeUntilSucceeds {
            //cluster 0
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost1:${port}", "Hello");
                assertUrlHasText("http://localhost1:${port}/", "Hello");
                assertUrlHasText("http://localhost1:${port}/hello/frank", "http://localhost1:${port}/hello/frank");
            }
            //cluster 1
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost2:${port}/hello-world", "Hello");
                assertUrlHasText("http://localhost2:${port}/hello-world/", "Hello");
                assertUrlHasText("http://localhost2:${port}/hello-world/hello/bob", "http://localhost2:${port}/hello-world/hello/bob");
            }
            //cluster 2
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost3:${port}/c2", "Hello");
                assertUrlHasText("http://localhost3:${port}/c2/", "Hello");
                assertUrlHasText("http://localhost3:${port}/c2/hello/joe", "http://localhost3:${port}/c2/hello/new%20joe");
            }
        }
        
        //these should *not* be available
        assertEquals(urlRespondsStatusCode("http://localhost:${port}/"), 404);
        assertEquals(urlRespondsStatusCode("http://localhost1:${port}/hello-world"), 404);
        assertEquals(urlRespondsStatusCode("http://localhost2:${port}/"), 404);
        assertEquals(urlRespondsStatusCode("http://localhost2:${port}/hello-world/notexists"), 404);
        assertEquals(urlRespondsStatusCode("http://localhost3:${port}/"), 404);
        
        //make sure nginx default welcome page isn't displayed
        assertFails { assertUrlHasText(timeout:1, "http://localhost:${port}/", "ginx"); }
        assertFails { assertUrlHasText(timeout:1, "http://localhost2:${port}/", "ginx"); }
        assertFails { assertUrlHasText(timeout:1, "http://localhost3:${port}/", "ginx"); }
    }

    @Test(groups = "Integration")
    public void testUrlMappingRoutesRequestByPathToCorrectGroup() {
        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        DynamicCluster c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        
        nginx = new NginxController([
                "parent" : app,
                "domain" : "localhost",
                "port" : "8000+",
                "portNumberSensor" : WebAppService.HTTP_PORT,
                "urlMappings" : urlMappingsGroup
            ])
        
        UrlMapping u0 = new UrlMapping(urlMappingsGroup, domain: "localhost", path: '/atC0($|/.*)', target: c0);
        UrlMapping u1 = new UrlMapping(urlMappingsGroup, domain: "localhost", path: '/atC1($|/.*)', target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        
        for (Entity child : c0.getChildren()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC0.war")
        }
        for (Entity child : c1.getChildren()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC1.war")
        }

        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        executeUntilSucceeds {
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost:${port}/atC0", "Hello");
                assertUrlHasText("http://localhost:${port}/atC0/", "Hello");
            }
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost:${port}/atC1", "Hello");
                assertUrlHasText("http://localhost:${port}/atC1/", "Hello");
            }
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingRemovedWhenMappingEntityRemoved() {
        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c0.setConfig(JBoss7Server.ROOT_WAR, war.toString())
        
        UrlMapping u0 = new UrlMapping(urlMappingsGroup, domain: "localhost2", target: c0);
        
        nginx = new NginxController([
                "parent" : app,
                "domain" : "localhost",
                "port" : "8000+",
                "portNumberSensor" : WebAppService.HTTP_PORT,
                "urlMappings" : urlMappingsGroup
            ])
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        
        // Wait for deployment to be successful
        assertUrlStatusCodeEventually("http://localhost2:${port}/", 200);
        
        // Now remove mapping; will no longer route requests
        Entities.unmanage(u0);
        assertUrlStatusCodeEventually("http://localhost2:${port}/", 404);
    }
    
    @Test(groups = "Integration")
    public void testWithCoreClusterAndUrlMappedGroup() {
        // TODO Should use different wars, so can confirm content from each cluster
        // TODO Could also assert on: nginx.getConfigFile()
        
        checkExtraLocalhosts();
        
        def coreCluster = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        coreCluster.setConfig(JavaWebAppService.ROOT_WAR, war.path);
                
        nginx = new NginxController([
	            "parent" : app,
	            "cluster" : coreCluster,
	            "domain" : "localhost",
	            "port" : "8000+",
	            "portNumberSensor" : WebAppService.HTTP_PORT,
                "urlMappings" : urlMappingsGroup
            ])
        
        def c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c1.setConfig(JavaWebAppService.NAMED_WARS, [war.path]);
        def u1 = new UrlMapping(urlMappingsGroup, domain: "localhost1", target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        
        // check nginx forwards localhost1 to c1, and localhost to core group 
        executeUntilSucceeds {
            assertUrlHasText("http://localhost1:${port}/hello-world", "Hello");
            assertEquals(urlRespondsStatusCode("http://localhost1:${port}"), 404);
            
            assertUrlHasText("http://localhost:${port}", "Hello");
            assertEquals(urlRespondsStatusCode("http://localhost:${port}/hello-world"), 404);
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingMultipleRewrites() {
        nginx = new NginxController(app, urlMappings:urlMappingsGroup);
    
        //cluster 0 mounted at localhost1 /
        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+")).
            configure(JavaWebAppService.ROOT_WAR, WAR_URL)
        UrlMapping u0 = new UrlMapping(urlMappingsGroup, domain: "localhost1", target: c0);
        u0.addRewrite("/goodbye/al(.*)", '/hello/al$1');
        u0.addRewrite(new UrlRewriteRule('/goodbye(|/.*)$', '/hello$1').setBreak());
        u0.addRewrite("(.*)/hello/al(.*)", '$1/hello/Big Al$2');
        u0.addRewrite('/hello/an(.*)', '/hello/Sir An$1');

        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        
        // Confirm routes requests to the correct cluster
        executeUntilSucceeds {
            // health check
            assertUrlHasText("http://localhost1:${port}", "Hello");
            assertUrlHasText("http://localhost1:${port}/hello/frank", "http://localhost1:${port}/hello/frank");
            
            // goodbye rewritten to hello
            assertUrlHasText("http://localhost1:${port}/goodbye/frank", "http://localhost1:${port}/hello/frank");
            // hello al rewritten to hello Big Al
            assertUrlHasText("http://localhost1:${port}/hello/aled", "http://localhost1:${port}/hello/Big%20Aled");
            // hello andrew rewritten to hello Sir Andrew
            assertUrlHasText("http://localhost1:${port}/hello/andrew", "http://localhost1:${port}/hello/Sir%20Andrew");
            
            // goodbye alex rewritten to hello Big Alex (two rewrites)
            assertUrlHasText("http://localhost1:${port}/goodbye/alex", "http://localhost1:${port}/hello/Big%20Alex");
            // but goodbye andrew rewritten only to hello Andrew -- test the "break" logic above (won't continue rewriting)
            assertUrlHasText("http://localhost1:${port}/goodbye/andrew", "http://localhost1:${port}/hello/andrew");
            
            // al rewrite can be anywhere
            assertUrlHasText("http://localhost1:${port}/hello/hello/alex", "http://localhost1:${port}/hello/hello/Big%20Alex");
            // but an rewrite must be at beginning
            assertUrlHasText("http://localhost1:${port}/hello/hello/andrew", "http://localhost1:${port}/hello/hello/andrew");
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingGroupRespondsToScaleOut() {
        checkExtraLocalhosts();
        
        nginx = new NginxController([
                "parent" : app,
                "domain" : "localhost",
                "port" : "8000+",
                "portNumberSensor" : WebAppService.HTTP_PORT,
                "urlMappings" : urlMappingsGroup
            ])
        
        def c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c1.setConfig(JavaWebAppService.ROOT_WAR, war.path);
        def u1 = new UrlMapping(urlMappingsGroup, domain: "localhost1", target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        
        Entity c1jboss = Iterables.getOnlyElement(c1.getChildren());
        
        // Wait for app-server to be responsive, and url-mapping to update its TARGET_ADDRESSES (through async subscription)
        executeUntilSucceeds {
            // Entities.dumpInfo(app);
            assertEquals(u1.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), 1);
        }

        // check nginx forwards localhost1 to c1 
        executeUntilSucceeds {
            assertUrlHasText("http://localhost1:${port}", "Hello");
        }
        
        // Resize target cluster of url-mapping
        c1.resize(2);
        List c1jbosses = new ArrayList(c1.getChildren());
        c1jbosses.remove(c1jboss);
        Entity c1jboss2 = Iterables.getOnlyElement(c1jbosses);

        // TODO Have to wait for new app-server; should fix app-servers to block
        // Also wait for TARGET_ADDRESSES to update
        assertAppServerRespondsEventually(c1jboss2);
        executeUntilSucceeds {
            assertEquals(u1.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), 2);
        }

        // check jboss2 is included in nginx rules
        // TODO Should getConfigFile return the current config file, rather than recalculate?
        //      This assertion isn't good enough to tell if it's been deployed.
        String c1jboss2addr = c1jboss2.getAttribute(Attributes.HOSTNAME)+":"+c1jboss2.getAttribute(Attributes.HTTP_PORT);
        assertEventually { 
            String conf = nginx.getConfigFile();
            assertTrue(conf.contains(c1jboss2addr), "could not find "+c1jboss2addr+" in:\n"+conf);
        }
        
        // and check forwarding to c1 by nginx still works
        for (int i = 0; i < 2; i++) {
            assertUrlHasText("http://localhost1:${port}", "Hello");
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingWithEmptyCoreCluster() {
        def serverFactory = { throw new UnsupportedOperationException(); }
        DynamicCluster nullCluster = new DynamicCluster(parent:app, factory:serverFactory, initialSize:0)

        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        
        nginx = new NginxController([
                "parent" : app,
                "cluster" : nullCluster,
                "domain" : "localhost",
                "port" : "8000+",
                "portNumberSensor" : WebAppService.HTTP_PORT,
                "urlMappings" : urlMappingsGroup
            ])
        
        UrlMapping u0 = new UrlMapping(urlMappingsGroup, domain: "localhost", path: '/atC0($|/.*)', target: c0);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        
        for (Entity child : c0.getChildren()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC0.war")
        }

        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        executeUntilSucceeds {
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost:${port}/atC0", "Hello");
                assertUrlHasText("http://localhost:${port}/atC0/", "Hello");
            }
        }

        // And empty-core should return 404        
        assertEquals(urlRespondsStatusCode("http://localhost:${port}"), 404);
    }
    
    @Test(groups = "Integration")
    public void testDiscardUrlMapping() {
        nginx = new NginxController(app, urlMappings:urlMappingsGroup);
        
        //cluster 0 mounted at localhost1 /
        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+")).
            configure(JavaWebAppService.ROOT_WAR, WAR_URL)
        UrlMapping u0 = new UrlMapping(urlMappingsGroup, domain: "localhost1", target: c0);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT)
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals("http://localhost1:${port}", 200);

        // Discard, and confirm that subsequently get a 404 instead
        u0.discard();
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals("http://localhost1:${port}", 404);
    }

    private void assertAppServerRespondsEventually(final JBoss7Server server) {
        String hostname = server.getAttribute(Attributes.HOSTNAME);
        int port = server.getAttribute(Attributes.HTTP_PORT);
        assertUrlStatusCodeEventually("http://"+hostname+":"+port, 200);
    }
}
