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
import static org.testng.Assert.assertTrue;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.cassandra.TokenGenerators.PosNeg63TokenGenerator;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * An integration test of the {@link CassandraDatacenter} entity.
 *
 * Tests that a one node cluster can be started on localhost and data can be written/read, using the Astyanax API.
 * 
 * NOTE: If these tests fail with "Timeout waiting for SERVICE_UP" and "java.lang.IllegalStateException: Unable to contact any seeds!" 
 * or "java.lang.RuntimeException: Unable to gossip with any seeds" appears in the log, it may be that the broadcast_address 
 * (set to InetAddress.getLocalHost().getHostName()) is not resolving to the value specified in listen_address 
 * (InetAddress.getLocalHost().getHostAddress()). You can work round this issue by ensuring that you machine has only one 
 * address, e.g. by disabling wireless if you are also using a wired connection
 */
public class CassandraDatacenterIntegrationTest extends BrooklynAppLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(CassandraDatacenterIntegrationTest.class);

    protected Location testLocation;
    protected CassandraDatacenter cluster;

    @Override
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

    @Test(groups = "Integration")
    public void testStartAndShutdownClusterSizeOne() throws Exception {
        EntitySpec<CassandraDatacenter> spec = EntitySpec.create(CassandraDatacenter.class)
                .configure("initialSize", 1)
                .configure("tokenShift", 42);
        runStartAndShutdownClusterSizeOne(spec, true);
    }
    
    /**
     * Cassandra v2 needs Java >= 1.7. If you have java 6 as the defult locally, then you can use
     * something like {@code .configure("shell.env", MutableMap.of("JAVA_HOME", "/Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home"))}
     */
    @Test(groups = "Integration")
    public void testStartAndShutdownClusterSizeOneCassandraVersion2() throws Exception {
        String version = "2.0.9";
        
        EntitySpec<CassandraDatacenter> spec = EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraNode.SUGGESTED_VERSION, version)
                .configure("initialSize", 1);
        runStartAndShutdownClusterSizeOne(spec, false);
    }
    
    /**
     * Test that a single node cluster starts up and allows access via the Astyanax API.
     * Only one node because Cassandra can only run one node per VM!
     */
    protected void runStartAndShutdownClusterSizeOne(EntitySpec<CassandraDatacenter> datacenterSpec, final boolean assertToken) throws Exception {
        cluster = app.createAndManageChild(datacenterSpec);
        assertEquals(cluster.getCurrentSize().intValue(), 0);

        app.start(ImmutableList.of(testLocation));
        Entities.dumpInfo(app);
        
        final CassandraNode node = (CassandraNode) Iterables.get(cluster.getMembers(), 0);
        String nodeAddr = checkNotNull(node.getAttribute(CassandraNode.HOSTNAME), "hostname") + ":" + checkNotNull(node.getAttribute(CassandraNode.THRIFT_PORT), "thriftPort");
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.GROUP_SIZE, 1);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.CASSANDRA_CLUSTER_NODES, ImmutableList.of(nodeAddr));

        EntityTestUtils.assertAttributeEqualsEventually(node, Startable.SERVICE_UP, true);
        if (assertToken) {
            PosNeg63TokenGenerator tg = new PosNeg63TokenGenerator();
            tg.growingCluster(1);
            EntityTestUtils.assertAttributeEqualsEventually(node, CassandraNode.TOKEN, tg.newToken().add(BigInteger.valueOf(42)));
        }

        // may take some time to be consistent (with new thrift_latency checks on the node,
        // contactability should not be an issue, but consistency still might be)
        Asserts.succeedsEventually(MutableMap.of("timeout", 120*1000), new Runnable() {
            public void run() {
                boolean open = CassandraDatacenterLiveTest.isSocketOpen(node);
                Boolean consistant = open ? CassandraDatacenterLiveTest.areVersionsConsistent(node) : null;
                Integer numPeers = node.getAttribute(CassandraNode.PEERS);
                Integer liveNodeCount = node.getAttribute(CassandraNode.LIVE_NODE_COUNT);
                String msg = "consistency:  "
                        + (!open ? "unreachable" : consistant==null ? "error" : consistant)+"; "
                        + "peer group sizes: "+numPeers + "; live node count: " + liveNodeCount;
                assertTrue(open, msg);
                assertEquals(consistant, Boolean.TRUE, msg);
                if (assertToken) {
                    assertEquals(numPeers, (Integer)1, msg);
                } else {
                    assertTrue(numPeers != null && numPeers >= 1, msg);
                }
                assertEquals(liveNodeCount, (Integer)1, msg);
            }});
        
        CassandraDatacenterLiveTest.checkConnectionRepeatedly(2, 5, ImmutableList.of(node));
    }
}
