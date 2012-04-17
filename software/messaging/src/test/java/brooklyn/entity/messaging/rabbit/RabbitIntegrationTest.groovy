/*
 * TODO license
 */
package brooklyn.entity.messaging.rabbit;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link RabbitBroker} class.
 */
public class RabbitIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(RabbitIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private Location testLocation
    private RabbitBroker rabbit

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation()
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app) app.stop()
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        rabbit = new RabbitBroker(owner:app);
        rabbit.start([ testLocation ])
        executeUntilSucceedsWithShutdown(rabbit) {
            assertTrue rabbit.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse rabbit.getAttribute(Startable.SERVICE_UP)
    }

}
