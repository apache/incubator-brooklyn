package brooklyn.entity.hello;

import static org.testng.Assert.*

import java.util.concurrent.atomic.AtomicReference

import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.EventListener
import brooklyn.event.SensorEvent
import brooklyn.management.Task
import brooklyn.location.Location
import brooklyn.management.ExecutionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.util.task.BasicTask
import brooklyn.util.task.ParallelTask;

import static brooklyn.event.basic.DependentConfiguration.*
import brooklyn.location.basic.AbstractLocation;

/** tests effector invocation and a variety of sensor accessors and subscribers */
class LocalEntitiesTest {

    private static class MockLocation extends AbstractLocation {
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
//            h.setAge(5)
            new ParallelTask([h /*, otherEntity, anotherEntity */ ].
                    collect { it.invoke(HelloEntity.SET_AGE, age: 5) });
            evt.wait(5000)
        }
        
        assertNotNull(evt.get())
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
            synchronized (data) { 
                println "Thread "+Thread.currentThread()+" notify on subscription received for "+e.value+", data is "+data
                data.notifyAll()
                data.wait(2000) 
            } 
        });
        //need for notify-then-wait (above) and wait-then-notify (below) is ugly but simplest way (i could find) 
        //to ensure they are in lock step; otherwise above might notify twice in succession, below not successful at resuming in between   
        long startTime = System.currentTimeMillis()
        synchronized (data) {
            (1..5).each { h.setAge(it) }
            (1..5).each { println "Thread "+Thread.currentThread()+" waiting on $it"; data.wait(2000); data.notifyAll(); }
        }
        a.getSubscriptionContext().unsubscribeAll();
        h.setAge(6)
        Thread.sleep(50);
        assertEquals((1..5), data)
        assertTrue(System.currentTimeMillis() - startTime < 2000)  //shouldn't have blocked for anywhere close to 2s
    }

    @Test
    public void testConfigSetFromAttribute() {
        AbstractApplication a = new AbstractApplication() {}
        a.setConfig(HelloEntity.MY_NAME, "Bob")
        
        HelloEntity dad = new HelloEntity(owner:a)
        HelloEntity son = new HelloEntity(owner:dad)
        
        //config is inherited
        assertEquals("Bob", a.getConfig(HelloEntity.MY_NAME))
        assertEquals("Bob", dad.getConfig(HelloEntity.MY_NAME))
        assertEquals("Bob", son.getConfig(HelloEntity.MY_NAME))
        
        //attributes are not
        a.setAttribute(HelloEntity.FAVOURITE_NAME, "Carl")
        assertEquals("Carl", a.getAttribute(HelloEntity.FAVOURITE_NAME))
        assertEquals(null, dad.getAttribute(HelloEntity.FAVOURITE_NAME))
        
        //config can be set from an attribute
        son.setConfig(HelloEntity.MY_NAME, attributeWhenReady(dad, HelloEntity.FAVOURITE_NAME
            /* third param is closure; defaults to groovy truth (see google), but could be e.g.
               , { it!=null && it.length()>0 && it!="Jebediah" }
             */ ));
        Object[] sonsConfig = new Object[1]
        Thread t = new Thread( { 
            sonsConfig[0] = son.getConfig(HelloEntity.MY_NAME);
//            println "got config "+sonsConfig[0] 
            synchronized (sonsConfig) { sonsConfig.notify() } 
        } );
        t.start();
        //thread should be blocking, not finishing after 10ms
        Thread.sleep(50);
        assertTrue(t.isAlive());
        long startTime = System.currentTimeMillis();
        synchronized (sonsConfig) {
            assertEquals(null, sonsConfig[0]);
            for (Task tt in dad.getExecutionContext().getTasks()) { println "task at dad:  $tt, "+tt.getStatusDetail(false) }
            for (Task tt in son.getExecutionContext().getTasks()) { println "task at son:  $tt, "+tt.getStatusDetail(false) }
            dad.setAttribute(HelloEntity.FAVOURITE_NAME, "Dan");
            sonsConfig.wait(1000)
        }
        //shouldn't have blocked for very long at all
        assertTrue(System.currentTimeMillis() - startTime < 800)
        //and sons config should now pick up the dad's attribute
        assertEquals("Dan", sonsConfig[0])
        
        //and config can have transformations
        son.setConfig(HelloEntity.MY_NAME, transform(attributeWhenReady(dad, HelloEntity.FAVOURITE_NAME), { it+it[-1]+"y" }))
        assertEquals("Danny", son.getConfig(HelloEntity.MY_NAME))
    }


}
