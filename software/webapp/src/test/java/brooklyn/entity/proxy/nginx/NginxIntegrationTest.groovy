package brooklyn.entity.proxy.nginx;

import static brooklyn.test.HttpTestUtils.*
import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.group.DynamicClusterImpl
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.WebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.HttpTestUtils
import brooklyn.test.WebAppMonitor
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxIntegrationTest.class)

    static final String HELLO_WAR_URL = "classpath://hello-world.war";
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
        if (app != null) Entities.destroyAll(app);
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testWhenNoServersReturns404() {
        def serverFactory = { throw new UnsupportedOperationException(); }
        serverPool = new DynamicClusterImpl(parent:app, factory:serverFactory, initialSize:0)
        
        nginx = new NginxController([
                "parent" : app,
                "serverPool" : serverPool,
                "domain" : "localhost"
            ])
        
        Entities.startManagement(app);
        
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
        
        serverPool = new DynamicClusterImpl(parent:app, factory:template, initialSize:1, war:HELLO_WAR_URL)
        
        nginx = new NginxController([
	            "parent" : app,
	            "serverPool" : serverPool,
	            "domain" : "localhost",
	            "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        Entities.startManagement(app);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        // App-servers and nginx has started
        assertEventually {        
            serverPool.members.each { 
                assertTrue it.getAttribute(SoftwareProcessEntity.SERVICE_UP);
            }
            assertTrue nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP);
        }

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
        
        serverPool = new DynamicClusterImpl(parent:app, factory:template, initialSize:1, war:HELLO_WAR_URL)
        
        nginx = new NginxController([
                "parent" : app,
                "serverPool" : serverPool,
                "portNumberSensor" : WebAppService.HTTP_PORT,
            ])
        
        Entities.startManagement(app);
        
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
        serverPool = new DynamicClusterImpl(parent:app, factory:serverFactory, initialSize:0)
        
        def nginx1 = new NginxController([
                "parent" : app,
                "serverPool" : serverPool,
                "domain" : "localhost",
                "port" : "14000+"
            ]);
        def nginx2 = new NginxController([
            "parent" : app,
            "serverPool" : serverPool,
            "domain" : "localhost",
            "port" : "14000+"
        ])

        Entities.startManagement(app);
        
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
    
    /** Test that site access does not fail even while nginx is reloaded */
    // FIXME test disabled -- reload isn't a problem, but #365 is
    @Test(enabled = false, groups = "Integration")
    public void testServiceContinuity() {
        def template = { Map properties -> new JBoss7Server(properties) }
        
        serverPool = new DynamicClusterImpl(parent:app, factory:template, initialSize:1, war:HELLO_WAR_URL)
        
        nginx = new NginxController(app, serverPool: serverPool);
        
        Entities.startManagement(app);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])

        assertEventually {        
            serverPool.members.each { 
                assertHttpStatusCodeEquals it.getAttribute(WebAppService.ROOT_URL), 200;
            }
            assertHttpStatusCodeEquals nginx.getAttribute(WebAppService.ROOT_URL), 200;
        }

        WebAppMonitor monitor = new WebAppMonitor(nginx.getAttribute(WebAppService.ROOT_URL)).
            logFailures(LOG).
            delayMillis(0);
        Thread t = new Thread(monitor);
        t.start();

        try {
            Thread.sleep(1*1000);
            LOG.info("service continuity test, startup, "+monitor.getAttempts()+" requests made");
            monitor.assertAttemptsMade(10, "startup").assertNoFailures("startup").resetCounts();
            
            for (int i=0; i<20; i++) {
                nginx.reload();
                Thread.sleep(500);
                LOG.info("service continuity test, iteration "+i+", "+monitor.getAttempts()+" requests made");
                monitor.assertAttemptsMade(10, "reloaded").assertNoFailures("reloaded").resetCounts();
            }
            
        } finally {
            t.interrupt();
        }
        
        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        serverPool.members.each {
            assertFalse(it.getAttribute(SoftwareProcessEntity.SERVICE_UP));
        }
    }

    // FIXME test disabled -- issue #365
    /*
     * This currently makes no assertions, but writes out the number of sequential reqs per sec
     * supported with nginx and jboss.
     * <p>
     * jboss is (now) steady, at 6k+, since we close the connections in HttpTestUtils.getHttpStatusCode.
     * but nginx still hits problems, after about 15k reqs, something is getting starved in nginx.
     */
    @Test(enabled=false, groups = "Integration")
    public void testContinuityNginxAndJboss() {
        def template = { Map properties -> new JBoss7Server(properties) }
        
        serverPool = new DynamicClusterImpl(parent:app, factory:template, initialSize:1, war:HELLO_WAR_URL)
        
        nginx = new NginxController(app, serverPool: serverPool);
        
        Entities.startManagement(app);
        
        app.start([ new LocalhostMachineProvisioningLocation() ])

        String nginxUrl = nginx.getAttribute(WebAppService.ROOT_URL);
        String jbossUrl;
        assertEventually {
            serverPool.members.each {
                jbossUrl = it.getAttribute(WebAppService.ROOT_URL);
                assertHttpStatusCodeEquals jbossUrl, 200;
            }
            assertHttpStatusCodeEquals nginxUrl, 200;
        }

        Thread t = new Thread() {
            public void run() {
                long lastReportTime = System.currentTimeMillis();
                int num = 0;
                while (true) {
                    try {
                        num++;
                        int code = HttpTestUtils.getHttpStatusCode(nginxUrl);
                        if (code!=200) LOG.info("NGINX GOT: "+code);
                        else LOG.debug("NGINX GOT: "+code);
                        if (System.currentTimeMillis()>=lastReportTime+1000) {
                            LOG.info("NGINX DID "+num+" requests in last "+(System.currentTimeMillis()-lastReportTime)+"ms");
                            num=0;
                            lastReportTime = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        LOG.info("NGINX GOT: "+e);
                    }
                }
            }
        };
        t.start();
        
        Thread t2 = new Thread() {
            public void run() {
                long lastReportTime = System.currentTimeMillis();
                int num = 0;
        while (true) {
            try {
                num++;
                int code = HttpTestUtils.getHttpStatusCode(jbossUrl);
                if (code!=200) LOG.info("JBOSS GOT: "+code);
                else LOG.debug("JBOSS GOT: "+code);
                if (System.currentTimeMillis()>=1000+lastReportTime) {
                    LOG.info("JBOSS DID "+num+" requests in last "+(System.currentTimeMillis()-lastReportTime)+"ms");
                    num=0;
                    lastReportTime = System.currentTimeMillis();
                }
            } catch (Exception e) {
                LOG.info("JBOSS GOT: "+e);
            }
        }
            }
        };
        t2.start();
        
        t2.join();
    }

}
