package brooklyn.policy

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicSensor
import brooklyn.management.SubscriptionContext
import brooklyn.management.internal.BasicSubscriptionContext
import brooklyn.policy.SimpleTimeAveragingEnricher.ConfidenceQualifiedNumber

class SimpleTimeAveragingEnricherTest {
    
    AbstractApplication app
    
    EntityLocal producer

    Sensor<Integer> intSensor, deltaSensor
    Sensor<Double> avgSensor
    SubscriptionContext subscription

    @BeforeMethod
    public void before() {
        app = new AbstractApplication() {}

        producer = new LocallyManagedEntity(owner:app)

        intSensor = new BasicSensor<Integer>(Integer.class, "int sensor")
        deltaSensor = new BasicSensor<Integer>(Integer.class, "delta sensor")
        avgSensor = new BasicSensor<Double>(Integer.class, "avg sensor")
    }

    @AfterMethod
    public void after() {
    }

    @Test
    public void testAveraging() {
        producer.addPolicy(new DeltaEnricher<Integer>(producer, intSensor, deltaSensor))
        SimpleTimeAveragingEnricher<Integer> averager = new SimpleTimeAveragingEnricher<Integer>(producer, deltaSensor, avgSensor, 1000)
        producer.addPolicy(averager)

        ConfidenceQualifiedNumber average = averager.getAverage(0)
        assertEquals(average.value, 0)
        assertEquals(average.confidence, 0.0d)
        
        averager.onEvent(intSensor.newEvent(producer, 10), 1000)
        average = averager.getAverage(1000)
        assertEquals(average.value, 10.0d)
        assertEquals(average.confidence, 1.0d)
        
        averager.onEvent(intSensor.newEvent(producer, 20), 1500)
        average = averager.getAverage(1500)
        assertEquals(average.value, 15.0d)
        assertEquals(average.confidence, 1.0d)
        average = averager.getAverage(2000)
        assertEquals(average.value, 20.0d)
        assertEquals(average.confidence, 0.5d)
        average = averager.getAverage(3000)
        assertEquals(average.value, 20.0d)
        assertEquals(average.confidence, 0.0d)
        
        averager.onEvent(intSensor.newEvent(producer, 30), 3000)
        average = averager.getAverage(3000)
        assertEquals(average.value, 30.0d)
        assertEquals(average.confidence, 1.0d)
        
        averager.onEvent(intSensor.newEvent(producer, 10), 4000)
        averager.onEvent(intSensor.newEvent(producer, 20), 4200)
        averager.onEvent(intSensor.newEvent(producer, 30), 4400)
        averager.onEvent(intSensor.newEvent(producer, 40), 4600)
        averager.onEvent(intSensor.newEvent(producer, 50), 4800)
        averager.onEvent(intSensor.newEvent(producer, 60), 5000)
        
        average = averager.getAverage(5000)
        assertEquals(average.value, 40.0d)
        assertEquals(average.confidence, 1.0d)
    }

}
