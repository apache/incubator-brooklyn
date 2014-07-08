/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.cassandra;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

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

    public final String clusterName;
    public final String hostname;
    public final int thriftPort;
    
    public AstyanaxSupport(CassandraNode node) {
        this(node.getClusterName(), node.getAttribute(Attributes.HOSTNAME), node.getThriftPort());
    }
    
    public AstyanaxSupport(String clusterName, String hostname, int thriftPort) {
        this.clusterName = clusterName;
        this.hostname = hostname;
        this.thriftPort = thriftPort;
    }
    
    public AstyanaxContext<Keyspace> getAstyanaxContextForKeyspace(String keyspace) {
        AstyanaxContext<Keyspace> context = new AstyanaxContext.Builder()
                .forCluster(clusterName)
                .forKeyspace(keyspace)
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setDiscoveryType(NodeDiscoveryType.NONE))
                .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("BrooklynPool")
                        .setPort(thriftPort)
                        .setMaxConnsPerHost(1)
                        .setConnectTimeout(5000) // 10s
                        .setSeeds(String.format("%s:%d", hostname, thriftPort)))
                .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                .buildKeyspace(ThriftFamilyFactory.getInstance());

        context.start();
        return context;
    }
    
    public AstyanaxContext<Cluster> getAstyanaxContextForCluster() {
        AstyanaxContext<Cluster> context = new AstyanaxContext.Builder()
                .forCluster(clusterName)
                .withAstyanaxConfiguration(new AstyanaxConfigurationImpl()
                        .setDiscoveryType(NodeDiscoveryType.NONE))
                .withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl("BrooklynPool")
                        .setPort(thriftPort)
                        .setMaxConnsPerHost(1)
                        .setConnectTimeout(5000) // 10s
                        .setSeeds(String.format("%s:%d", hostname, thriftPort)))
                .withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
                .buildCluster(ThriftFamilyFactory.getInstance());

        context.start();
        return context;
    }
    
    public static class AstyanaxSample extends AstyanaxSupport {
        
        public static class Builder {
            protected CassandraNode node;
            protected String clusterName;
            protected String hostname;
            protected Integer thriftPort;
            protected String columnFamilyName = Identifiers.makeRandomId(8);
            
            public Builder node(CassandraNode val) {
                this.node = val;
                clusterName = node.getClusterName();
                hostname = node.getAttribute(Attributes.HOSTNAME);
                thriftPort = node.getThriftPort();
                return this;
            }
            public Builder host(String clusterName, String hostname, int thriftPort) {
                this.clusterName = clusterName;
                this.hostname = hostname;
                this.thriftPort = thriftPort;
                return this;
            }
            public Builder columnFamilyName(String val) {
                this.columnFamilyName = val;
                return this;
            }
            public AstyanaxSample build() {
                return new AstyanaxSample(this);
            }
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public final String columnFamilyName;
        public final ColumnFamily<String, String> sampleColumnFamily;

        public AstyanaxSample(CassandraNode node) {
            this(builder().node(node));
        }

        public AstyanaxSample(String clusterName, String hostname, int thriftPort) {
            this(builder().host(clusterName, hostname, thriftPort));
        }

        protected AstyanaxSample(Builder builder) {
            super(builder.clusterName, builder.hostname, builder.thriftPort);
            columnFamilyName = checkNotNull(builder.columnFamilyName, "columnFamilyName");
            sampleColumnFamily = new ColumnFamily<String, String>(
                    columnFamilyName, // Column Family Name
                    StringSerializer.get(), // Key Serializer
                    StringSerializer.get()); // Column Serializer
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
         * @throws ConnectionException 
         */
        public void writeData() throws ConnectionException {
            // Create context
            AstyanaxContext<Keyspace> context = getAstyanaxContextForKeyspace("BrooklynIntegrationTest");
            try {
                Keyspace keyspace = context.getEntity();
                try {
                    assertNull(keyspace.describeKeyspace().getColumnFamily(columnFamilyName));
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
                        // (seems to have no effect; trying to fix by starting first node before others)
                        log.warn("error creating Cassandra keyspace "+keyspace+" (retrying): "+e);
                        Time.sleep(Duration.FIVE_SECONDS);
                        keyspace.createKeyspace(ImmutableMap.<String, Object>builder()
                                .put("strategy_options", ImmutableMap.<String, Object>of("replication_factor", "1"))
                                .put("strategy_class", "SimpleStrategy")
                                .build());
                    }
                }
                
                assertNull(keyspace.describeKeyspace().getColumnFamily("Rabbits"));
                assertNull(keyspace.describeKeyspace().getColumnFamily(columnFamilyName));

                // Create column family
                keyspace.createColumnFamily(sampleColumnFamily, null);

                // Insert rows
                MutationBatch m = keyspace.prepareMutationBatch();
                m.withRow(sampleColumnFamily, "one")
                .putColumn("name", "Alice", null)
                .putColumn("company", "Cloudsoft Corp", null);
                m.withRow(sampleColumnFamily, "two")
                .putColumn("name", "Bob", null)
                .putColumn("company", "Cloudsoft Corp", null)
                .putColumn("pet", "Cat", null);

                OperationResult<Void> insert = m.execute();
                assertEquals(insert.getHost().getHostName(), hostname);
                assertTrue(insert.getLatency() > 0L);
            } finally {
                context.shutdown();
            }
        }
        
        /**
         * Read from a {@link CassandraNode} using the Astyanax API.
         * @throws ConnectionException 
         */
        public void readData() throws ConnectionException {
            // Create context
            AstyanaxContext<Keyspace> context = getAstyanaxContextForKeyspace("BrooklynIntegrationTest");
            try {
                Keyspace keyspace = context.getEntity();

                // Query data
                OperationResult<ColumnList<String>> query = keyspace.prepareQuery(sampleColumnFamily)
                        .getKey("one")
                        .execute();
                assertEquals(query.getHost().getHostName(), hostname);
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
            } finally {
                context.shutdown();
            }
        }
        

        public void writeData(int numRetries) throws ConnectionException {
            while (true) {
                try {
                    writeData();
                    return;
                } catch (Exception e) {
                    log.warn("Error writing data - num retries = "+numRetries+": "+e, e);
                    if (--numRetries <= 0)
                        throw Exceptions.propagate(e);
                }
            }
        }

        public void readData(int numRetries) throws ConnectionException {
            while (true) {
                try {
                    readData();
                    return;
                } catch (Exception e) {
                    log.warn("Error reading data - num retries = "+numRetries+": "+e, e);
                    if (--numRetries <= 0)
                        throw Exceptions.propagate(e);
                }
            }
        }

    }

    public static void main(String[] args) throws Exception {
        AstyanaxSample support = new AstyanaxSample("ignored", "ec2-79-125-32-2.eu-west-1.compute.amazonaws.com", 9160);
        System.out.println(support.getAstyanaxContextForCluster().getEntity().describeSchemaVersions());
    }
}
