package brooklyn.enricher

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.enricher.RollingTimeWindowMeanEnricher.ConfidenceQualifiedNumber
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.management.SubscriptionContext

class RollingTimeWindowMeanEnricherTest {
    
    AbstractApplication app
    
    EntityLocal producer

    Sensor<Integer> intSensor, deltaSensor
    Sensor<Double> avgSensor
    SubscriptionContext subscription
    
    RollingTimeWindowMeanEnricher<Integer> averager
    ConfidenceQualifiedNumber average

    private final long timePeriod = 1000
    
    @BeforeMethod
    public void before() {
        app = new AbstractApplication() {}
        producer = new AbstractEntity(app) {}
        Entities.startManagement(app);

        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor")
        deltaSensor = new BasicAttributeSensor<Integer>(Integer.class, "delta sensor")
        avgSensor = new BasicAttributeSensor<Double>(Integer.class, "avg sensor")
        
        producer.addEnricher(new DeltaEnricher<Integer>(producer, intSensor, deltaSensor))
        averager = new RollingTimeWindowMeanEnricher<Integer>(producer, deltaSensor, avgSensor, timePeriod)
        producer.addEnricher(averager)
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testDefaultAverageWhenEmpty() {
        average = averager.getAverage(0)
        assertEquals(average.value, 0d)
        assertEquals(average.confidence, 0.0d)
    }
    
    @Test
    public void testNoRecentValuesAverage() {
        averager.onEvent(intSensor.newEvent(producer, 10), 0L)
        average = averager.getAverage(timePeriod+1000)
        assertEquals(average.value, 10d)
        assertEquals(average.confidence, 0d)
    }
    
    @Test
    public void testNoRecentValuesUsesLastForAverage() {
        averager.onEvent(intSensor.newEvent(producer, 10), 0L)
        averager.onEvent(intSensor.newEvent(producer, 20), 10L)
        average = averager.getAverage(timePeriod+1000)
        assertEquals(average.value, 20d)
        assertEquals(average.confidence, 0d)
    }
    
    @Test
    public void testSingleValueAverage() {
        averager.onEvent(intSensor.newEvent(producer, 10), 1000)
        average = averager.getAverage(1000)
        assertEquals(average.value, 10 /1d)
        assertEquals(average.confidence, 1d)
    }
    
    @Test
    public void testMonospacedAverage() {
        averager.onEvent(intSensor.newEvent(producer, 10), 1000)
        averager.onEvent(intSensor.newEvent(producer, 20), 1250)
        averager.onEvent(intSensor.newEvent(producer, 30), 1500)
        averager.onEvent(intSensor.newEvent(producer, 40), 1750)
        averager.onEvent(intSensor.newEvent(producer, 50), 2000)
        average = averager.getAverage(2000)
        assertEquals(average.value, (20+30+40+50)/4d)
        assertEquals(average.confidence, 1d)
    }
    
    @Test
    public void testWeightedAverage() {
        averager.onEvent(intSensor.newEvent(producer, 10), 1000)
        averager.onEvent(intSensor.newEvent(producer, 20), 1100)
        averager.onEvent(intSensor.newEvent(producer, 30), 1300)
        averager.onEvent(intSensor.newEvent(producer, 40), 1600)
        averager.onEvent(intSensor.newEvent(producer, 50), 2000)
        average = averager.getAverage(2000)
        assertEquals(average.value, (20*0.1d)+(30*0.2d)+(40*0.3d)+(50*0.4d))
        assertEquals(average.confidence, 1d)
    }
    
    @Test
    public void testConfidenceDecay() {
        averager.onEvent(intSensor.newEvent(producer, 10), 1000)
        averager.onEvent(intSensor.newEvent(producer, 20), 1250)
        averager.onEvent(intSensor.newEvent(producer, 30), 1500)
        averager.onEvent(intSensor.newEvent(producer, 40), 1750)
        averager.onEvent(intSensor.newEvent(producer, 50), 2000)
        
        average = averager.getAverage(2250)
        assertEquals(average.value, (30+40+50)/3d)
        assertEquals(average.confidence, 0.75d)
        average = averager.getAverage(2500)
        assertEquals(average.value, (40+50)/2d)
        assertEquals(average.confidence, 0.5d)
        average = averager.getAverage(2750)
        assertEquals(average.value, 50d)
        assertEquals(average.confidence, 0.25d)
        average = averager.getAverage(3000)
        assertEquals(average.value, 50d)
        assertEquals(average.confidence, 0d)
    }
}
