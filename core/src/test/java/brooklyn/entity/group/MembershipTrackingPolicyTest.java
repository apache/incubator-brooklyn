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
package brooklyn.entity.group;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.Sensor;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.EntityManager;
import brooklyn.policy.PolicySpec;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MembershipTrackingPolicyTest extends BrooklynAppUnitTestSupport {

    private static final long TIMEOUT_MS = 10*1000;

    SimulatedLocation loc;
    EntityManager entityManager;
    private BasicGroup group;
    private RecordingMembershipTrackingPolicy policy;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        entityManager = app.getManagementContext().getEntityManager();
        
        group = app.createAndManageChild(EntitySpec.create(BasicGroup.class)
                .configure("childrenAsMembers", true));
        policy = app.addPolicy(PolicySpec.create(RecordingMembershipTrackingPolicy.class)
                .configure("group", group));

        app.start(ImmutableList.of(loc));
    }

    private TestEntity createAndManageChildOf(Entity parent) {
        EntityManager entityManager = app.getManagementContext().getEntityManager();
        TestEntity result = entityManager.createEntity(EntitySpec.create(TestEntity.class).parent(parent));
        Entities.manage(result);
        return result;
    }

    @Test
    public void testNotifiedOfMemberAddedAndRemoved() throws Exception {
        TestEntity e1 = createAndManageChildOf(group);

        assertRecordsEventually(Record.newAdded(e1));

        e1.clearParent();
        assertRecordsEventually(Record.newAdded(e1), Record.newRemoved(e1));
    }

    @Test
    public void testNotifiedOfMemberChanged() throws Exception {
        TestEntity e1 = createAndManageChildOf(group);

        e1.setAttribute(Startable.SERVICE_UP, true);

        assertRecordsEventually(Record.newAdded(e1), Record.newChanged(e1));
    }

    @Test
    public void testNotNotifiedWhenPolicySuspended() throws Exception {
        policy.suspend();

        createAndManageChildOf(group);
        assertRecordsContinually(new Record[0]);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNotifiedOfEverythingWhenPolicyResumed() throws Exception {
        TestEntity e1 = createAndManageChildOf(group);

        assertRecordsEventually(Record.newAdded(e1));

        policy.suspend();

        TestEntity e2 = createAndManageChildOf(group);
        assertRecordsContinually(Record.newAdded(e1));

        policy.resume();

        // Order of members set is non-deterministic, so could get [e1,e1,e2] or [e1,e2,e1]
        assertRecordsEventually(policy, ImmutableList.of(Record.newAdded(e1), Record.newAdded(e1), Record.newAdded(e2)),
                ImmutableList.of(Record.newAdded(e1), Record.newAdded(e2), Record.newAdded(e1)));
    }

    @Test
    public void testNotifiedOfSubsequentChangesWhenPolicyResumed() throws Exception {
        policy.suspend();
        policy.resume();

        TestEntity e1 = createAndManageChildOf(group);
        assertRecordsEventually(Record.newAdded(e1));
    }

    @Test
    public void testNotifiedOfExtraTrackedSensors() throws Exception {
        TestEntity e1 = createAndManageChildOf(group);

        RecordingMembershipTrackingPolicy policy2 = app.addPolicy(PolicySpec.create(RecordingMembershipTrackingPolicy.class)
                .configure("group", group)
                .configure("sensorsToTrack", ImmutableSet.of(TestEntity.NAME)));


        e1.setAttribute(TestEntity.NAME, "myname");

        assertRecordsEventually(policy2, Record.newAdded(e1), Record.newChanged(e1));
    }
    
    @Test
    public void testDeprecatedSetGroupWorks() throws Exception {
        RecordingMembershipTrackingPolicy policy2 = new RecordingMembershipTrackingPolicy(MutableMap.of("sensorsToTrack", ImmutableSet.of(TestEntity.NAME)));
        group.addPolicy(policy2);
        policy2.setGroup(group);

        TestEntity e1 = createAndManageChildOf(group);
        e1.setAttribute(TestEntity.NAME, "myname");

        assertRecordsEventually(policy2, Record.newAdded(e1), Record.newChanged(e1));
    }
    
    @Test
    public void testNotNotifiedOfExtraTrackedSensorsIfNonDuplicate() throws Exception {
        TestEntity e1 = createAndManageChildOf(group);
        
        RecordingMembershipTrackingPolicy nonDuplicateTrackingPolicy = app.addPolicy(PolicySpec.create(RecordingMembershipTrackingPolicy.class)
                .configure(AbstractMembershipTrackingPolicy.SENSORS_TO_TRACK, ImmutableSet.<Sensor<?>>of(TestEntity.NAME))
                .configure(AbstractMembershipTrackingPolicy.NOTIFY_ON_DUPLICATES, false)
                .configure(AbstractMembershipTrackingPolicy.GROUP, group));

        e1.setAttribute(TestEntity.NAME, "myname");
        assertRecordsEventually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1));
        
        e1.setAttribute(TestEntity.NAME, "myname");
        assertRecordsContinually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1));
        
        e1.setAttribute(TestEntity.NAME, "mynewname");
        assertRecordsEventually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1), Record.newChanged(e1));
    }

    // NOTIFY_ON_DUPLICATES==false is default
    @Test
    public void testDefaultNotNotifiedOfExtraTrackedSensorsIfDuplicate() throws Exception {
        TestEntity e1 = createAndManageChildOf(group);
        
        RecordingMembershipTrackingPolicy nonDuplicateTrackingPolicy = app.addPolicy(PolicySpec.create(RecordingMembershipTrackingPolicy.class)
                .configure(AbstractMembershipTrackingPolicy.SENSORS_TO_TRACK, ImmutableSet.<Sensor<?>>of(TestEntity.NAME))
                .configure(AbstractMembershipTrackingPolicy.GROUP, group));

        e1.setAttribute(TestEntity.NAME, "myname");
        assertRecordsEventually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1));
        
        e1.setAttribute(TestEntity.NAME, "myname");
        assertRecordsContinually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1));
        
        e1.setAttribute(TestEntity.NAME, "mynewname");
        assertRecordsEventually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1), Record.newChanged(e1));
    }

    @Test
    public void testNotifiedOfExtraTrackedSensorsIfDuplicate() throws Exception {
        TestEntity e1 = createAndManageChildOf(group);
        
        RecordingMembershipTrackingPolicy nonDuplicateTrackingPolicy = app.addPolicy(PolicySpec.create(RecordingMembershipTrackingPolicy.class)
                .configure(AbstractMembershipTrackingPolicy.SENSORS_TO_TRACK, ImmutableSet.<Sensor<?>>of(TestEntity.NAME))
                .configure(AbstractMembershipTrackingPolicy.NOTIFY_ON_DUPLICATES, true)
                .configure(AbstractMembershipTrackingPolicy.GROUP, group));

        e1.setAttribute(TestEntity.NAME, "myname");
        assertRecordsEventually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1));
        
        e1.setAttribute(TestEntity.NAME, "myname");
        assertRecordsEventually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1), Record.newChanged(e1));
        
        e1.setAttribute(TestEntity.NAME, "mynewname");
        assertRecordsEventually(nonDuplicateTrackingPolicy, Record.newAdded(e1), Record.newChanged(e1), Record.newChanged(e1), Record.newChanged(e1));
    }

    private void assertRecordsEventually(final Record... expected) {
        assertRecordsEventually(policy, expected);
    }

    @SuppressWarnings("unchecked")
    private void assertRecordsEventually(final RecordingMembershipTrackingPolicy policy, final Record... expected) {
        assertRecordsEventually(policy, ImmutableList.copyOf(expected));
    }

    private void assertRecordsEventually(final RecordingMembershipTrackingPolicy policy, final List<Record>... validExpecteds) {
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                for (List<Record> validExpected : validExpecteds) {
                    if (policy.records.equals(validExpected)) return;
                }
                fail("actual="+policy.records+"; valid: "+Arrays.toString(validExpecteds));
            }});
    }

    private void assertRecordsContinually(final Record... expected) {
        assertRecordsContinually(policy, expected);
    }
    
    private void assertRecordsContinually(final RecordingMembershipTrackingPolicy policy, final Record... expected) {
        Asserts.succeedsContinually(ImmutableMap.of("timeout", 100), new Runnable() {
            public void run() {
                assertEquals(policy.records, ImmutableList.copyOf(expected), "actual="+policy.records);
            }});
    }

    // Needs to be public when instantiated from a spec (i.e. by InternalPolicyFactory)
    public static class RecordingMembershipTrackingPolicy extends AbstractMembershipTrackingPolicy {
        final List<Record> records = new CopyOnWriteArrayList<Record>();

        public RecordingMembershipTrackingPolicy() {
            super();
        }
        
        public RecordingMembershipTrackingPolicy(MutableMap<String, ?> flags) {
            super(flags);
        }

        @Override protected void onEntityChange(Entity member) {
            records.add(Record.newChanged(member));
        }

        @Override protected void onEntityAdded(Entity member) {
            records.add(Record.newAdded(member));
        }

        @Override protected void onEntityRemoved(Entity member) {
            records.add(Record.newRemoved(member));
        }
    }

    static class Record {
        final String action;
        final Entity member;

        static Record newChanged(Entity member) {
            return new Record("change", member);
        }
        static Record newAdded(Entity member) {
            return new Record("added", member);
        }
        static Record newRemoved(Entity member) {
            return new Record("removed", member);
        }
        static Record newChangeRecord(Entity member) {
            return new Record("change", member);
        }
        private Record(String action, Entity member) {
            this.action = action;
            this.member = member;
        }
        @Override
        public String toString() {
            return action+"("+member+")";
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(action, member);
        }
        @Override
        public boolean equals(Object other) {
            return other instanceof Record && Objects.equal(action, ((Record)other).action) &&
                    Objects.equal(member, ((Record)other).member);
        }
    }
}
