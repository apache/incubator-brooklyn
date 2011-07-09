package brooklyn.entity.jms.qpid;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.JavaApp
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link QpidNode} class.
 *
 * TODO clarify test purpose
 */
public class QpidNodeIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossNodeIntegrationTest)

    static { TimeExtras.init() }

    private Application app
    private Location testLocation
    private QpidNode qpid

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
        if (qpid.getAttribute(JavaApp.NODE_UP)) {
            log.warn "Qpid broker {} is still running", qpid.id
	        try {
	            qpid.stop()
	        } catch (Exception e) {
	            log.warn "Error caught trying to shut down Qpid: {}", e.message
	        }
        }
    }

    /**
     * Test that the broker starts up and sets NODE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        qpid = new QpidNode(owner:app);
        qpid.start([ testLocation ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue qpid.getAttribute(JavaApp.NODE_UP)
        }, {
            qpid.stop()
        })
        assertFalse qpid.getAttribute(JavaApp.NODE_UP)
    }

    /**
     * Test that setting the 'queue' property causes a named queue to be created.
     * 
     * TODO use JMS to verify
     */
    @Test(groups = "Integration")
    public void testCreatingQueues() {
        qpid = new QpidNode(owner:app, queue:"testQueue");
        qpid.start([ testLocation ])
        try {
            executeUntilSucceeds([:], {
                assertTrue qpid.getAttribute(JavaApp.NODE_UP)
            })
            
            // check queue created
            assertFalse qpid.queueNames.isEmpty()
            assertEquals qpid.queueNames.size(), 1
            assertTrue qpid.queueNames.contains("testQueue")
            assertEquals qpid.ownedChildren.size(), 1
        } finally {
	        qpid.stop() // stop broker
        }
    }
}
