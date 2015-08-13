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
package org.apache.brooklyn.entity.nosql.mongodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.trait.Startable;
import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mongodb.DBObject;

public class MongoDBReplicaSetIntegrationTest extends BrooklynAppLiveTestSupport {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(MongoDBReplicaSetIntegrationTest.class);
    
    private Collection<LocalhostMachineProvisioningLocation> locs;

    // Replica sets can take a while to start
    private static final Duration TIMEOUT = Duration.of(3, TimeUnit.MINUTES);

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        locs = ImmutableList.of(app.newLocalhostProvisioningLocation());
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
        app.start(locs);

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
