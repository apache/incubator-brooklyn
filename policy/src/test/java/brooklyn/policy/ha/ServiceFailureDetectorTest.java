package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ManagementContext;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;

public class ServiceFailureDetectorTest {

    private static final int TIMEOUT_MS = 10*1000;

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity e1;
    private ServiceFailureDetector policy;
    
    private List<SensorEvent<FailureDescriptor>> events;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        events = new CopyOnWriteArrayList<SensorEvent<FailureDescriptor>>();
        
        managementContext = Entities.newManagementContext();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        app.getManagementContext().getSubscriptionManager().subscribe(
                e1, 
                HASensors.ENTITY_FAILED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
        app.getManagementContext().getSubscriptionManager().subscribe(
                e1, 
                HASensors.ENTITY_RECOVERED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testNotNotifiedOfFailuresForHealthy() throws Exception {
        // Create members before and after the policy is registered, to test both scenarios
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        
        policy = new ServiceFailureDetector();
        e1.addPolicy(policy);
        
        assertNoEventsContinually();
    }
    
    @Test
    public void testNotifiedOfFailure() throws Exception {
        policy = new ServiceFailureDetector();
        e1.addPolicy(policy);
        
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
    }
    
    @Test
    public void testNotifiedOfFailureOnStateOnFire() throws Exception {
        policy = new ServiceFailureDetector();
        e1.addPolicy(policy);
        
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.ON_FIRE);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
    }
    
    @Test
    public void testNotifiedOfRecovery() throws Exception {
        policy = new ServiceFailureDetector();
        e1.addPolicy(policy);
        
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
        policy = new ServiceFailureDetector();
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually();
    }
    
    @Test
    public void testDisablingOnlyReportsFailureIfPreviouslyUp() throws Exception {
        policy = new ServiceFailureDetector(ImmutableMap.of("onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testSetsOnFireOnFailure() throws Exception {
        policy = new ServiceFailureDetector(ImmutableMap.of("onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE, Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testDisablingSetsOnFireOnFailure() throws Exception {
        policy = new ServiceFailureDetector(ImmutableMap.of("setOnFireOnFailure", false, "onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        EntityTestUtils.assertAttributeEqualsContinually(e1, TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testUsesServiceStateRunning() throws Exception {
        policy = new ServiceFailureDetector(ImmutableMap.of("onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);
        
        // entity no counted as failed, because serviceState != running || onfire
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually();
    }

    @Test
    public void testDisablingUsesServiceStateRunning() throws Exception {
        policy = new ServiceFailureDetector(ImmutableMap.of("useServiceStateRunning", false, "onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }

    @Test(groups="Integration") // Has a 1 second wait
    public void testOnlyReportsFailureIfRunning() throws Exception {
        policy = new ServiceFailureDetector();
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.STARTING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually();
    }
    
    @Test
    public void testReportsFailureWhenNotPreviouslyUp() throws Exception {
        policy = new ServiceFailureDetector(ImmutableMap.of("onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenNoServiceState() throws Exception {
        policy = new ServiceFailureDetector(ImmutableMap.of("useServiceStateRunning", false));
        e1.addPolicy(policy);
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenAlreadyDownOnRegisteringPolicy() throws Exception {
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        policy = new ServiceFailureDetector(ImmutableMap.of("onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenAlreadyOnFireOnRegisteringPolicy() throws Exception {
        e1.setAttribute(TestEntity.SERVICE_STATE, Lifecycle.ON_FIRE);

        policy = new ServiceFailureDetector(ImmutableMap.of("onlyReportIfPreviouslyUp", false));
        e1.addPolicy(policy);

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
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertHasEvent(sensor, componentPredicate, descriptionPredicate);
            }});
    }
    
    private void assertNoEventsContinually() {
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
}
