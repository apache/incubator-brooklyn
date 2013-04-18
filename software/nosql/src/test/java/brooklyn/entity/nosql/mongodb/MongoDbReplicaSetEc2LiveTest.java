/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mongodb.DBObject;
import groovy.time.TimeDuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class MongoDbReplicaSetEc2LiveTest extends AbstractEc2LiveTest {

    protected static final Logger LOG = LoggerFactory.getLogger(MongoDbReplicaSetEc2LiveTest.class);
    private static final Integer REPLICA_SET_SIZE = 3;
    private static final TimeDuration TIMEOUT = new TimeDuration(0, 0, 180, 0);

    /**
     * Test that a three node replica set starts and allows access through both nodes.
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        final MongoDbReplicaSet replicaSet = app.createAndManageChild(EntitySpecs.spec(MongoDbReplicaSet.class)
                .configure(DynamicCluster.INITIAL_SIZE, REPLICA_SET_SIZE)
                .configure("replicaSetName", "mongodb-live-test-replica-set")
                .configure("memberSpec", EntitySpecs.spec(MongoDbServer.class)
                        .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf")
                        .configure("port", "27017+")));

        assertEquals(replicaSet.getCurrentSize().intValue(), 0);

        app.start(ImmutableList.of(loc));

        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Callable<Boolean>() {
            @Override
            public Boolean call() {
                assertEquals(replicaSet.getCurrentSize(), REPLICA_SET_SIZE);
                assertNotNull(replicaSet.getPrimary());
                assertEquals(replicaSet.getSecondaries().size(), REPLICA_SET_SIZE-1);
                return true;
            }
        });

        Entities.dumpInfo(app);

        // Test inserting a document and reading from secondaries
        final String documentId = MongoDbTestHelper.insert(replicaSet.getPrimary(), "meaning-of-life", 42);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                assertEquals(replicaSet.getCurrentSize().intValue(), 3);
                for (MongoDbServer secondary : replicaSet.getSecondaries()) {
                    DBObject docOut = MongoDbTestHelper.getById(secondary, documentId);
                    assertEquals(docOut.get("meaning-of-life"), 42);
                }
                return true;
            }
        });

    }

    @Test(enabled=false)
    public void testDummy() {} // Convince TestNG IDE integration that this really does have test methods
}
