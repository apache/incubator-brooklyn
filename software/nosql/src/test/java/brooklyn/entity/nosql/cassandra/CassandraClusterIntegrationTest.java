/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynMgmtContextTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * A live test of the {@link CassandraCluster} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CassandraNode}
 * can be read from another, using the Astyanax API.
 */
public class CassandraClusterIntegrationTest extends BrooklynMgmtContextTestSupport {

    private static final Logger log = LoggerFactory.getLogger(CassandraClusterIntegrationTest.class);

    protected Location testLocation;
    protected CassandraCluster cluster;

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
        cluster = app.createAndManageChild(EntitySpec.create(CassandraCluster.class)
                .configure("initialSize", 1));
        assertEquals(cluster.getCurrentSize().intValue(), 0);

        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.GROUP_SIZE, 1);
        Entities.dumpInfo(app);

        CassandraNode node = (CassandraNode) Iterables.get(cluster.getMembers(), 0);

        EntityTestUtils.assertAttributeEqualsEventually(node, Startable.SERVICE_UP, true);
        
        // Default token-generator will assign 0 to first node
        EntityTestUtils.assertAttributeEqualsEventually(node, CassandraNode.TOKEN, BigInteger.ZERO);

        // may take some time to be consistent (with new thrift_latency checks on the node,
        // contactability should not be an issue, but consistency still might be)
        for (int i=0; ; i++) {
            boolean open = CassandraClusterLiveTest.isSocketOpen(node);
            Boolean consistant = open ? CassandraClusterLiveTest.areVersionsConsistent(node) : null;
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

        CassandraClusterLiveTest.checkConnectionRepeatedly(2, 5, node, node);
    }
}
