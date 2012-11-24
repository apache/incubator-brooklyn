package brooklyn.entity.nosql.cassandra;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link CassandraServer} class.
 */
public class CassandraIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(CassandraIntegrationTest.class)

    static { TimeExtras.init() }

    private Application app
    private Location testLocation
    private CassandraServer cassandra

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation()
    }

    @AfterMethod(groups = "Integration")
    public void shutdown() {
        if (cassandra != null && cassandra.getAttribute(Startable.SERVICE_UP)) {
            cassandra.stop();
        }
    }

    /**
     * Test that the server starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        cassandra = new CassandraServer(owner:app);
        Entities.startManagement(app);
        cassandra.start([ testLocation ])
        executeUntilSucceedsWithShutdown(cassandra, timeout:600*TimeUnit.SECONDS) {
            assertTrue cassandra.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse cassandra.getAttribute(Startable.SERVICE_UP)
    }

    /**
    * Test that the broker starts up and sets SERVICE_UP correctly,
    * when a jmx port is supplied
    */
   @Test(groups = "Integration")
   public void canStartupAndShutdownWithCustomJmx() {
       cassandra = new CassandraServer(owner:app, jmxPort: "11099+");
       Entities.startManagement(app);
       app.start([ testLocation ])
       executeUntilSucceedsWithShutdown(cassandra, timeout:600*TimeUnit.SECONDS) {
           assertTrue cassandra.getAttribute(Startable.SERVICE_UP)
       }
       assertFalse cassandra.getAttribute(Startable.SERVICE_UP)
   }

    /**
     * Test that setting the 'queue' property causes a named queue to be created.
     */
    @Test(groups = "Integration")
    public void testConnection() {
        cassandra = new CassandraServer(owner:app);
        Entities.startManagement(app);
        cassandra.start([ testLocation ])
        executeUntilSucceeds {
            assertTrue cassandra.getAttribute(Startable.SERVICE_UP)
        }

        try {
            // Check 
        } finally {
            // Stop
	        cassandra.stop()
        }
    }
}
