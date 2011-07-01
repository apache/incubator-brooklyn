package brooklyn.policy

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CyclicBarrier
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicSensor
import brooklyn.management.SubscriptionContext
import brooklyn.management.internal.BasicSubscriptionContext

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
        
        Sensor<Integer> intSensor = new BasicSensor<Integer>(Integer.class, "int sensor")
        EntityLocal dummyEntity = new LocallyManagedEntity()
        SubscriptionContext subscription  = new BasicSubscriptionContext(dummyEntity.getManagementContext().getSubscriptionManager(), this)
        BufferingEnricher<Integer> buffer = new BufferingEnricher<Integer>(dummyEntity, intSensor, false)
        
        
        subscription.publish(intSensor.newEvent(dummyEntity, 1))
        println "waiting event"
//        barrier.await()
        assertEquals(buffer.getBuffer(), [1])
        subscription.publish(intSensor.newEvent(dummyEntity, 2))
//        barrier.await()
        assertEquals(buffer.getBuffer(), [2, 1])
        subscription.publish(intSensor.newEvent(dummyEntity, 3))
//        barrier.await()
        assertEquals(buffer.getBuffer(), [3, 2, 1])
        subscription.publish(intSensor.newEvent(dummyEntity, 4))
//        barrier.await()
        assertEquals(buffer.getBuffer(), [4, 3, 2, 1])
    }

}
