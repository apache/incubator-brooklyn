package brooklyn.entity.nosql.cassandra

import static brooklyn.test.TestUtils.executeUntilSucceeds
import static brooklyn.test.TestUtils.executeUntilSucceedsWithShutdown
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.LocationRegistry
import brooklyn.location.basic.BasicLocationRegistry
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.ImmutableList

class CassandraClusterLiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(CassandraClusterLiveTest.class)

    static {
        TimeExtras.init()
    }

    // private String provider = "rackspace-cloudservers-uk"
    private String provider = "named:test"
    // private String provider = "aws-ec2:eu-west-1"

    protected TestApplication app
    protected Location testLocation
    protected CassandraCluster cluster

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = new TestApplication()
        Entities.startManagement(app)

        LocationRegistry locationRegistry = new BasicLocationRegistry(app.managementContext)
        testLocation = locationRegistry.resolve(provider)
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (cluster != null && cluster.getAttribute(Startable.SERVICE_UP)) {
            cluster.stop()
        }
        Entities.destroy(app)
    }

    /**
     * Test that the cluster starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Live")
    public void canStartupAndShutdown() {
        cluster = new CassandraCluster(parent:app, initialSize:2, clusterName:'Amazon Cluster')
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceeds(cluster) {
            assertTrue cluster.currentSize == 2
            cluster.members.each {
                assertTrue it.getAttribute(Startable.SERVICE_UP)
            }
        }
        // cluster.stop()
        // assertFalse cluster.getAttribute(Startable.SERVICE_UP)
    }
}
