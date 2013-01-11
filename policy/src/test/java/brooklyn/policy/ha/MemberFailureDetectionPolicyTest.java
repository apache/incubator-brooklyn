package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.BasicGroupImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.MutableMap;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class MemberFailureDetectionPolicyTest {

    private static final int TIMEOUT_MS = 10*1000;

    private MemberFailureDetectionPolicy policy;
    private BasicGroup group;
    private TestApplication app;
    private List<SensorEvent<FailureDescriptor>> events;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        events = new CopyOnWriteArrayList<SensorEvent<FailureDescriptor>>();
        app = new TestApplication();
        group = new BasicGroupImpl(MutableMap.of("childrenAsMembers", true), app);
        Entities.startManagement(app);
        
        app.getManagementSupport().getManagementContext(false).getSubscriptionManager().subscribe(
                null, 
                HASensors.ENTITY_FAILED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
        app.getManagementSupport().getManagementContext(false).getSubscriptionManager().subscribe(
                null, 
                HASensors.ENTITY_RECOVERED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testNotNotifiedOfFailuresForHealthyMembers() throws Exception {
        // Create members before and after the policy is registered, to test both scenarios
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        
        policy = new MemberFailureDetectionPolicy(MutableMap.<String,Object>of());
        group.addPolicy(policy);
        
        TestEntity e2 = new TestEntity(group);
        Entities.manage(e2);
        e2.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e2.setAttribute(TestEntity.SERVICE_UP, true);

        TestUtils.assertSucceedsContinually(new Runnable() {
            public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
    
    @Test
    public void testNotifiedOfFailedMember() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.<String,Object>of());
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
    }
    
    @Test
    public void testNotifiedOfFailedMemberOnStateOnFire() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.<String,Object>of());
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.ON_FIRE);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
    }
    
    @Test
    public void testNotifiedOfRecoveredMember() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.<String,Object>of());
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);

        // And make the entity recover
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 2, "events="+events);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testOnlyReportsFailureIfPreviouslyUp() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.<String,Object>of());
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        TestUtils.assertSucceedsContinually(new Runnable() {
            public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testOnlyReportsFailureIfRunning() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.<String,Object>of());
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.STARTING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        TestUtils.assertSucceedsContinually(new Runnable() {
            public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
    
    @Test
    public void testReportsFailureWhenNotPreviouslyUp() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.of("onlyReportIfPreviouslyUp", false));
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenNoServiceState() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.of("useServiceStateRunning", false));
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(group);
        Entities.manage(e1);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenAlreadyDownOnBecomingMember() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.of("onlyReportIfPreviouslyUp", false));
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(app);
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);
        Entities.manage(e1);

        group.addMember(e1);
        
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenAlreadyOnFireOnBecomingMember() throws Exception {
        policy = new MemberFailureDetectionPolicy(MutableMap.of("onlyReportIfPreviouslyUp", false));
        group.addPolicy(policy);
        
        TestEntity e1 = new TestEntity(app);
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.ON_FIRE);
        Entities.manage(e1);

        group.addMember(e1);
        
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    private void assertHasEvent(Sensor<?> sensor, Predicate<Object> componentPredicate, Predicate<? super CharSequence> descriptionPredicate) {
        for (SensorEvent<FailureDescriptor> event : events) {
            if (event.getSensor().equals(sensor) && 
                    (componentPredicate == null || componentPredicate.apply(event.getValue().getComponent())) &&
                    (descriptionPredicate == null || descriptionPredicate.apply(event.getValue().getDescription()))) {
                return;
            }
        }
        fail("No matching "+sensor+" event found; events="+events);
    }
    
    private void assertHasEventEventually(final Sensor<?> sensor, final Predicate<Object> componentPredicate, final Predicate<? super CharSequence> descriptionPredicate) {
        TestUtils.executeUntilSucceeds(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertHasEvent(sensor, componentPredicate, descriptionPredicate);
            }});
    }
}
