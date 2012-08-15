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
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.ResourceUtils
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables

/**
 * Test the operation of the {@link NginxController} class, for URL mapped groups (two different pools).
 */
public class NginxUrlMappingIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxUrlMappingIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private NginxController nginx
    private DynamicCluster cluster

    private URL war;
    
    @BeforeMethod(groups = "Integration")
    public void setup() {
        war = getClass().getClassLoader().getResource("hello-world.war")
        assertNotNull(war, "Unable to locate hello-world.war resource");
        
        app = new TestApplication();
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
        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        DynamicCluster c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
    
        nginx = new NginxController([
                "owner" : app,
                "port" : 8000
            ])
    
        UrlMapping u0 = new UrlMapping(nginx, domain: "localhost1", path: '/atC0($|/.*)', target: c0);
        UrlMapping u1 = new UrlMapping(nginx, domain: "localhost2", path: '/atC1($|/.*)', target: c1);
    
        app.start([ new LocalhostMachineProvisioningLocation() ])
    
        for (Entity child : c0.getOwnedChildren()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC0.war")
        }
        for (Entity child : c1.getOwnedChildren()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC1.war")
        }
    
        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        // FIXME Make JBoss7Server.deploy wait for the web-app to actually be deployed
        executeUntilSucceeds {
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost1:8000/atC0", "Hello");
                assertUrlHasText("http://localhost1:8000/atC0/", "Hello");
            }
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost2:8000/atC1", "Hello");
                assertUrlHasText("http://localhost2:8000/atC1/", "Hello");
            }
        }
        assertFails {
            //this should *not* be available
            assertUrlHasText("http://localhost1:8000/atC1", "Hello");
        }
    }

    @Test(groups = "Integration")
    public void testUrlMappingRoutesRequestByPathToCorrectGroup() {
        def serverFactory = { throw new UnsupportedOperationException(); }
        DynamicCluster nullCluster = new DynamicCluster(owner:app, factory:serverFactory, initialSize:0)

        DynamicCluster c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        DynamicCluster c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        
        nginx = new NginxController([
                "owner" : app,
                "cluster" : nullCluster,
                "domain" : "localhost",
                "port" : 8000,
                "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        UrlMapping u0 = new UrlMapping(nginx, domain: "localhost", path: '/atC0($|/.*)', target: c0);
        UrlMapping u1 = new UrlMapping(nginx, domain: "localhost", path: '/atC1($|/.*)', target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        for (Entity child : c0.getOwnedChildren()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC0.war")
        }
        for (Entity child : c1.getOwnedChildren()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC1.war")
        }

        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        // FIXME Make JBoss7Server.deploy wait for the web-app to actually be deployed
        executeUntilSucceeds {
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost:8000/atC0", "Hello");
                assertUrlHasText("http://localhost:8000/atC0/", "Hello");
            }
            for (int i = 0; i < 2; i++) {
                assertUrlHasText("http://localhost:8000/atC1", "Hello");
                assertUrlHasText("http://localhost:8000/atC1/", "Hello");
            }
        }
    }
    
    @Test(groups = "Integration")
    public void testWithCoreClusterAndUrlMappedGroup() {
        checkExtraLocalhosts();
        
        def c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c0.setConfig(JavaWebAppService.ROOT_WAR, war.path);
                
        nginx = new NginxController([
	            "owner" : app,
	            "cluster" : c0,
	            "domain" : "localhost",
	            "port" : 8000,
	            "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        def c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c1.setConfig(JavaWebAppService.ROOT_WAR, war.path);
        def u1 = new UrlMapping(nginx, domain: "localhost1", target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        Entity c0jboss = Iterables.getOnlyElement(c0.getOwnedChildren());
        Entity c1jboss = Iterables.getOnlyElement(c1.getOwnedChildren());
        
        // Wait for url-mapping to update its TARGET_ADDRESSES (through async subscription)
        executeUntilSucceeds {
            // Entities.dumpInfo(app);
            String jbossHostname = c1jboss.getAttribute(Attributes.HOSTNAME);
            int jbossPort = c1jboss.getAttribute(Attributes.HTTP_PORT);
            String jbossTarget = jbossHostname+":"+jbossPort;
            assertEquals(ImmutableSet.copyOf(u1.getAttribute(UrlMapping.TARGET_ADDRESSES)), ImmutableSet.of(jbossTarget));
            
            // Wait for web-app in c1's app-server to be available
            // TODO should be able to reply on jboss7 entity for that
            assertUrlHasText("http://"+jbossHostname+":"+jbossPort, "Hello");
            
            // Wait for web-app in core's app-server to be available
            // TODO should be able to reply on jboss7 entity for that
            assertUrlHasText("http://"+c0jboss.getAttribute(Attributes.HOSTNAME)+":"+c0jboss.getAttribute(Attributes.HTTP_PORT), "Hello");
        }

        // TODO could also assert on:
//        nginx.getConfigFile()
        
        // check nginx forwards localhost1 to c1, and localhost to core gorup 
        executeUntilSucceeds {
            assertUrlHasText("http://localhost1:8000", "Hello");
    
            assertUrlHasText("http://localhost:8000", "Hello");
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingGroupRespondsToScaleOut() {
        checkExtraLocalhosts();
        
        def c0 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c0.setConfig(JavaWebAppService.ROOT_WAR, war.path);
                
        nginx = new NginxController([
                "owner" : app,
                "cluster" : c0,
                "domain" : "localhost",
                "port" : 8000,
                "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        def c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c1.setConfig(JavaWebAppService.ROOT_WAR, war.path);
        def u1 = new UrlMapping(nginx, domain: "localhost1", target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        Entity c1jboss = Iterables.getOnlyElement(c1.getOwnedChildren());
        
        // Wait for url-mapping to update its TARGET_ADDRESSES (through async subscription)
        executeUntilSucceeds {
            // Entities.dumpInfo(app);
            assertEquals(u1.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), 1);
            
            // Wait for web-app in c1's app-server to be available
            // TODO should be able to reply on jboss7 entity for that
            String jbossHostname = c1jboss.getAttribute(Attributes.HOSTNAME);
            int jbossPort = c1jboss.getAttribute(Attributes.HTTP_PORT);
            assertUrlHasText("http://"+jbossHostname+":"+jbossPort, "Hello");
        }

        // check nginx forwards localhost1 to c1 
        executeUntilSucceeds {
            assertUrlHasText("http://localhost1:8000", "Hello");
        }
        
        // Resize target cluster of url-mapping
        c1.resize(2);
        List c1jbosses = new ArrayList(c1.getOwnedChildren());
        c1jbosses.remove(c1jboss);
        Entity c1jboss2 = Iterables.getOnlyElement(c1jbosses);

        // Wait for url-mapping to update its TARGET_ADDRESSES (through async subscription)
        executeUntilSucceeds {
            // Entities.dumpInfo(app);
            assertEquals(u1.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), 2);
            
            // Wait for new app-server's web-app to be available
            // TODO should be able to reply on jboss7 entity for that
            String jbossHostname = c1jboss2.getAttribute(Attributes.HOSTNAME);
            int jbossPort = c1jboss2.getAttribute(Attributes.HTTP_PORT);
            assertUrlHasText("http://"+jbossHostname+":"+jbossPort, "Hello");
        }

        // now check jboss2 resolves
        String c1jboss2addr = c1jboss2.getAttribute(Attributes.HOSTNAME)+":"+c1jboss2.getAttribute(Attributes.HTTP_PORT);
        assertEventually( { new ResourceUtils(this).getResourceAsString("http://"+c1jboss2addr); } );
        assertUrlHasText("http://"+c1jboss2addr, "Hello");
        
        // and jboss2 is included in nginx rules
        assertEventually( { 
            if (!nginx.getConfigFile().contains(c1jboss2addr))
                return new BooleanWithMessage(false, "could not find "+c1jboss2addr+" in:\n"+nginx.getConfigFile());
        } );
        
        // and check forwarding to c1 by nginx still works
        for (int i = 0; i < 2; i++) {
            assertUrlHasText("http://localhost1:8000", "Hello");
        }
    }
    
    @Test(groups = "Integration")
    public void startWithUrlMappedGroupButNoCoreCluster() {
        checkExtraLocalhosts();
                        
        nginx = new NginxController([
                "owner" : app,
                "domain" : "localhost",
                "port" : 8000,
            ])
        
        def c1 = new DynamicCluster(app, initialSize:1, factory: new JBoss7ServerFactory(httpPort:"8100+"));
        c1.setConfig(JavaWebAppService.ROOT_WAR, war.path);
        def u1 = new UrlMapping(nginx, domain: "localhost1", target: c1);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])

        Entity c1jboss = Iterables.getOnlyElement(c1.getOwnedChildren());
        
        // Wait for url-mappings to update their TARGET_ADDRESSES, based on target cluster async notifications
        executeUntilSucceeds {
            // Entities.dumpInfo(app);
            String jbossHostname = c1jboss.getAttribute(Attributes.HOSTNAME);
            int jbossPort = c1jboss.getAttribute(Attributes.HTTP_PORT);
            String jbossTarget = jbossHostname+":"+jbossPort;
            assertEquals(ImmutableSet.copyOf(u1.getAttribute(UrlMapping.TARGET_ADDRESSES)), ImmutableSet.of(jbossTarget));
            
            // Wait for web-app in c1's app-server to be available
            // TODO should be able to reply on jboss7 entity for that
            assertUrlHasText("http://"+jbossHostname+":"+jbossPort, "Hello");
        }
        
        executeUntilSucceeds {
            // check localhost1 forwarded (to c1) by nginx
            assertUrlHasText("http://localhost1:8000", "Hello");
        }
        
        // and check core group does _not_ resolve
        assertEquals(urlRespondsStatusCode("http://localhost:8000"), 404)
    }
}
