/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Cluster;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.connectionpool.exceptions.SchemaDisagreementException;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;

/**
 * Cassandra testing using Astyanax API.
 */
public class AstyanaxSupport {
    private static final Logger log = LoggerFactory.getLogger(AstyanaxSupport.class);

    protected final CassandraNode node;

    public AstyanaxSupport(CassandraNode node) {
        this.node = node;
    }
    
    public AstyanaxContext<Keyspace> getAstyanaxContextForKeyspace(String keyspace) {
        AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
                .forCluster(node.getClusterName())
                .forKeyspace(keyspace)
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setDiscoveryType(NodeDiscoveryType.NONE))
                .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("BrooklynPool")
                        .setPort(node.getThriftPort())
                        .setMaxConnsPerHost(1)
                        .setConnectTimeout(5000) // 10s
                        .setSeeds(String.format("%s:%d", node.getAttribute(Attributes.HOSTNAME), node.getThriftPort())))
                .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                .buildKeyspace(ThriftFamilyFactory.getInstance());

        context.start();
        return context;
    }
    
    public AstyanaxContext<Cluster> getAstyanaxContextForCluster() {
        AstyanaxContext<Cluster> context = new AstyanaxContext.Builder()
                .forCluster(node.getClusterName())
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setDiscoveryType(NodeDiscoveryType.NONE))
                .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("BrooklynPool")
                        .setPort(node.getThriftPort())
                        .setMaxConnsPerHost(1)
                        .setConnectTimeout(5000) // 10s
                        .setSeeds(String.format("%s:%d", node.getAttribute(Attributes.HOSTNAME), node.getThriftPort())))
                .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                .buildCluster(ThriftFamilyFactory.getInstance());

        context.start();
        return context;
    }
    
    public static class AstyanaxSample extends AstyanaxSupport {
        public static final ColumnFamily<String, String> SAMPLE_COLUMN_FAMILY = new ColumnFamily<String, String>(
                "People", // Column Family Name
                StringSerializer.get(), // Key Serializer
                StringSerializer.get()); // Column Serializer

        public AstyanaxSample(CassandraNode node) {
            super(node);
        }

        /**
         * Exercise the {@link CassandraNode} using the Astyanax API.
         */
        public void astyanaxTest() throws Exception {
            writeData();
            readData();
        }

        /**
         * Write to a {@link CassandraNode} using the Astyanax API.
         */
        public void writeData() throws Exception {
            // Create context
            AstyanaxContext<Keyspace> context = getAstyanaxContextForKeyspace("BrooklynIntegrationTest");
            try {
                Keyspace keyspace = context.getEntity();
                try {
                    assertNull(keyspace.describeKeyspace().getColumnFamily("Rabbits"));
                } catch (Exception ek) {
                    // (Re) Create keyspace if needed
                    log.debug("repairing Cassandra error by re-creating keyspace "+keyspace+": "+ek);
                    try {
                        log.debug("dropping Cassandra keyspace "+keyspace);
                        keyspace.dropKeyspace();
                    } catch (Exception e) {
                        /* Ignore */ 
                        log.debug("Cassandra keyspace "+keyspace+" could not be dropped (probably did not exist): "+e);
                    }
                    try {
                        keyspace.createKeyspace(ImmutableMap.<String, Object>builder()
                                .put("strategy_options", ImmutableMap.<String, Object>of("replication_factor", "1"))
                                .put("strategy_class", "SimpleStrategy")
                                .build());
                    } catch (SchemaDisagreementException e) {
                        // discussion (but not terribly helpful) at http://stackoverflow.com/questions/6770894/schemadisagreementexception
                        // let's just try again after a delay
                        log.warn("error creating Cassandra keyspace "+keyspace+" (retrying): "+e);
                        Time.sleep(Duration.FIVE_SECONDS);
                        keyspace.createKeyspace(ImmutableMap.<String, Object>builder()
                                .put("strategy_options", ImmutableMap.<String, Object>of("replication_factor", "1"))
                                .put("strategy_class", "SimpleStrategy")
                                .build());
                    }
                }
                
                assertNull(keyspace.describeKeyspace().getColumnFamily("Rabbits"));
                assertNull(keyspace.describeKeyspace().getColumnFamily("People"));

                // Create column family
                keyspace.createColumnFamily(SAMPLE_COLUMN_FAMILY, null);

                // Insert rows
                MutationBatch m = keyspace.prepareMutationBatch();
                m.withRow(SAMPLE_COLUMN_FAMILY, "one")
                .putColumn("name", "Alice", null)
                .putColumn("company", "Cloudsoft Corp", null);
                m.withRow(SAMPLE_COLUMN_FAMILY, "two")
                .putColumn("name", "Bob", null)
                .putColumn("company", "Cloudsoft Corp", null)
                .putColumn("pet", "Cat", null);

                OperationResult<Void> insert = m.execute();
                assertEquals(insert.getHost().getHostName(), node.getAttribute(Attributes.HOSTNAME));
                assertTrue(insert.getLatency() > 0L);
            } catch (ConnectionException ce) {
                // Error connecting to Cassandra
                throw Throwables.propagate(ce);
            } finally {
                context.shutdown();
            }
        }

        /**
         * Read from a {@link CassandraNode} using the Astyanax API.
         */
        public void readData() throws Exception {
            // Create context
            AstyanaxContext<Keyspace> context = getAstyanaxContextForKeyspace("BrooklynIntegrationTest");
            try {
                Keyspace keyspace = context.getEntity();

                // Query data
                OperationResult<ColumnList<String>> query = keyspace.prepareQuery(SAMPLE_COLUMN_FAMILY)
                        .getKey("one")
                        .execute();
                assertEquals(query.getHost().getHostName(), node.getAttribute(Attributes.HOSTNAME));
                assertTrue(query.getLatency() > 0L);

                ColumnList<String> columns = query.getResult();
                assertEquals(columns.size(), 2);

                // Lookup columns in response by name
                String name = columns.getColumnByName("name").getStringValue();
                assertEquals(name, "Alice");

                // Iterate through the columns
                for (Column<String> c : columns) {
                    assertTrue(ImmutableList.of("name", "company").contains(c.getName()));
                }
            } catch (ConnectionException ce) {
                // Error connecting to Cassandra
                Throwables.propagate(ce);
            } finally {
                context.shutdown();
            }
        }
    }
    
}
