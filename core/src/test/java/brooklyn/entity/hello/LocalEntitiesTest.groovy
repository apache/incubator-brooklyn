package brooklyn.entity.hello;

import static org.testng.Assert.*

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.EventListener;
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

    //REVIEW 1459 - new test
    //subscriptions get notified in separate thread
    @Test
    public void testEffectorEmitsAttributeSensor() {
        AbstractApplication a = new AbstractApplication() {}
        HelloEntity h = new HelloEntity(owner:a)
        a.start([new MockLocation()])
        
        AtomicReference<SensorEvent> evt = new AtomicReference()
        a.getSubscriptionContext().subscribe(h, HelloEntity.AGE, { 
            SensorEvent e -> 
            evt.set(e)
            synchronized (evt) {
                evt.notifyAll();
            }
        } as EventListener)
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
    
    //REVIEW 1459 - new test
    @Test
    public void testEffectorEmitsTransientSensor() {
        AbstractApplication a = new AbstractApplication() {}
        HelloEntity h = new HelloEntity(owner:a)
        a.start([new MockLocation()])
        
        AtomicReference<SensorEvent> evt = new AtomicReference()
        a.getSubscriptionContext().subscribe(h, HelloEntity.ITS_MY_BIRTHDAY, {
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

    @Test
    public void testSendMultipleInOrderThenUnsubscribe() {
        AbstractApplication a = new AbstractApplication() {}
        HelloEntity h = new HelloEntity(owner:a)
        a.start([new MockLocation()])

        List data = []       
        a.getSubscriptionContext().subscribe(h, HelloEntity.AGE, { SensorEvent e -> 
            data << e.value
            Thread.sleep((int)(20*Math.random()))
            println "notify on subscription received for "+e.value
            synchronized (data) { data.notifyAll() } 
        });

        long startTime = System.currentTimeMillis()
        synchronized (data) {
            (1..5).each { h.setAge(it) }
            (1..5).each { println "waiting on $it"; data.wait(2000); }
        }
        a.getSubscriptionContext().unsubscribeAll();
        h.setAge(6)
        Thread.sleep(50);
        assertEquals((1..5), data)
        assertTrue(System.currentTimeMillis() - startTime < 2000)  //shouldn't have blocked for anywhere close to 2s
    }

}
