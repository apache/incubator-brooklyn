package brooklyn.policy

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CyclicBarrier

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicSensor
import brooklyn.management.SubscriptionContext
import brooklyn.management.internal.BasicSubscriptionContext
import brooklyn.policy.BufferingEnricher.BufferChangedEvent
import brooklyn.policy.BufferingEnricher.BufferEvent
import brooklyn.policy.BufferingEnricher.BufferFlushedEvent

class BufferingEnricherTest {
    
    public static class BarrieredEventListener<T> implements EventListener<T> {
        CyclicBarrier changeBarrier = new CyclicBarrier(2)
        CyclicBarrier flushBarrier = new CyclicBarrier(2)
        
        public void onEvent(SensorEvent<BufferEvent> e) {awaitEvent(e.getValue())}
        public void awaitEvent(BufferEvent e) {
            if(e instanceof BufferChangedEvent) {changeBarrier.await()}
            if(e instanceof BufferFlushedEvent) {flushBarrier.await()}}
    }
    
    public static final BarrieredEventListener<BufferChangedEvent> BARRIER_EVENT_LISTENER =
        new BarrieredEventListener<BufferChangedEvent>()
    
    AbstractApplication app
    
    EntityLocal producer
    EntityLocal bufferer
    
    Sensor<Integer> intSensor
    SubscriptionContext subscription
    
    @BeforeMethod
    public void before() {
        app = new AbstractApplication() {}
        
        producer = new LocallyManagedEntity(owner:app)
        bufferer = new LocallyManagedEntity(owner:app)
        
        intSensor = new BasicSensor<Integer>(Integer.class, "int sensor")
        subscription = new BasicSubscriptionContext(app.getManagementContext().getSubscriptionManager(), this) {
           void publishAndWait(SensorEvent event) {publish(event); BARRIER_EVENT_LISTENER.awaitEvent(new BufferChangedEvent())}
        }
    }

    @AfterMethod
    public void after() {
    }
    
    @Test
    public void testBufferWrapsSensor() {
        BufferingEnricher<Integer> buffer = new BufferingEnricher<Integer>(bufferer, producer, intSensor, false)
        
        subscription.subscribe(bufferer, buffer.result, BARRIER_EVENT_LISTENER)
        
        assertEquals(buffer.getBuffer(), [])
        subscription.publishAndWait(intSensor.newEvent(producer, 1))
        assertEquals(buffer.getBuffer(), [1])
        subscription.publishAndWait(intSensor.newEvent(producer, 2))
        assertEquals(buffer.getBuffer(), [2, 1])
        subscription.publishAndWait(intSensor.newEvent(producer, 3))
        assertEquals(buffer.getBuffer(), [3, 2, 1])
        subscription.publishAndWait(intSensor.newEvent(producer, 4))
        assertEquals(buffer.getBuffer(), [4, 3, 2, 1])
        
        buffer.flush()
        BARRIER_EVENT_LISTENER.awaitEvent(new BufferFlushedEvent())
        assertEquals(buffer.getBuffer(), [])
        
        subscription.publishAndWait(intSensor.newEvent(producer, 1))
        subscription.publishAndWait(intSensor.newEvent(producer, 2))
        subscription.publishAndWait(intSensor.newEvent(producer, 3))
        subscription.publishAndWait(intSensor.newEvent(producer, 4))
        
        assertEquals(buffer.getBuffer(), [4, 3, 2, 1])
    }
}
