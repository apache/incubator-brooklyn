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

class BufferingEnricherTest {
    
    @BeforeMethod
    public void before() {
    }

    @AfterMethod
    public void after() {
    }
    
    @Test
    public void testBufferWrapsSensor() {
        CyclicBarrier barrier = new CyclicBarrier(2)
        
        AbstractApplication app = new AbstractApplication() {}
        
        EntityLocal producer = new LocallyManagedEntity(owner:app)
        EntityLocal bufferer = new LocallyManagedEntity(owner:app)
        
        Sensor<Integer> intSensor = new BasicSensor<Integer>(Integer.class, "int sensor")
        SubscriptionContext subscription  = new BasicSubscriptionContext(app.getManagementContext().getSubscriptionManager(), this)
        BufferingEnricher<Integer> buffer = new BufferingEnricher<Integer>(bufferer, producer, intSensor, false)
        
        subscription.subscribe(bufferer, buffer.result, new EventListener<BufferChangedEvent>() {
            void onEvent(SensorEvent<BufferChangedEvent> e) {
                barrier.await()
            }
        })
        
        subscription.publish(intSensor.newEvent(producer, 1))
        barrier.await()
        assertEquals(buffer.getBuffer(), [1])
        subscription.publish(intSensor.newEvent(producer, 2))
        barrier.await()
        assertEquals(buffer.getBuffer(), [2, 1])
        subscription.publish(intSensor.newEvent(producer, 3))
        barrier.await()
        assertEquals(buffer.getBuffer(), [3, 2, 1])
        subscription.publish(intSensor.newEvent(producer, 4))
        barrier.await()
        assertEquals(buffer.getBuffer(), [4, 3, 2, 1])
    }

}
