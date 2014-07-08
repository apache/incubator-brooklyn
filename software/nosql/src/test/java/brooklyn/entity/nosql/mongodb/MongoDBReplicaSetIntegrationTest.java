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
package brooklyn.entity.nosql.mongodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import groovy.time.TimeDuration;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mongodb.DBObject;

public class MongoDBReplicaSetIntegrationTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(MongoDBReplicaSetIntegrationTest.class);
    
    private TestApplication app;
    private Collection<LocalhostMachineProvisioningLocation> localhostMachineProvisioningLocation;

    // Replica sets can take a while to start
    private static final TimeDuration TIMEOUT = new TimeDuration(0, 0, 180, 0);

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
        localhostMachineProvisioningLocation = ImmutableList.of(location);
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Creates and starts a replica set, asserts it reaches the given size
     * and that the primary and secondaries are non-null.
     */
    private MongoDBReplicaSet makeAndStartReplicaSet(final Integer size, final String testDescription) {
        // Sets secondaryPreferred so we can read from slaves.
        final MongoDBReplicaSet replicaSet = app.createAndManageChild(EntitySpec.create(MongoDBReplicaSet.class)
                .configure(DynamicCluster.INITIAL_SIZE, size)
                .configure("replicaSetName", "test-rs-"+testDescription)
                .configure("memberSpec", EntitySpec.create(MongoDBServer.class)
                        .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf")
                        .configure("port", "27017+")));
        app.start(localhostMachineProvisioningLocation);

        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize(), size);
                assertNotNull(replicaSet.getPrimary(), "replica set has no primary");
                assertEquals(replicaSet.getPrimary().getReplicaSet().getName(), "test-rs-"+testDescription+replicaSet.getId());
                assertEquals(replicaSet.getSecondaries().size(), size-1);
            }
        });
        return replicaSet;
    }

    @Test(groups = "Integration")
    public void testCanStartAndStopAReplicaSet() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "can-start-and-stop");
        replicaSet.stop();
        assertFalse(replicaSet.getAttribute(Startable.SERVICE_UP));
    }

    @Test(groups = "Integration")
    public void testWriteToMasterAndReadFromSecondary() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "master-write-secondary-read");

        // Test we can read a document written to the primary from all secondaries
        final String documentId = MongoDBTestHelper.insert(replicaSet.getPrimary(), "meaning-of-life", 42);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize().intValue(), 3);
                for (MongoDBServer secondary : replicaSet.getSecondaries()) {
                    DBObject docOut = MongoDBTestHelper.getById(secondary, documentId);
                    assertEquals(docOut.get("meaning-of-life"), 42);
                }
            }
        });
    }

    @Test(groups = "Integration")
    public void testCanResizeAndReadFromNewInstances() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "resize-and-read-from-secondaries");

        // Test we can a document written to the primary from all secondaries
        final String documentId = MongoDBTestHelper.insert(replicaSet.getPrimary(), "meaning-of-life", 42);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize().intValue(), 3);
                for (MongoDBServer secondary : replicaSet.getSecondaries()) {
                    DBObject docOut = MongoDBTestHelper.getById(secondary, documentId);
                    assertEquals(docOut.get("meaning-of-life"), 42);
                }
            }
        });

        // Resize and confirm new members get data
        replicaSet.resize(5);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize().intValue(), 5);
                Collection<MongoDBServer> secondaries = replicaSet.getSecondaries();
                assertEquals(secondaries.size(), 4);
                for (MongoDBServer secondary : secondaries) {
                    DBObject docOut = MongoDBTestHelper.getById(secondary, documentId);
                    assertEquals(docOut.get("meaning-of-life"), 42);
                }
            }
        });

    }

    @Test(groups = "Integration")
    public void testResizeToEvenNumberOfMembers() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "resize-even-ignored");
        assertEquals(replicaSet.getCurrentSize().intValue(), 3);
        replicaSet.resize(4);
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize().intValue(), 4);
            }
        });
    }

    /**
     * Test replacing the primary succeeds. More interesting than replacing a secondary
     * because the removal of a primary must happen _through_ the primary. The flow is:
     *  - Brooklyn removes the server from the set and stops it
     *  - The remaining members of the set elect a new primary
     *  - We remove the original primary from the new primary.
     */
    @Test(groups = "Integration")
    public void testReplacePrimary() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "replace-primary");
        final MongoDBServer replaced = replicaSet.getPrimary();
        replicaSet.replaceMember(replaced.getId());
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize().intValue(), 3);
                for (Entity member : replicaSet.getMembers()) {
                    assertNotEquals(member.getId(), replaced.getId());
                }
                assertNotNull(replicaSet.getPrimary());
                assertNotEquals(replicaSet.getPrimary().getId(), replaced.getId(), "Expected a new primary to have been elected");
            }
        });
    }

    @Test(groups = "Integration")
    public void testRemovePrimary() {
        final MongoDBReplicaSet replicaSet = makeAndStartReplicaSet(3, "remove-primary");
        final MongoDBServer removed = replicaSet.getPrimary();

        replicaSet.removeMember(removed);
        removed.stop();
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT), new Runnable() {
            @Override
            public void run() {
                assertEquals(replicaSet.getCurrentSize().intValue(), 2);
                for (Entity member : replicaSet.getMembers()) {
                    assertNotEquals(member.getId(), removed.getId());
                }
                assertNotNull(replicaSet.getPrimary());
                assertNotEquals(replicaSet.getPrimary().getId(), removed.getId(), "Expected a new primary to have been elected");
            }
        });
    }
}
