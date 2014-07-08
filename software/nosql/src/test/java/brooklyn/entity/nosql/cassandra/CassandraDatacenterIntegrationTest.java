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

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.cassandra.TokenGenerators.PosNeg63TokenGenerator;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * An integration test of the {@link CassandraDatacenter} entity.
 *
 * Tests that a one node cluster can be started on localhost and data can be written/read, using the Astyanax API.
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

    /**
     * Test that a single node cluster starts up and allows access via the Astyanax API.
     * Only one node because Cassandra can only run one node per VM!
     */
    @Test(groups = "Integration")
    public void testStartAndShutdownClusterSizeOne() throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                .configure("initialSize", 1).configure("tokenShift", 42));
        assertEquals(cluster.getCurrentSize().intValue(), 0);

        app.start(ImmutableList.of(testLocation));
        Entities.dumpInfo(app);
        CassandraNode node = (CassandraNode) Iterables.get(cluster.getMembers(), 0);
        String nodeAddr = checkNotNull(node.getAttribute(CassandraNode.HOSTNAME), "hostname") + ":" + checkNotNull(node.getAttribute(CassandraNode.THRIFT_PORT), "thriftPort");
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.GROUP_SIZE, 1);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.CASSANDRA_CLUSTER_NODES, ImmutableList.of(nodeAddr));

        EntityTestUtils.assertAttributeEqualsEventually(node, Startable.SERVICE_UP, true);
        PosNeg63TokenGenerator tg = new PosNeg63TokenGenerator();
        tg.growingCluster(1);
        EntityTestUtils.assertAttributeEqualsEventually(node, CassandraNode.TOKEN, tg.newToken().add(BigInteger.valueOf(42)));

        // may take some time to be consistent (with new thrift_latency checks on the node,
        // contactability should not be an issue, but consistency still might be)
        for (int i=0; ; i++) {
            boolean open = CassandraDatacenterLiveTest.isSocketOpen(node);
            Boolean consistant = open ? CassandraDatacenterLiveTest.areVersionsConsistent(node) : null;
            Integer numPeers = node.getAttribute(CassandraNode.PEERS);
            String msg = "consistency:  "
                    + (!open ? "unreachable" : consistant==null ? "error" : consistant)+"; "
                    + "peer group sizes: "+numPeers;
            log.info(msg);
            if (open && Boolean.TRUE.equals(consistant) && numPeers==1)
                break;
            if (i == 0) log.warn("NOT yet consistent, waiting");
            if (i >= 120) Assert.fail("Did not become consistent in time: "+msg);
            Time.sleep(Duration.ONE_SECOND);
        }

        EntityTestUtils.assertAttributeEquals(node, CassandraNode.PEERS, 1);

        CassandraDatacenterLiveTest.checkConnectionRepeatedly(2, 5, node, node);
    }
}
