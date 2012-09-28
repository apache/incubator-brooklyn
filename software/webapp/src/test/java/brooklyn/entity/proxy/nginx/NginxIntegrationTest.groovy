package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.event.AttributeSensor
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Preconditions

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private NginxController nginx
    private DynamicCluster serverPool

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void shutdown() {
        app?.stop()
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testWhenNoServersReturns404() {
        def serverFactory = { throw new UnsupportedOperationException(); }
        serverPool = new DynamicCluster(owner:app, factory:serverFactory, initialSize:0)
        
        nginx = new NginxController([
                "owner" : app,
                "serverPool" : serverPool,
                "domain" : "localhost"
            ])
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        assertAttributeEventually(nginx, SoftwareProcessEntity.SERVICE_UP, true);
        assertUrlStatusCodeEventually(nginx.getAttribute(NginxController.ROOT_URL), 404);
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testCanStartupAndShutdown() {
        def template = { Map properties -> new JBoss7Server(properties) }
        URL war = getClass().getClassLoader().getResource("hello-world.war")
        Preconditions.checkState war != null, "Unable to locate resource $war"
        
        serverPool = new DynamicCluster(owner:app, factory:template, initialSize:1)
        serverPool.setConfig(JavaWebAppService.ROOT_WAR, war.path)
        
        nginx = new NginxController([
	            "owner" : app,
	            "serverPool" : serverPool,
	            "domain" : "localhost",
	            "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        // App-servers and nginx has started
        assertAttributeEventually(serverPool, SoftwareProcessEntity.SERVICE_UP, true);
        serverPool.members.each {
            assertAttributeEventually(it, SoftwareProcessEntity.SERVICE_UP, true);
        }
        assertAttributeEventually(nginx, SoftwareProcessEntity.SERVICE_UP, true);

        // URLs reachable        
        assertUrlStatusCodeEventually(nginx.getAttribute(NginxController.ROOT_URL), 200);
        serverPool.members.each {
            assertUrlStatusCodeEventually(it.getAttribute(WebAppService.ROOT_URL), 200);
        }

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        serverPool.members.each {
            assertFalse(it.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        }
    }
    
    /**
     * Test that the Nginx proxy works, serving all domains, if no domain is set
     */
    @Test(groups = "Integration")
    public void testDomainless() {
        def template = { Map properties -> new JBoss7Server(properties) }
        URL war = getClass().getClassLoader().getResource("hello-world.war")
        Preconditions.checkState war != null, "Unable to locate resource $war"
        
        serverPool = new DynamicCluster(owner:app, factory:template, initialSize:1)
        serverPool.setConfig(JavaWebAppService.ROOT_WAR, war.path)
        
        nginx = new NginxController([
                "owner" : app,
                "serverPool" : serverPool,
                "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        // App-servers and nginx has started
        assertAttributeEventually(serverPool, SoftwareProcessEntity.SERVICE_UP, true);
        serverPool.members.each {
            assertAttributeEventually(it, SoftwareProcessEntity.SERVICE_UP, true);
        }
        assertAttributeEventually(nginx, SoftwareProcessEntity.SERVICE_UP, true);

        // URLs reachable
        assertUrlStatusCodeEventually(nginx.getAttribute(NginxController.ROOT_URL), 200);
        serverPool.members.each {
            assertUrlStatusCodeEventually(it.getAttribute(WebAppService.ROOT_URL), 200);
        }

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        serverPool.members.each {
            assertFalse(it.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        }
    }
    
    @Test(groups = "Integration")
    public void testTwoNginxesGetDifferentPorts() {
        def serverFactory = { throw new UnsupportedOperationException(); }
        serverPool = new DynamicCluster(owner:app, factory:serverFactory, initialSize:0)
        
        def nginx1 = new NginxController([
                "owner" : app,
                "serverPool" : serverPool,
                "domain" : "localhost",
                "port" : "14000+"
            ]);
        def nginx2 = new NginxController([
            "owner" : app,
            "serverPool" : serverPool,
            "domain" : "localhost",
            "port" : "14000+"
        ])

        app.start([ new LocalhostMachineProvisioningLocation() ])

        String url1 = nginx1.getAttribute(NginxController.ROOT_URL)
        String url2 = nginx2.getAttribute(NginxController.ROOT_URL)

        assertTrue(url1.contains(":1400"), url1);
        assertTrue(url2.contains(":1400"), url2);
        assertNotEquals(url1, url2, "Two nginxs should listen on different ports, not both on "+url1);
        
        // Nginx has started
        assertAttributeEventually(nginx1, SoftwareProcessEntity.SERVICE_UP, true);
        assertAttributeEventually(nginx2, SoftwareProcessEntity.SERVICE_UP, true);

        // Nginx reachable (returning default 404)
        assertUrlStatusCodeEventually(url1, 404);
        assertUrlStatusCodeEventually(url2, 404);
    }
}
