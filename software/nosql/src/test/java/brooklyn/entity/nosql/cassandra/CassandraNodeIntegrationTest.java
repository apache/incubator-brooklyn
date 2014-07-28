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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.cassandra.AstyanaxSupport.AstyanaxSample;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.math.MathPredicates;

import com.google.common.collect.ImmutableList;

/**
 * Cassandra integration tests.
 *
 * Test the operation of the {@link CassandraNode} class.
 */
public class CassandraNodeIntegrationTest extends AbstractCassandraNodeTest {

    /**
     * Test that a node starts and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure("jmxPort", "11099+")
                .configure("rmiRegistryPort", "19001+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);
        Entities.dumpInfo(app);

        cassandra.stop();

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a keyspace and column family can be created and used with Astyanax client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure("jmxPort", "11099+")
                .configure("rmiRegistryPort", "19001+")
                .configure("thriftPort", "9876+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);

        AstyanaxSample astyanax = new AstyanaxSample(cassandra);
        astyanax.astyanaxTest();
    }
    
    /**
     * Cassandra v2 needs Java >= 1.7. If you have java 6 as the defult locally, then you can use
     * something like {@code .configure("shell.env", MutableMap.of("JAVA_HOME", "/Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home"))}
     */
    @Test(groups = "Integration")
    public void testCassandraVersion2() throws Exception {
        String version = "2.0.9";
        String majorMinorVersion = "2.0";
        
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure(CassandraNode.SUGGESTED_VERSION, version)
                .configure(CassandraNode.NUM_TOKENS_PER_NODE, 256)
                .configure("jmxPort", "11099+")
                .configure("rmiRegistryPort", "19001+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);
        Entities.dumpInfo(app);

        AstyanaxSample astyanax = new AstyanaxSample(cassandra);
        astyanax.astyanaxTest();

        assertEquals(cassandra.getMajorMinorVersion(), majorMinorVersion);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(cassandra.getAttribute(CassandraNode.TOKEN));
                assertNotNull(cassandra.getAttribute(CassandraNode.TOKENS));
                assertEquals(cassandra.getAttribute(CassandraNode.TOKENS).size(), 256, "tokens="+cassandra.getAttribute(CassandraNode.TOKENS));
                
                assertEquals(cassandra.getAttribute(CassandraNode.PEERS), (Integer)256);
                assertEquals(cassandra.getAttribute(CassandraNode.LIVE_NODE_COUNT), (Integer)1);
        
                assertTrue(cassandra.getAttribute(CassandraNode.SERVICE_UP_JMX));
                assertNotNull(cassandra.getAttribute(CassandraNode.THRIFT_PORT_LATENCY));
        
                assertNotNull(cassandra.getAttribute(CassandraNode.READ_PENDING));
                assertNotNull(cassandra.getAttribute(CassandraNode.READ_ACTIVE));
                EntityTestUtils.assertAttribute(cassandra, CassandraNode.READ_COMPLETED, MathPredicates.greaterThanOrEqual(1));
                assertNotNull(cassandra.getAttribute(CassandraNode.WRITE_PENDING));
                assertNotNull(cassandra.getAttribute(CassandraNode.WRITE_ACTIVE));
                EntityTestUtils.assertAttribute(cassandra, CassandraNode.WRITE_COMPLETED, MathPredicates.greaterThanOrEqual(1));
                
                assertNotNull(cassandra.getAttribute(CassandraNode.READS_PER_SECOND_LAST));
                assertNotNull(cassandra.getAttribute(CassandraNode.WRITES_PER_SECOND_LAST));
        
                assertNotNull(cassandra.getAttribute(CassandraNode.THRIFT_PORT_LATENCY_IN_WINDOW));
                assertNotNull(cassandra.getAttribute(CassandraNode.READS_PER_SECOND_IN_WINDOW));
                assertNotNull(cassandra.getAttribute(CassandraNode.WRITES_PER_SECOND_IN_WINDOW));
                
                // an example MXBean
                EntityTestUtils.assertAttribute(cassandra, CassandraNode.MAX_HEAP_MEMORY, MathPredicates.greaterThanOrEqual(1));
            }});

        cassandra.stop();

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, false);
    }
}
