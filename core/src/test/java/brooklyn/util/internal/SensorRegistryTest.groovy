package brooklyn.util.internal

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import groovy.time.TimeDuration;

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.legacy.ValueProvider;
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.TestUtils;

/**
 * Test the operation of the {@link SensorRegistry} class.
 */
public class SensorRegistryTest {
    private static final Logger log = LoggerFactory.getLogger(SensorRegistryTest.class)

    @Test
    public void sensorUpdatedPeriodically() {
        AbstractEntity entity = new LocallyManagedEntity()
        SensorRegistry sensorRegistry = new SensorRegistry(entity, [period:50])
        
        final AtomicInteger desiredVal = new AtomicInteger(1)
        BasicAttributeSensor<Integer> FOO = [ Integer, "foo", "My foo" ]
        sensorRegistry.addSensor(FOO, { return desiredVal.get() } as ValueProvider)

        executeUntilSucceeds {
            assertEquals(entity.getAttribute(FOO), 1)
        }
        desiredVal.set(2)
        executeUntilSucceeds {
            assertEquals(entity.getAttribute(FOO), 2)
        }
    }
    
    @Test
    public void sensorUpdateDefaultPeriodIsUsed() {
        final int PERIOD = 250
        AbstractEntity entity = new LocallyManagedEntity()
        SensorRegistry sensorRegistry = new SensorRegistry(entity, [period:PERIOD, connectDelay:0])
        
        List<Long> callTimes = [] as CopyOnWriteArrayList
        
        BasicAttributeSensor<Integer> FOO = [ Integer, "foo", "My foo" ]
        sensorRegistry.addSensor(FOO, { callTimes.add(System.currentTimeMillis()); return 1 } as ValueProvider)
        
        Thread.sleep(500)
        assertApproxPeriod(callTimes, PERIOD, 500)
    }

    @Test
    public void sensorUpdatePeriodOverrideIsUsed() {
        final int PERIOD = 250
        // Create an entity and configure it with the above JMX service
        AbstractEntity entity = new LocallyManagedEntity()
        SensorRegistry sensorRegistry = new SensorRegistry(entity, [period:1000, connectDelay:0])
        
        List<Long> callTimes = [] as CopyOnWriteArrayList
        
        BasicAttributeSensor<Integer> FOO = [ Integer, "foo", "My foo" ]
        sensorRegistry.addSensor(FOO, { callTimes.add(System.currentTimeMillis()); return 1 } as ValueProvider, PERIOD)
        
        Thread.sleep(500)
        assertApproxPeriod(callTimes, PERIOD, 500)
    }
    
    @Test
    public void testRemoveSensorStopsItBeingUpdated() {
        AbstractEntity entity = new LocallyManagedEntity()
        SensorRegistry sensorRegistry = new SensorRegistry(entity, [period:50])
        
        final AtomicInteger desiredVal = new AtomicInteger(1)
        
        BasicAttributeSensor<Integer> FOO = [ Integer, "foo", "My foo" ]
        sensorRegistry.addSensor(FOO, { return desiredVal.get() } as ValueProvider)

        TimeExtras.init();
        TestUtils.executeUntilSucceeds(period:10*TimeUnit.MILLISECONDS, timeout:1*TimeUnit.SECONDS, { entity.getAttribute(FOO)!=null }); 
        assertEquals(entity.getAttribute(FOO), 1)
        
        sensorRegistry.removeSensor(FOO)
        
        // The poller could already be calling the value provider, so can't simply assert never called again.
        // And want to ensure that it is never called again (after any currently executing call), so need to wait.
        // TODO Nicer way than a sleep?  (see comment in TestUtils about need for blockUntilTrue)
        
        Thread.sleep(200)
        desiredVal.set(2)
        Thread.sleep(100)
        assertEquals(entity.getAttribute(FOO), 1)
        
        sensorRegistry.updateAll()
        assertEquals(entity.getAttribute(FOO), 1)
        
        try {
            sensorRegistry.update(FOO)
            fail()
        } catch (IllegalStateException e) {
            // success
        }
    }

    @Test
    public void testClosePollerStopsItBeingUpdated() {
        AbstractEntity entity = new LocallyManagedEntity()
        SensorRegistry sensorRegistry = new SensorRegistry(entity, [period:50])
        
        final AtomicInteger desiredVal = new AtomicInteger(1)
        BasicAttributeSensor<Integer> FOO = [ Integer, "foo", "My foo" ]
        sensorRegistry.addSensor(FOO, { return desiredVal.get() } as ValueProvider)

        Thread.sleep(100)
        assertEquals(entity.getAttribute(FOO), 1)
        
        sensorRegistry.close()
        
        // The poller could already be calling the value provider, so can't simply assert never called again.
        // And want to ensure that it is never called again (after any currently executing call), so need to wait.
        // TODO Nicer way than a sleep?
        
        Thread.sleep(100)
        desiredVal.set(2)
        Thread.sleep(100)
        assertEquals(entity.getAttribute(FOO), 1)
    }

    private void assertApproxPeriod(List<Long> actual, int expectedInterval, long expectedDuration) {
        final long ACCEPTABLE_VARIANCE = 200
        long minNextExpected = actual.get(0);
        actual.each {
            assertTrue it >= minNextExpected && it <= (minNextExpected+ACCEPTABLE_VARIANCE), 
                    "expected=$minNextExpected, actual=$it, interval=$expectedInterval, series=$actual, duration=$expectedDuration"
            minNextExpected += expectedInterval
        }
        int expectedSize = expectedDuration/expectedInterval
        assertTrue Math.abs(actual.size()-expectedSize) <= 1, "actualSize=${actual.size()}, series=$actual, duration=$expectedDuration, interval=$expectedInterval"
    }
    
}
