package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.FailingEntity;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ManagementContext;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ServiceRestarterTest {

    private static final int TIMEOUT_MS = 10*1000;

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity e1;
    private ServiceRestarter policy;
    private SensorEventListener<Object> eventListener;
    private List<SensorEvent<?>> events;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        events = Lists.newCopyOnWriteArrayList();
        eventListener = new SensorEventListener<Object>() {
            @Override public void onEvent(SensorEvent<Object> event) {
                events.add(event);
            }
        };
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testRestartsOnFailure() throws Exception {
        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e1.addPolicy(policy);
        
        e1.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(e1.getCallHistory(), ImmutableList.of("restart"));
            }});
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testDoesNotRestartsWhenHealthy() throws Exception {
        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e1.addPolicy(policy);
        
        e1.emit(HASensors.ENTITY_RECOVERED, new FailureDescriptor(e1, "not a failure"));
        
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertEquals(e1.getCallHistory(), ImmutableList.of());
            }});
    }
    
    @Test
    public void testEmitsFailureEventWhenRestarterFails() throws Exception {
        final FailingEntity e2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.FAIL_ON_RESTART, true));
        app.subscribe(e2, ServiceRestarter.ENTITY_RESTART_FAILED, eventListener);

        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e2.addPolicy(policy);

        e2.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e2, "simulate failure"));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(Iterables.getOnlyElement(events).getSensor(), ServiceRestarter.ENTITY_RESTART_FAILED, "events="+events);
                assertEquals(Iterables.getOnlyElement(events).getSource(), e2, "events="+events);
                assertEquals(((FailureDescriptor)Iterables.getOnlyElement(events).getValue()).getComponent(), e2, "events="+events);
            }});
        
        assertEquals(e2.getAttribute(Attributes.SERVICE_STATE), Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testDoesNotSetOnFireOnFailure() throws Exception {
        final FailingEntity e2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.FAIL_ON_RESTART, true));
        app.subscribe(e2, ServiceRestarter.ENTITY_RESTART_FAILED, eventListener);

        policy = new ServiceRestarter(new ConfigBag()
                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED)
                .configure(ServiceRestarter.SET_ON_FIRE_ON_FAILURE, false));
        e2.addPolicy(policy);

        e2.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e2, "simulate failure"));
        
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertNotEquals(e2.getAttribute(Attributes.SERVICE_STATE), Lifecycle.ON_FIRE);
            }});
    }
}
