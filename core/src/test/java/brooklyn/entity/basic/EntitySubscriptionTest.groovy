package brooklyn.entity.basic;

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.List
import java.util.concurrent.CopyOnWriteArrayList

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicSensorEvent
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.SubscriptionHandle
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class EntitySubscriptionTest {

    // TODO Duplication between this and PolicySubscriptionTest
    
    private static final long TIMEOUT_MS = 5000;
    private static final long SHORT_WAIT_MS = 100;
    
    private SimulatedLocation loc;
    private TestApplication app;
    private TestEntity entity;
    private TestEntity observedEntity;
    private TestEntity observedChildEntity;
    private TestEntity otherEntity;
    private RecordingSensorEventListener listener;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        loc = new SimulatedLocation();
        app = new TestApplication();
        entity = new TestEntity(owner:app);
        observedEntity = new TestEntity(owner:app);
        observedChildEntity = new TestEntity(owner:observedEntity);
        otherEntity = new TestEntity(owner:app);
        listener = new RecordingSensorEventListener();
        app.start([loc])
    }
    
    @Test
    public void testSubscriptionReceivesEvents() {
        entity.subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        entity.subscribe(observedEntity, TestEntity.NAME, listener);
        entity.subscribe(observedEntity, TestEntity.MY_NOTIF, listener);
        
        otherEntity.setAttribute(TestEntity.SEQUENCE, 123);
        observedEntity.setAttribute(TestEntity.SEQUENCE, 123);
        observedEntity.setAttribute(TestEntity.NAME, "myname");
        observedEntity.emit(TestEntity.MY_NOTIF, 456);
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(listener.events, [
                new BasicSensorEvent(TestEntity.SEQUENCE, observedEntity, 123),
                new BasicSensorEvent(TestEntity.NAME, observedEntity, "myname"),
                new BasicSensorEvent(TestEntity.MY_NOTIF, observedEntity, 456)
            ])
        }
    }
    
    @Test
    public void testSubscriptionToAllReceivesEvents() {
        entity.subscribe(null, TestEntity.SEQUENCE, listener);
        
        observedEntity.setAttribute(TestEntity.SEQUENCE, 123);
        otherEntity.setAttribute(TestEntity.SEQUENCE, 456);
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(listener.events, [
                new BasicSensorEvent(TestEntity.SEQUENCE, observedEntity, 123),
                new BasicSensorEvent(TestEntity.SEQUENCE, otherEntity, 456),
            ])
        }
    }
    
    @Test
    public void testSubscribeToChildrenReceivesEvents() {
        entity.subscribeToChildren(observedEntity, TestEntity.SEQUENCE, listener);
        
        observedChildEntity.setAttribute(TestEntity.SEQUENCE, 123);
        observedEntity.setAttribute(TestEntity.SEQUENCE, 456);
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(listener.events, [
                new BasicSensorEvent(TestEntity.SEQUENCE, observedChildEntity, 123)
            ])
        }
    }
    
    @Test
    public void testUnsubscribeRemovesAllSubscriptionsForThatEntity() {
        entity.subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        entity.subscribe(observedEntity, TestEntity.NAME, listener);
        entity.subscribe(observedEntity, TestEntity.MY_NOTIF, listener);
        entity.subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        entity.unsubscribe(observedEntity);
        
        observedEntity.setAttribute(TestEntity.SEQUENCE, 123);
        observedEntity.setAttribute(TestEntity.NAME, "myname");
        observedEntity.emit(TestEntity.MY_NOTIF, 123);
        otherEntity.setAttribute(TestEntity.SEQUENCE, 456);
        
        Thread.sleep(SHORT_WAIT_MS)
        assertEquals(listener.events, [
            new BasicSensorEvent(TestEntity.SEQUENCE, otherEntity, 456)
        ]);
    }
    
    @Test
    public void testUnsubscribeUsingHandleStopsEvents() {
        SubscriptionHandle handle1 = entity.subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        SubscriptionHandle handle2 = entity.subscribe(observedEntity, TestEntity.NAME, listener);
        SubscriptionHandle handle3 = entity.subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        
        entity.unsubscribe(observedEntity, handle2)
        
        observedEntity.setAttribute(TestEntity.SEQUENCE, 123);
        observedEntity.setAttribute(TestEntity.NAME, "myname");
        otherEntity.setAttribute(TestEntity.SEQUENCE, 456);
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(listener.events, [
                new BasicSensorEvent(TestEntity.SEQUENCE, observedEntity, 123),
                new BasicSensorEvent(TestEntity.SEQUENCE, otherEntity, 456)
            ])
        }
    }
    
    @Test
    public void testSubscriptionReceivesEventsInOrder() {
        final int NUM_EVENTS = 100
        entity.subscribe(observedEntity, TestEntity.MY_NOTIF, listener);

        for (int i = 0; i < NUM_EVENTS; i++) {
            observedEntity.emit(TestEntity.MY_NOTIF, i);
        }
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(listener.events.size(), NUM_EVENTS)
            for (int i = 0; i < NUM_EVENTS; i++) {
                assertEquals(listener.events.get(i).getValue(), i)
            }
        }
    }

    private static class RecordingSensorEventListener implements SensorEventListener<Object> {
        final List<SensorEvent<?>> events = new CopyOnWriteArrayList<SensorEvent<?>>();
        
        @Override public void onEvent(SensorEvent<Object> event) {
            events.add(event);
        }
    }
}
