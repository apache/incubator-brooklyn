package brooklyn.policy

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicSensor

class CustomAggregatingEnricherTest {

    AbstractApplication app

    EntityLocal producer
    Sensor<Integer> intSensor
    Sensor<Integer> target

    @BeforeMethod()
    public void before() {
        app = new AbstractApplication() {}
        producer = new LocallyManagedEntity(owner:app)
        intSensor = new BasicSensor<Integer>(Integer.class, "int sensor")
        target = new BasicSensor<Integer>(Integer.class, "target sensor")
    }

    @Test
    public void testSingleProducerSum() {
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>getSummingEnricher([producer],
                intSensor, target)
        producer.addPolicy(cae)
        assertEquals cae.getAggregate(), 0
        cae.onEvent(intSensor.newEvent(producer, 1))
        assertEquals cae.getAggregate(), 1
    }
    
    @Test
    public void testMultipleProducersSum() {
        List<LocallyManagedEntity> producers = [
                [owner: app] as LocallyManagedEntity,
                [owner: app] as LocallyManagedEntity,
                [owner: app] as LocallyManagedEntity]
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>getSummingEnricher(producers,
            intSensor, target)
        
        producer.addPolicy(cae)
        assertEquals cae.getAggregate(), 0
        cae.onEvent(intSensor.newEvent(producers[2], 1))
        assertEquals cae.getAggregate(), 1
        cae.onEvent(intSensor.newEvent(producers[0], 3))
        assertEquals cae.getAggregate(), 4
        cae.onEvent(intSensor.newEvent(producers[1], 3))
        assertEquals cae.getAggregate(), 7

    }
    
    @Test
    public void testMultipleProducersAverage() {
        List<LocallyManagedEntity> producers = [
                [owner: app] as LocallyManagedEntity,
                [owner: app] as LocallyManagedEntity,
                [owner: app] as LocallyManagedEntity]
        CustomAggregatingEnricher<Double> cae = CustomAggregatingEnricher.<Double>getAveragingEnricher(producers,
            intSensor, new BasicSensor<Double>(Double.class, "target sensor"))
        
        producer.addPolicy(cae)
        
        assertEquals cae.getAggregate(), 0d
        cae.onEvent(intSensor.newEvent(producers[0], 3))
        assertEquals cae.getAggregate(), 1d
        
        cae.onEvent(intSensor.newEvent(producers[1], 3))
        assertEquals cae.getAggregate(), 2d
        
        cae.onEvent(intSensor.newEvent(producers[2], 6))
        assertEquals cae.getAggregate(), 4d

    }
    
    @Test
    public void testAddingAndRemovingProducers() {
        LocallyManagedEntity p1 = [owner: app] 
        LocallyManagedEntity p2 = [owner: app]
        
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>getSummingEnricher([p1],
                intSensor, target)

        producer.addPolicy(cae)
        assertEquals cae.getAggregate(), 0
        
        // Event by initial producer
        cae.onEvent(intSensor.newEvent(p1, 1))
        assertEquals cae.getAggregate(), 1
        
        // Add producer and fire event
        cae.addProducer(p2)
        cae.onEvent(intSensor.newEvent(p2, 4))
        assertEquals cae.getAggregate(), 5
        
    }
}
