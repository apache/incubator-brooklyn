/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;

import static brooklyn.test.TestUtils.executeUntilSucceeds;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MongoDbReplicaSetEc2LiveTest extends AbstractEc2LiveTest {

    protected static final Logger LOG = LoggerFactory.getLogger(MongoDbReplicaSetEc2LiveTest.class);
    private static final int REPLICA_SET_SIZE = 1;

    /**
     * Test that a two node cluster starts up and allows access through both nodes.
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        final MongoDbReplicaSet cluster = app.createAndManageChild(BasicEntitySpec.newInstance(MongoDbReplicaSet.class)
                .configure(MongoDbReplicaSet.INITIAL_SIZE, REPLICA_SET_SIZE)
                .configure(MongoDbReplicaSet.REPLICA_SET_NAME, "AmazonCluster"));
        assertEquals((int)cluster.getCurrentSize(), 0);

        app.start(ImmutableList.of(loc));

        executeUntilSucceeds(ImmutableMap.of("timeout", 2 * 60 * 1000), new Callable<Boolean>() {
            public Boolean call() {
                assertEquals((int) cluster.getCurrentSize(), REPLICA_SET_SIZE);
                for (Entity e : cluster.getMembers()) {
                    assertTrue(e.getAttribute(Startable.SERVICE_UP));
                    assertEquals((int)e.getAttribute(MongoDbServer.REPLICA_SET_NUMBER_OF_HOSTS), REPLICA_SET_SIZE);
                }
                return true;
            }
        });

        Entities.dumpInfo(app);

        MongoDbServer first = (MongoDbServer) Iterables.get(cluster.getMembers(), 0);
//        MongoDbServer second = (MongoDbServer) Iterables.get(cluster.getMembers(), 1);

    }

    @Test(enabled=false)
    public void testDummy() {} // Convince TestNG IDE integration that this really does have test methods
}
