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
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.ClusterFromTemplate
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
        if (nginx != null && nginx.getAttribute(JavaApp.SERVICE_UP)) {
            log.warn "Nginx controller is still running", nginx.id
	        try {
	            nginx.stop()
	        } catch (Exception e) {
	            log.warn "Error caught trying to shut down Nginx: {}", e.message
	        }
        }
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        Entity template = new AbstractEntity() { }
        Cluster cluster = new ClusterFromTemplate(template, initialSize:1) { }
        nginx = new NginxController(owner:app, cluster:cluster, domain:"localhost")
        nginx.start([ testLocation ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue nginx.getAttribute(JavaApp.SERVICE_UP)
        }, {
            nginx.stop()
        })
        assertFalse nginx.getAttribute(JavaApp.SERVICE_UP)
    }
}
