package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.event.basic.AttributeMap
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.entity.TestApplicationImpl
import brooklyn.test.entity.TestEntity
import brooklyn.test.entity.TestEntityImpl

public class AttributeMapTest {

    Application app;
    AttributeMap map
    private final BasicAttributeSensor<Integer> exampleSensor = [ Integer, "attributeMapTest.exampleSensor", "" ]

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new TestApplicationImpl()
        TestEntity e = new TestEntityImpl(app)
        map = new AttributeMap(e, Collections.synchronizedMap(new LinkedHashMap()));
        Entities.startManagement(app);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app);
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
    
    @Test
    public void testStoredSensorsCanBeRetrieved() {
        BasicAttributeSensor<String> sensor1 = [ Integer, "a", "" ]
        BasicAttributeSensor<String> sensor2 = [ Integer, "b.c", "" ]
        
        map.update(sensor1, "1val")
        map.update(sensor2, "2val")
        
        assertEquals(map.getValue(sensor1), "1val")
        assertEquals(map.getValue(sensor2), "2val")
        
        assertEquals(map.getValue(["a"]), "1val")
        assertEquals(map.getValue(["b","c"]), "2val")
    }
        
    @Test
    public void testStoredByPathCanBeRetrieved() {
        BasicAttributeSensor<String> sensor1 = [ Integer, "a", "" ]
        BasicAttributeSensor<String> sensor2 = [ Integer, "b.c", "" ]
        
        map.update(["a"], "1val")
        map.update(["b", "c"], "2val")
        
        assertEquals(map.getValue(sensor1), "1val")
        assertEquals(map.getValue(sensor2), "2val")
        
        assertEquals(map.getValue(["a"]), "1val")
        assertEquals(map.getValue(["b","c"]), "2val")
    }
        
    @Test
    public void testCanStoreSensorThenChildSensor() {
        BasicAttributeSensor<String> sensor = [ Integer, "a", "" ]
        BasicAttributeSensor<String> childSensor = [ Integer, "a.b", "" ]
        
        map.update(sensor, "parentValue")
        map.update(childSensor, "childValue")
        
        assertEquals(map.getValue(childSensor), "childValue")
        assertEquals(map.getValue(sensor), "parentValue")
    }
        
    @Test
    public void testCanStoreChildThenParentSensor() {
        BasicAttributeSensor<String> sensor = [ Integer, "a", "" ]
        BasicAttributeSensor<String> childSensor = [ Integer, "a.b", "" ]
        
        map.update(childSensor, "childValue")
        map.update(sensor, "parentValue")
        
        assertEquals(map.getValue(childSensor), "childValue")
        assertEquals(map.getValue(sensor), "parentValue")
    }
}
