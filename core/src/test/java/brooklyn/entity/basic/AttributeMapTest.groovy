package brooklyn.entity.basic;

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.event.basic.AttributeMap
import brooklyn.event.basic.BasicAttributeSensor

public class AttributeMapTest {

    AttributeMap map
    private final BasicAttributeSensor<Integer> exampleSensor = [ Integer, "attributeMapTest.exampleSensor", "" ]

    @BeforeMethod
    public void setUp() {
        LocallyManagedEntity e = []
        map = new AttributeMap(e)
    }
    
    // See ENGR-2111
    @Test
    public void testConcurrentUpdatesDoNotCauseConcurrentModificationException() {
        ExecutorService executor = Executors.newCachedThreadPool()
        List<Future> futures = []
        
        try {
            for (int i = 0; i < 1000; i++) {
                final BasicAttributeSensor<Integer> nextSensor = [ Integer, "attributeMapTest.exampleSensor"+i, "" ]
                def future = executor.submit({ map.update(nextSensor, "a") } as Runnable)
                futures.add(future)
            }
            
            futures.each {
                it.get()
            }
            
        } finally {
            executor.shutdownNow()
        }
    }
    
    @Test
    public void testConcurrentUpdatesAndGetsDoNotCauseConcurrentModificationException() {
        ExecutorService executor = Executors.newCachedThreadPool()
        List<Future> futures = []
        
        try {
            for (int i = 0; i < 1000; i++) {
                final BasicAttributeSensor<Integer> nextSensor = [ Integer, "attributeMapTest.exampleSensor"+i, "" ]
                def future = executor.submit({ map.update(nextSensor, "a") } as Runnable)
                def future2 = executor.submit({ map.getValue(nextSensor) } as Runnable)
                futures.add(future)
                futures.add(future2)
            }

            futures.each {
                it.get()
            }
            
        } finally {
            executor.shutdownNow()
        }
    }
}
