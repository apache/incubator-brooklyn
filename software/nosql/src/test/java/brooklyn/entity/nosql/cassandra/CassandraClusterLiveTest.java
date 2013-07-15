/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.internal.TimeExtras;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * A live test of the {@link CassandraCluster} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CassandraNode}
 * can be read from another, using the Astyanax API.
 */
class CassandraClusterLiveTest {

    protected static final Logger log = LoggerFactory.getLogger(CassandraClusterLiveTest.class);

    static {
        TimeExtras.init();
    }

    // private String provider = "rackspace-cloudservers-uk";
    private String provider = "aws-ec2:eu-west-1";

    protected TestApplication app;
    protected Location testLocation;
    protected CassandraCluster cluster;

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider);
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that a two node cluster starts up and allows access via the Astyanax API through both nodes.
     */
    @Test(groups = "Live")
    public void canStartupAndShutdown() throws Exception {
        cluster = app.createAndManageChild(EntitySpecs.spec(CassandraCluster.class)
                .configure("initialSize", 2)
                .configure("clusterName", "AmazonCluster"));
        assertEquals(cluster.getCurrentSize().intValue(), 0);

        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.GROUP_SIZE, 2);
        Entities.dumpInfo(app);

        CassandraNode first = (CassandraNode) Iterables.get(cluster.getMembers(), 0);
        CassandraNode second = (CassandraNode) Iterables.get(cluster.getMembers(), 1);

        EntityTestUtils.assertAttributeEqualsEventually(first, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(second, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEquals(first, CassandraNode.PEERS, 2);
        EntityTestUtils.assertAttributeEquals(second, CassandraNode.PEERS, 2);

        AstyanaxSupport astyanaxFirst = new AstyanaxSupport(first);
        AstyanaxSupport astyanaxSecond = new AstyanaxSupport(second);
        astyanaxFirst.writeData();
        astyanaxSecond.readData();
    }
}
