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

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.SoftwareProcess
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxying.EntitySpecs
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

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testWhenNoServersReturns404() {
        serverPool = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.FACTORY, { throw new UnsupportedOperationException(); })
                .configure("initialSize", 0));
        
        nginx = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost"));
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        assertAttributeEventually(nginx, SoftwareProcess.SERVICE_UP, true);
        assertUrlStatusCodeEventually(nginx.getAttribute(NginxController.ROOT_URL), 404);
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testCanStartupAndShutdown() {
        serverPool = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpecs.spec(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
	            .configure("portNumberSensor", WebAppService.HTTP_PORT));
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        // App-servers and nginx has started
        assertEventually {        
            serverPool.members.each { 
                assertTrue it.getAttribute(SoftwareProcess.SERVICE_UP);
            }
            assertTrue nginx.getAttribute(SoftwareProcess.SERVICE_UP);
        }

        // URLs reachable        
        assertUrlStatusCodeEventually(nginx.getAttribute(NginxController.ROOT_URL), 200);
        serverPool.members.each {
            assertUrlStatusCodeEventually(it.getAttribute(WebAppService.ROOT_URL), 200);
        }

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcess.SERVICE_UP));
        serverPool.members.each {
            assertFalse(it.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }
    
    /**
     * Test that the Nginx proxy works, serving all domains, if no domain is set
     */
    @Test(groups = "Integration")
    public void testDomainless() {
        serverPool = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpecs.spec(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("portNumberSensor", WebAppService.HTTP_PORT));
        
        app.start([ new LocalhostMachineProvisioningLocation() ])
        
        // App-servers and nginx has started
        assertAttributeEventually(serverPool, SoftwareProcess.SERVICE_UP, true);
        serverPool.members.each {
            assertAttributeEventually(it, SoftwareProcess.SERVICE_UP, true);
        }
        assertAttributeEventually(nginx, SoftwareProcess.SERVICE_UP, true);

        // URLs reachable
        assertUrlStatusCodeEventually(nginx.getAttribute(NginxController.ROOT_URL), 200);
        serverPool.members.each {
            assertUrlStatusCodeEventually(it.getAttribute(WebAppService.ROOT_URL), 200);
        }

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcess.SERVICE_UP));
        serverPool.members.each {
            assertFalse(it.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }
    
    @Test(groups = "Integration")
    public void testTwoNginxesGetDifferentPorts() {
        serverPool = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.FACTORY, { throw new UnsupportedOperationException(); })
                .configure("initialSize", 0));
        
        NginxController nginx1 = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("port", "14000+"));
        
        NginxController nginx2 = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("port", "14000+"));
        
        app.start([ new LocalhostMachineProvisioningLocation() ])

        String url1 = nginx1.getAttribute(NginxController.ROOT_URL)
        String url2 = nginx2.getAttribute(NginxController.ROOT_URL)

        assertTrue(url1.contains(":1400"), url1);
        assertTrue(url2.contains(":1400"), url2);
        assertNotEquals(url1, url2, "Two nginxs should listen on different ports, not both on "+url1);
        
        // Nginx has started
        assertAttributeEventually(nginx1, SoftwareProcess.SERVICE_UP, true);
        assertAttributeEventually(nginx2, SoftwareProcess.SERVICE_UP, true);

        // Nginx reachable (returning default 404)
        assertUrlStatusCodeEventually(url1, 404);
        assertUrlStatusCodeEventually(url2, 404);
    }
    
    /** Test that site access does not fail even while nginx is reloaded */
    // FIXME test disabled -- reload isn't a problem, but #365 is
    @Test(enabled = false, groups = "Integration")
    public void testServiceContinuity() {
        serverPool = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpecs.spec(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", serverPool));
        
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
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcess.SERVICE_UP));
        serverPool.members.each {
            assertFalse(it.getAttribute(SoftwareProcess.SERVICE_UP));
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
        serverPool = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpecs.spec(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", serverPool));
        
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
