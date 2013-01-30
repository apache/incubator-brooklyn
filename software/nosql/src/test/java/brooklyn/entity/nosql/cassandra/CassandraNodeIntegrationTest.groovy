package brooklyn.entity.nosql.cassandra

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
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.netflix.astyanax.AstyanaxContext
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.MutationBatch
import com.netflix.astyanax.connectionpool.NodeDiscoveryType
import com.netflix.astyanax.connectionpool.OperationResult
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl
import com.netflix.astyanax.model.Column
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.astyanax.thrift.ThriftFamilyFactory

/**
 * Cassandra integration tests.
 *
 * Test the operation of the {@link CassandraNode} class.
 */
public class CassandraNodeIntegrationTest extends AbstractCassandraNodeTest {
    private static final Logger log = LoggerFactory.getLogger(CassandraNodeIntegrationTest.class)

    /**
     * Test that the server starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        cassandra = new CassandraNode(parent:app)
        Entities.startManagement(app)
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceedsWithShutdown(cassandra, timeout:10*TimeUnit.MINUTES) {
            assertTrue cassandra.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse cassandra.getAttribute(Startable.SERVICE_UP)
    }

    /**
     * Test that the server starts up and sets SERVICE_UP correctly,
     * when a jmx port is supplied
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdownWithCustomJmx() {
        cassandra = new CassandraNode(parent:app, jmxPort:'11099+', rmiServerPort:'19001+')
        Entities.startManagement(app)
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceedsWithShutdown(cassandra, timeout:10*TimeUnit.MINUTES) {
            assertTrue cassandra.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse cassandra.getAttribute(Startable.SERVICE_UP)
    }

    /**
     * Test that a keyspace and column family can be created and used with Astyanax client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        cassandra = new CassandraNode(parent:app, thriftPort:'9876+', clusterName:'TestCluster')
        Entities.startManagement(app)
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceeds {
            assertTrue cassandra.getAttribute(Startable.SERVICE_UP)
        }

        astyanaxTest()
    }
}
