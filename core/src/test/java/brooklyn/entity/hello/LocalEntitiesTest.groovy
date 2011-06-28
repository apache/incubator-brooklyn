package brooklyn.entity.hello;

import static org.junit.Assert.*

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Ignore
import org.junit.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.SensorEvent
import brooklyn.location.Location

/** tests effector invocation and a variety of sensor accessors and subscribers */
class LocalEntitiesTest {

    private static class MockLocation implements Location {
    }
    
    @Test
    public void testEffectorUpdatesAttributeSensor() {
        AbstractApplication a = new AbstractApplication() {}
        HelloEntity h = new HelloEntity(owner:a)
        a.start([new MockLocation()])
        
        h.setAge(5)
        assertEquals(5, h.getAttribute(HelloEntity.AGE))
    }

    @Ignore //needs FIXME SUBS before will pass (so subscriptions can be registered, and so subscriptions get notified in separate thread)
    @Test
    public void testEffectorEmitsAttributeSensor() {
        AbstractApplication a = new AbstractApplication() {}
        HelloEntity h = new HelloEntity(owner:a)
        a.start([new MockLocation()])
        
        AtomicReference<SensorEvent> evt = new AtomicReference()
        a.getManagementContext().getSubscriptionManager.subscribe(/* listener tag (typically an entity) */ null, h, HelloEntity.AGE, { 
            SensorEvent e -> 
            evt.set(e)
            synchronized (evt) {
                evt.notifyAll();
            }
        })
        long startTime = System.currentTimeMillis()
        synchronized (evt) {
            h.setAge(5)
            evt.wait(5000)
        }
        assertEquals(HelloEntity.AGE, evt.get().sensor)
        assertEquals(h, evt.get().source)
        assertEquals(5, evt.get().value)
        assertTrue(System.currentTimeMillis() - startTime < 5000)  //shouldn't have blocked for all 5s
    }
    
    @Ignore //needs FIXME SUBS before will pass
    @Test
    public void testEffectorEmitsTransientSensor() {
        AbstractApplication a = new AbstractApplication() {}
        HelloEntity h = new HelloEntity(owner:a)
        a.start([new MockLocation()])
        
        AtomicReference<SensorEvent> evt = new AtomicReference()
        a.getManagementContext().getSubscriptionManager.subscribe(/* listener tag (typically an entity) */ null, h, HelloEntity.ITS_MY_BIRTHDAY, {
            SensorEvent e ->
            evt.set(e)
            synchronized (evt) {
                evt.notifyAll();
            }
        })
        long startTime = System.currentTimeMillis()
        synchronized (evt) {
            h.setAge(5)
            evt.wait(5000)
        }
        assertNotNull(evt.get())
        assertEquals(HelloEntity.ITS_MY_BIRTHDAY, evt.get().sensor)
        assertEquals(h, evt.get().source)
        assertNull(evt.get().value)
        assertTrue(System.currentTimeMillis() - startTime < 5000)  //shouldn't have blocked for all 5s
    }
    

}
