/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
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

import brooklyn.entity.Entity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
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
 * A live test of the {@link CassandraCluster} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CassandraNode}
 * can be read from another, using the Astyanax API.
 */
class CassandraClusterLiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(CassandraClusterLiveTest.class)

    static {
        TimeExtras.init()
    }

    // private String provider = "rackspace-cloudservers-uk"
    private String provider = "aws-ec2:eu-west-1"

    protected TestApplication app
    protected Location testLocation
    protected CassandraCluster cluster

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider)
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (cluster != null && cluster.getAttribute(Startable.SERVICE_UP)) {
            cluster.stop()
        }
        Entities.destroy(app)
    }

    /**
     * Test that a two node cluster starts up and allows access via the Astyanax API through both nodes.
     */
    @Test(groups = "Live")
    public void canStartupAndShutdown() {
        cluster = app.createAndManageChild(BasicEntitySpec.newInstance(CassandraCluster.class)
                .configure("initialSize", 2)
                .configure("clusterName", "AmazonCluster"));
        assertEquals cluster.currentSize, 0

        app.start(ImmutableList.of(testLocation))

        executeUntilSucceeds(timeout:10*TimeUnit.MINUTES) {
            assertEquals cluster.currentSize, 2
            cluster.members.each { Entity e ->
                assertTrue e.getAttribute(Startable.SERVICE_UP)
                assertEquals e.getAttribute(CassandraNode.PEERS), 2
            }
        }

        Entities.dumpInfo(app)

        CassandraNode first = Iterables.get(cluster.members, 0)
        CassandraNode second = Iterables.get(cluster.members, 1)

        ColumnFamily<String, String> people = new ColumnFamily<String, String>(
                "People", // Column Family Name
                StringSerializer.get(), // Key Serializer
                StringSerializer.get()) // Column Serializer

        writeData(first, people)
        readData(second, people)
    }

    /**
     * Write to a {@link CassandraNode} using the Astyanax API.
     */
    protected void writeData(CassandraNode cassandra, ColumnFamily cf) throws Exception {
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
        } catch (ConnectionException ce) {
            // Error connecting to Cassandra
            Throwables.propagate(ce)
        } finally {
            context.shutdown()
        }
    }

    /**
     * Read from a {@link CassandraNode} using the Astyanax API.
     */
    protected void readData(CassandraNode cassandra, ColumnFamily cf) throws Exception {
        // Create context
        AstyanaxContext<Keyspace> context = getAstyanaxContext(cassandra)
        try {
            // (Re) Create keyspace
            Keyspace keyspace = context.getEntity()

            // Query data
            OperationResult<ColumnList<String>> query = keyspace.prepareQuery(cf)
                    .getKey("one")
                    .execute()
            assertEquals(query.host.hostName, cassandra.getAttribute(Attributes.HOSTNAME))
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
            // Error connecting to Cassandra
            Throwables.propagate(ce)
        } finally {
            context.shutdown()
        }
    }

    protected AstyanaxContext<Keyspace> getAstyanaxContext(CassandraNode server) {
        AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
                .forCluster(server.getClusterName())
                .forKeyspace("BrooklynIntegrationTest")
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setDiscoveryType(NodeDiscoveryType.NONE))
                .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("BrooklynPool")
                        .setPort(server.getThriftPort())
                        .setMaxConnsPerHost(2)
                        .setConnectTimeout(10000) // 10s
                        .setSeeds(String.format("%s:%d", server.getAttribute(Attributes.HOSTNAME), server.getThriftPort())))
                .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                .buildKeyspace(ThriftFamilyFactory.getInstance())

        context.start()
        return context
    }
}
