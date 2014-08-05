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

import java.math.BigInteger;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.cassandra.AstyanaxSupport.AstyanaxSample;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Cluster;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

/**
 * A live test of the {@link CassandraDatacenter} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CassandraNode}
 * can be read from another, using the Astyanax API.
 */
public class CassandraDatacenterLiveTest extends BrooklynAppLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(CassandraDatacenterLiveTest.class);
    
    private String provider = 
            "aws-ec2:eu-west-1";
//            "rackspace-cloudservers-uk";
//            "named:hpcloud-compute-at";
//            "localhost";
//            "jcloudsByon:(provider=\"aws-ec2\",region=\"us-east-1\",user=\"aled\",hosts=\"i-6f374743,i-35324219,i-1135453d\")";

    protected Location testLocation;
    protected CassandraDatacenter cluster;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testLocation = mgmt.getLocationRegistry().resolve(provider);
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }
    
    @Test(groups = "Live")
    public void testDatacenter() throws Exception {
        EntitySpec<CassandraDatacenter> spec = EntitySpec.create(CassandraDatacenter.class)
                .configure("initialSize", 2)
                .configure("clusterName", "CassandraClusterLiveTest");
        runCluster(spec, false);
    }
    
    @Test(groups = "Live")
    public void testDatacenterWithVnodes() throws Exception {
        EntitySpec<CassandraDatacenter> spec = EntitySpec.create(CassandraDatacenter.class)
                .configure("initialSize", 2)
                .configure(CassandraDatacenter.USE_VNODES, true)
                .configure("clusterName", "CassandraClusterLiveTest");
        runCluster(spec, true);
    }
    
    /*
     * TODO on some distros (e.g. CentOS?), it comes pre-installed with java 6. Installing java 7 
     * didn't seem to be enough. I also had to set JAVA_HOME:
     *     .configure("shell.env", MutableMap.of("JAVA_HOME", "/etc/alternatives/java_sdk_1.7.0"))
     * However, that would break other deployments such as on Ubuntu where JAVA_HOME would be different.
     */
    @Test(groups = "Live")
    public void testDatacenterWithVnodesVersion2() throws Exception {
        EntitySpec<CassandraDatacenter> spec = EntitySpec.create(CassandraDatacenter.class)
                .configure("initialSize", 2)
                .configure(CassandraNode.SUGGESTED_VERSION, "2.0.9")
                .configure(CassandraDatacenter.USE_VNODES, true)
                .configure("clusterName", "CassandraClusterLiveTest");
        runCluster(spec, true);
    }

    @Test(groups = {"Live", "Acceptance"}, invocationCount=10)
    public void testManyTimes() throws Exception {
        testDatacenter();
    }

    /**
     * Test a Cassandra Datacenter:
     * <ol>
     *   <li>Create two node datacenter
     *   <li>Confirm allows access via the Astyanax API through both nodes.
     *   <li>Confirm can size
     * </ol>
     */
    protected void runCluster(EntitySpec<CassandraDatacenter> datacenterSpec, boolean usesVnodes) throws Exception {
        cluster = app.createAndManageChild(datacenterSpec);
        assertEquals(cluster.getCurrentSize().intValue(), 0);

        app.start(ImmutableList.of(testLocation));

        // Check cluster is up and healthy
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.GROUP_SIZE, 2);
        Entities.dumpInfo(app);
        List<CassandraNode> members = castToCassandraNodes(cluster.getMembers());
        assertNodesConsistent(members);

        if (usesVnodes) {
            assertVnodeTokensConsistent(members);
        } else {
            assertSingleTokenConsistent(members);
        }
        
        // Can connect via Astyanax
        checkConnectionRepeatedly(2, 5, members);

        // Resize
        cluster.resize(3);
        assertEquals(cluster.getMembers().size(), 3, "members="+cluster.getMembers());
        if (usesVnodes) {
            assertVnodeTokensConsistent(castToCassandraNodes(cluster.getMembers()));
        } else {
            assertSingleTokenConsistent(castToCassandraNodes(cluster.getMembers()));
        }
        checkConnectionRepeatedly(2, 5, cluster.getMembers());
    }

    protected static List<CassandraNode> castToCassandraNodes(Collection<? extends Entity> rawnodes) {
        final List<CassandraNode> nodes = Lists.newArrayList();
        for (Entity node : rawnodes) {
            nodes.add((CassandraNode) node);
        }
        return nodes;
    }

    protected static void assertNodesConsistent(final List<CassandraNode> nodes) {
        final Integer expectedLiveNodeCount = nodes.size();
        // may take some time to be consistent (with new thrift_latency checks on the node,
        // contactability should not be an issue, but consistency still might be)
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TWO_MINUTES), new Runnable() {
            public void run() {
                for (Entity n : nodes) {
                    CassandraNode node = (CassandraNode) n;
                    EntityTestUtils.assertAttributeEquals(node, Startable.SERVICE_UP, true);
                    String errmsg = "node="+node+"; hostname="+node.getAttribute(Attributes.HOSTNAME)+"; port="+node.getThriftPort();
                    assertTrue(isSocketOpen(node), errmsg);
                    assertTrue(areVersionsConsistent(node), errmsg);
                    EntityTestUtils.assertAttributeEquals(node, CassandraNode.LIVE_NODE_COUNT, expectedLiveNodeCount);
                }
            }});
    }
    
    protected static void assertSingleTokenConsistent(final List<CassandraNode> nodes) {
        final int numNodes = nodes.size();
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TWO_MINUTES), new Runnable() {
            public void run() {
                Set<BigInteger> alltokens = Sets.newLinkedHashSet();
                for (Entity node : nodes) {
                    EntityTestUtils.assertAttributeEquals(node, Startable.SERVICE_UP, true);
                    EntityTestUtils.assertConfigEquals(node, CassandraNode.NUM_TOKENS_PER_NODE, 1);
                    EntityTestUtils.assertAttributeEquals(node, CassandraNode.PEERS, numNodes);
                    BigInteger token = node.getAttribute(CassandraNode.TOKEN);
                    Set<BigInteger> tokens = node.getAttribute(CassandraNode.TOKENS);
                    assertNotNull(token);
                    assertEquals(tokens, ImmutableSet.of(token));
                    alltokens.addAll(tokens);
                }
                assertEquals(alltokens.size(), numNodes);
            }});
    }

    protected static void assertVnodeTokensConsistent(final List<CassandraNode> nodes) {
        final int numNodes = nodes.size();
        final int tokensPerNode = Iterables.get(nodes, 0).getNumTokensPerNode();
        
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TWO_MINUTES), new Runnable() {
            public void run() {
                Set<BigInteger> alltokens = Sets.newLinkedHashSet();
                for (Entity node : nodes) {
                    EntityTestUtils.assertAttributeEquals(node, Startable.SERVICE_UP, true);
                    EntityTestUtils.assertAttributeEquals(node, CassandraNode.PEERS, tokensPerNode*numNodes);
                    EntityTestUtils.assertConfigEquals(node, CassandraNode.NUM_TOKENS_PER_NODE, 256);
                    BigInteger token = node.getAttribute(CassandraNode.TOKEN);
                    Set<BigInteger> tokens = node.getAttribute(CassandraNode.TOKENS);
                    assertNotNull(token);
                    assertEquals(tokens.size(), tokensPerNode, "tokens="+tokens);
                    alltokens.addAll(tokens);
                }
                assertEquals(alltokens.size(), tokensPerNode*numNodes);
            }});
    }

    protected static void checkConnectionRepeatedly(int totalAttemptsAllowed, int numRetriesPerAttempt, Iterable<? extends Entity> nodes) throws Exception {
        int attemptNum = 0;
        while (true) {
            try {
                checkConnection(numRetriesPerAttempt, nodes);
                return;
            } catch (Exception e) {
                attemptNum++;
                if (attemptNum >= totalAttemptsAllowed) {
                    log.warn("Cassandra not usable, "+attemptNum+" attempts; failing: "+e, e);
                    throw e;                
                }
                log.warn("Cassandra not usable (attempt "+attemptNum+" of "+totalAttemptsAllowed+"), trying again after delay: "+e, e);
                Time.sleep(Duration.TEN_SECONDS);
            }
        }
    }

    protected static void checkConnection(int numRetries, Iterable<? extends Entity> nodes) throws ConnectionException {
        CassandraNode first = (CassandraNode) Iterables.get(nodes, 0);
        
        // have been seeing intermittent SchemaDisagreementException errors on AWS, probably due to Astyanax / how we are using it
        // (confirmed that clocks are in sync)
        String uniqueName = Identifiers.makeRandomId(8);
        AstyanaxSample astyanaxFirst = AstyanaxSample.builder().node(first).columnFamilyName(uniqueName).build();
        Map<String, List<String>> versions;
        AstyanaxContext<Cluster> context = astyanaxFirst.newAstyanaxContextForCluster();
        try {
            versions = context.getEntity().describeSchemaVersions();
        } finally {
            context.shutdown();
        }
            
        log.info("Cassandra schema versions are: "+versions);
        if (versions.size() > 1) {
            Assert.fail("Inconsistent versions on Cassandra start: "+versions);
        }
        String keyspacePrefix = "BrooklynTests_"+Identifiers.makeRandomId(8);

        String keyspaceName = astyanaxFirst.writeData(keyspacePrefix, numRetries);

        for (Entity node : nodes) {
            AstyanaxSample astyanaxSecond = AstyanaxSample.builder().node((CassandraNode)node).columnFamilyName(uniqueName).build();
            astyanaxSecond.readData(keyspaceName, numRetries);
        }
    }

    protected static Boolean areVersionsConsistent(CassandraNode node) {
        AstyanaxContext<Cluster> context = null;
        try {
            context = new AstyanaxSample(node).newAstyanaxContextForCluster();
            Map<String, List<String>> v = context.getEntity().describeSchemaVersions();
            return v.size() == 1;
        } catch (Exception e) {
            return null;
        } finally {
            if (context != null) context.shutdown();
        }
    }

    protected static boolean isSocketOpen(CassandraNode node) {
        try {
            Socket s = new Socket(node.getAttribute(Attributes.HOSTNAME), node.getThriftPort());
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
