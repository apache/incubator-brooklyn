package brooklyn.entity.group;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.EntityManager;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MembershipTrackingPolicyTest {

    private static final long TIMEOUT_MS = 10*1000;

    SimulatedLocation loc;
    EntityManager entityManager;
    TestApplication app;
    private BasicGroup group;
    private RecordingMembershipTrackingPolicy policy;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entityManager = app.getManagementContext().getEntityManager();
        
        group = app.createAndManageChild(EntitySpec.create(BasicGroup.class)
                .configure("childrenAsMembers", true));
        policy = new RecordingMembershipTrackingPolicy(MutableMap.of("group", group));
        group.addPolicy(policy);
        policy.setGroup(group);

        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
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

        TestEntity e1 = createAndManageChildOf(group);

        assertRecordsContinually(new Record[0]);
    }

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

        RecordingMembershipTrackingPolicy policy2 = new RecordingMembershipTrackingPolicy(MutableMap.of("group", group, "sensorsToTrack", ImmutableSet.of(TestEntity.NAME)));
        group.addPolicy(policy2);
        policy2.setGroup(group);

        e1.setAttribute(TestEntity.NAME, "myname");

        assertRecordsEventually(policy2, Record.newAdded(e1), Record.newChanged(e1));
    }

    private void assertRecordsEventually(final Record... expected) {
        assertRecordsEventually(policy, expected);
    }

    private void assertRecordsEventually(final RecordingMembershipTrackingPolicy policy, final Record... expected) {
        assertRecordsEventually(policy, ImmutableList.copyOf(expected));
    }

    private void assertRecordsEventually(final RecordingMembershipTrackingPolicy policy, final List<Record>... validExpecteds) {
        TestUtils.assertEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                for (List<Record> validExpected : validExpecteds) {
                    if (policy.records.equals(validExpected)) return;
                }
                fail("actual="+policy.records+"; valid: "+validExpecteds);
            }});
    }

    private void assertRecordsContinually(final Record... expected) {
        TestUtils.assertSucceedsContinually(ImmutableMap.of("timeout", 100), new Runnable() {
            public void run() {
                assertEquals(policy.records, ImmutableList.copyOf(expected), "actual="+policy.records);
            }});
    }

    static class RecordingMembershipTrackingPolicy extends AbstractMembershipTrackingPolicy {
        final List<Record> records = new CopyOnWriteArrayList<Record>();

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
