package brooklyn.policy.basic;

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.proxying.EntitySpec
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicSensorEvent
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.SubscriptionHandle
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class PolicySubscriptionTest {

    // TODO Duplication between this and EntitySubscriptionTest
    
    private static final long TIMEOUT_MS = 5000;
    private static final long SHORT_WAIT_MS = 100;
    
    private SimulatedLocation loc;
    private TestApplication app;
    private TestEntity entity;
    private TestEntity entity2;
    private AbstractPolicy policy;
    private RecordingSensorEventListener listener;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        listener = new RecordingSensorEventListener();
        policy = new AbstractPolicy() {};
        entity.addPolicy(policy);
        app.start([loc])
    }
    
    @Test
    public void testSubscriptionReceivesEvents() {
        policy.subscribe(entity, TestEntity.SEQUENCE, listener);
        policy.subscribe(entity, TestEntity.NAME, listener);
        policy.subscribe(entity, TestEntity.MY_NOTIF, listener);
        
        entity2.setAttribute(TestEntity.SEQUENCE, 456);
        entity.setAttribute(TestEntity.SEQUENCE, 123);
        entity.setAttribute(TestEntity.NAME, "myname");
        entity.emit(TestEntity.MY_NOTIF, 789);
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(listener.events, [
                new BasicSensorEvent(TestEntity.SEQUENCE, entity, 123),
                new BasicSensorEvent(TestEntity.NAME, entity, "myname"),
                new BasicSensorEvent(TestEntity.MY_NOTIF, entity, 789)
            ])
        }
    }
    
    @Test
    public void testUnsubscribeRemovesAllSubscriptionsForThatEntity() {
        policy.subscribe(entity, TestEntity.SEQUENCE, listener);
        policy.subscribe(entity, TestEntity.NAME, listener);
        policy.subscribe(entity, TestEntity.MY_NOTIF, listener);
        policy.subscribe(entity2, TestEntity.SEQUENCE, listener);
        policy.unsubscribe(entity);
        
        entity.setAttribute(TestEntity.SEQUENCE, 123);
        entity.setAttribute(TestEntity.NAME, "myname");
        entity.emit(TestEntity.MY_NOTIF, 456);
        entity2.setAttribute(TestEntity.SEQUENCE, 789);
        
        Thread.sleep(SHORT_WAIT_MS)
        assertEquals(listener.events, [
            new BasicSensorEvent(TestEntity.SEQUENCE, entity2, 789)
        ]);
    }
    
    @Test
    public void testUnsubscribeUsingHandleStopsEvents() {
        SubscriptionHandle handle1 = policy.subscribe(entity, TestEntity.SEQUENCE, listener);
        SubscriptionHandle handle2 = policy.subscribe(entity, TestEntity.NAME, listener);
        SubscriptionHandle handle3 = policy.subscribe(entity2, TestEntity.SEQUENCE, listener);
        
        policy.unsubscribe(entity, handle2)
        
        entity.setAttribute(TestEntity.SEQUENCE, 123);
        entity.setAttribute(TestEntity.NAME, "myname");
        entity2.setAttribute(TestEntity.SEQUENCE, 456);
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(listener.events, [
                new BasicSensorEvent(TestEntity.SEQUENCE, entity, 123),
                new BasicSensorEvent(TestEntity.SEQUENCE, entity2, 456)
            ])
        }
    }
    
    private static class RecordingSensorEventListener implements SensorEventListener<Object> {
        final List<SensorEvent<?>> events = new CopyOnWriteArrayList<SensorEvent<?>>();
        
        @Override public void onEvent(SensorEvent<Object> event) {
            events.add(event);
        }
    }
}
