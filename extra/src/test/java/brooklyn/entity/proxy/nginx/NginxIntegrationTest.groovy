package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link NginxController} class.
 *
 * TODO clarify test purpose
 */
public class NginxBrokerIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxBrokerIntegrationTest.class)

    static { TimeExtras.init() }

    private Application app
    private Location testLocation
    private NginxController nginx

    static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation(name:'london', count:2)
    }

    @AfterMethod(groups = "Integration")
    public void shutdown() {
        if (nginx != null && nginx.getAttribute(AbstractService.SERVICE_UP)) {
            log.warn "Nginx controller is still running", nginx.id
	        try {
	            nginx.stop()
	        } catch (Exception e) {
	            log.warn "Error caught trying to shut down Nginx: {}", e.message
	        }
        }
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        def template = { properties -> new TomcatServer(properties) }
        DynamicCluster cluster = new DynamicCluster(owner:app, newEntity:template, initialSize:1)
        cluster.start([ new LocalhostMachineProvisioningLocation(count:1) ])
        nginx = new NginxController([
	            "owner" : app,
	            "cluster" : cluster,
	            "domain" : "localhost",
	            "port" : 8000,
	            "portNumberSensor" : TomcatServer.HTTP_PORT,
            ])
        nginx.start([ new LocalhostMachineProvisioningLocation(count:1) ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue cluster.getAttribute(AbstractService.SERVICE_UP)
            assertTrue nginx.getAttribute(AbstractService.SERVICE_UP)
        }, {
            nginx.stop()
            cluster.stop()
        })
        assertFalse nginx.getAttribute(AbstractService.SERVICE_UP)
        assertFalse cluster.getAttribute(AbstractService.SERVICE_UP)
    }
}
