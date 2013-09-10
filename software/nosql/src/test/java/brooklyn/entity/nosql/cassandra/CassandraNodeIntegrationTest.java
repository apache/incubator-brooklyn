/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.cassandra.AstyanaxSupport.AstyanaxSample;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.EntityTestUtils;

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
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);
        Entities.dumpInfo(app);

        cassandra.stop();

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a node starts and sets SERVICE_UP correctly when a jmx port is supplied.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdownWithCustomJmx() {
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "19001+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);

        cassandra.stop();

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a keyspace and column family can be created and used with Astyanax client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        cassandra = app.createAndManageChild(EntitySpec.create(CassandraNode.class)
                .configure("thriftPort", "9876+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(cassandra, Startable.SERVICE_UP, true);

        AstyanaxSample astyanax = new AstyanaxSample(cassandra);
        astyanax.astyanaxTest();
    }
}
