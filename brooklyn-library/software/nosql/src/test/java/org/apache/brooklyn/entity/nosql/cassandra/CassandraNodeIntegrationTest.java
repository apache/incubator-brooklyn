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
package org.apache.brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.nosql.cassandra.CassandraNode;
import org.apache.brooklyn.entity.nosql.cassandra.AstyanaxSupport.AstyanaxSample;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.NetworkingTestUtils;
import org.apache.brooklyn.util.math.MathPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

/**
 * Cassandra integration tests.
 *
 * Test the operation of the {@link CassandraNode} class.
 */
public class CassandraNodeIntegrationTest extends AbstractCassandraNodeTest {

    private static final Logger LOG = LoggerFactory.getLogger(CassandraNodeIntegrationTest.class);

    public static void assertCassandraPortsAvailableEventually() {
        Map<String, Integer> ports = getCassandraDefaultPorts();
        NetworkingTestUtils.assertPortsAvailableEventually(ports);
        LOG.info("Confirmed Cassandra ports are available: "+ports);
    }
    
    public static Map<String, Integer> getCassandraDefaultPorts() {
        List<PortAttributeSensorAndConfigKey> ports = ImmutableList.of(
                CassandraNode.GOSSIP_PORT, 
                CassandraNode.SSL_GOSSIP_PORT, 
                CassandraNode.THRIFT_PORT, 
                CassandraNode.NATIVE_TRANSPORT_PORT, 
                CassandraNode.RMI_REGISTRY_PORT);
        Map<String, Integer> result = Maps.newLinkedHashMap();
        for (PortAttributeSensorAndConfigKey key : ports) {
            result.put(key.getName(), key.getConfigKey().getDefaultValue().iterator().next());
        }
        return result;
    }

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        assertCassandraPortsAvailableEventually();
        super.setUp();
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        assertCassandraPortsAvailableEventually();
    }
    
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
        // TODO In v2.0.10, the bin/cassandra script changed to add an additional check for JMX connectivity.
        // This causes cassandera script to hang for us (presumably due to the CLASSPATH/JVM_OPTS we're passing
        // in, regarding JMX agent).
        // See:
        //  - https://issues.apache.org/jira/browse/CASSANDRA-7254
        //  - https://github.com/apache/cassandra/blame/trunk/bin/cassandra#L211-216
        
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
