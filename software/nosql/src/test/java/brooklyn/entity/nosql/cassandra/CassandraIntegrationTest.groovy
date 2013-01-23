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
 * Test the operation of the {@link CassandraServer} class.
 */
public class CassandraIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(CassandraIntegrationTest.class)

    static {
        TimeExtras.init()
    }

    protected Application app
    protected Location testLocation
    protected CassandraServer cassandra

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = new TestApplication()
        testLocation = new LocalhostMachineProvisioningLocation()
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (cassandra != null && cassandra.getAttribute(Startable.SERVICE_UP)) {
            cassandra.stop()
        }
        Entities.destroy(app)
    }

    /**
     * Test that the server starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        cassandra = new CassandraServer(parent:app)
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
        cassandra = new CassandraServer(parent:app)
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
        cassandra = new CassandraServer(parent:app, thriftPort:'9876', jmxPort:'11099', rmiServerPort:'9001', clusterName:'TestCluster')
        Entities.startManagement(app)
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceeds {
            assertTrue cassandra.getAttribute(Startable.SERVICE_UP)
        }

        // Create context
        AstyanaxContext<Keyspace> context = getAstyanaxContext(cassandra)
        try {
            // (Re) Create keyspace
            Keyspace keyspace = context.getEntity()
            try {
                keyspace.dropKeyspace()
            } catch (Exception e) { /* Ignore */ }
            keyspace.createKeyspace(ImmutableMap.<String, Object>builder()
                .put("strategy_options", ImmutableMap.<String, Object>of("replication_factor", "1"))
                .put("strategy_class", "SimpleStrategy")
                .build());
            assertNull(keyspace.describeKeyspace().getColumnFamily("Rabbits"))
            assertNull(keyspace.describeKeyspace().getColumnFamily("People"))

            // Create column family
            ColumnFamily<String, String> cf = new ColumnFamily<String, String>(
                    "People", // Column Family Name
                    StringSerializer.get(), // Key Serializer
                    StringSerializer.get()) // Column Serializer
            keyspace.createColumnFamily(cf, null);

            // Insert rows
            MutationBatch m = keyspace.prepareMutationBatch()
            m.withRow(cf, "one")
                    .putColumn("name", "Alice", null)
                    .putColumn("company", "Cloudsoft Corp", null)
            m.withRow(cf, "two")
                    .putColumn("name", "Bob", null)
                    .putColumn("company", "Cloudsoft Corp", null)
                    .putColumn("pet", "Cat", null)

            OperationResult<Void> insert = m.execute()
            assertEquals(insert.host.hostName, cassandra.getAttribute(Attributes.HOSTNAME))
            assertTrue(insert.latency > 0L)

            // Query data
            OperationResult<ColumnList<String>> query = keyspace.prepareQuery(cf)
                    .getKey("one")
                    .execute()
            assertEquals(insert.host.hostName, cassandra.getAttribute(Attributes.HOSTNAME))
            assertTrue(query.latency > 0L)

            ColumnList<String> columns = query.getResult()
            assertEquals(columns.size(), 2)

            // Lookup columns in response by name
            String name = columns.getColumnByName("name").getStringValue()
            assertEquals(name, "Alice")

            // Iterate through the columns
            for (Column<String> c : columns) {
                assertTrue(ImmutableList.of("name", "company").contains(c.getName()))
            }
        } catch (ConnectionException ce) {
            ce.printStackTrace()
            fail("Error connecting to Cassandra")
        } finally {
            context.shutdown()
            cassandra.stop() // Stop
        }
    }

    protected AstyanaxContext<Keyspace> getAstyanaxContext(CassandraServer server) {
        AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
                .forCluster("TestCluster")
                .forKeyspace("BrooklynIntegrationTest")
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setDiscoveryType(NodeDiscoveryType.NONE))
                .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("BrooklynPool")
                        .setPort(server.getThriftPort())
                        .setMaxConnsPerHost(1)
                        .setConnectTimeout(5000) // 10s
                        .setSeeds(String.format("%s:%d", server.getAttribute(Attributes.HOSTNAME), server.getThriftPort())))
                .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                .buildKeyspace(ThriftFamilyFactory.getInstance())

        context.start()
        return context
    }
}
