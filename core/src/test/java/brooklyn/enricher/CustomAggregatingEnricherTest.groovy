package brooklyn.enricher

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import com.google.common.base.Function

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.TestUtils

class CustomAggregatingEnricherTest {

    private static final long TIMEOUT_MS = 10*1000
    private static final long SHORT_WAIT_MS = 250
    
    AbstractApplication app

    EntityLocal producer
    AttributeSensor<Integer> intSensor
    AttributeSensor<Integer> target

    @BeforeMethod()
    public void before() {
        app = new AbstractApplication() {}
        producer = new LocallyManagedEntity(owner:app)
        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor")
        target = new BasicAttributeSensor<Integer>(Integer.class, "target sensor")
        
        app.start([new SimulatedLocation()])
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        app?.stop()
    }
    
    @Test
    public void testEnrichersWithNoProducers() {
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(intSensor, target)
        producer.addEnricher(cae)
        assertEquals cae.getAggregate(), 0
    }

    @Test
    public void testSummingEnricherWhenNoSensorValuesYet() {
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(
                intSensor, target, producers:[producer])
        producer.addEnricher(cae)
        assertEquals cae.getAggregate(), 0
    }

    @Test
    public void testSummingEnricherWhenNullSensorValue() {
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(
                intSensor, target, producers:[producer])
        producer.addEnricher(cae)
        cae.onEvent(intSensor.newEvent(producer, null))
        assertEquals cae.getAggregate(), 0
    }
    
    @Test
    public void testSingleProducerSum() {
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(
                intSensor, target, producers:[producer])
        producer.addEnricher(cae)
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
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(
            intSensor, target, producers:producers)
        
        producer.addEnricher(cae)
        assertEquals cae.getAggregate(), 0
        cae.onEvent(intSensor.newEvent(producers[2], 1))
        assertEquals cae.getAggregate(), 1
        cae.onEvent(intSensor.newEvent(producers[0], 3))
        assertEquals cae.getAggregate(), 4
        cae.onEvent(intSensor.newEvent(producers[1], 3))
        assertEquals cae.getAggregate(), 7

    }
    
    @Test
    public void testAveragingEnricherWhenNoSensorValuesYet() {
        List<LocallyManagedEntity> producers = [
                [owner: app] as LocallyManagedEntity]
        CustomAggregatingEnricher<Double> cae = CustomAggregatingEnricher.<Double>newAveragingEnricher(
                intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), producers:producers)
        producer.addEnricher(cae)
        assertEquals cae.getAggregate(), 0d
    }

    @Test
    public void testAveragingEnricherWhenNullSensorValue() {
        List<LocallyManagedEntity> producers = [
                [owner: app] as LocallyManagedEntity]
        CustomAggregatingEnricher<Double> cae = CustomAggregatingEnricher.<Double>newAveragingEnricher(
                intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), producers:producers)
        producer.addEnricher(cae)
        
        cae.onEvent(intSensor.newEvent(producers[0], null))
        assertEquals cae.getAggregate(), 0d
    }

    @Test
    public void testMultipleProducersAverage() {
        List<LocallyManagedEntity> producers = [
                [owner: app] as LocallyManagedEntity,
                [owner: app] as LocallyManagedEntity,
                [owner: app] as LocallyManagedEntity]
        CustomAggregatingEnricher<Double> cae = CustomAggregatingEnricher.<Double>newAveragingEnricher(
                intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), producers:producers)
        
        producer.addEnricher(cae)
        
        assertEquals cae.getAggregate(), 0d
        cae.onEvent(intSensor.newEvent(producers[0], 3))
        assertEquals cae.getAggregate(), 1d
        
        cae.onEvent(intSensor.newEvent(producers[1], 3))
        assertEquals cae.getAggregate(), 2d
        
        cae.onEvent(intSensor.newEvent(producers[2], 6))
        assertEquals cae.getAggregate(), 4d

        // change p2's value to 7.5, average increase of 0.5.
        cae.onEvent(intSensor.newEvent(producers[2], 7.5))
        assertEquals cae.getAggregate(), 4.5d
    }
    
    @Test
    public void testAddingAndRemovingProducers() {
        LocallyManagedEntity p1 = [owner: app] 
        LocallyManagedEntity p2 = [owner: app]
        
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(
                intSensor, target, producers:[p1])

        producer.addEnricher(cae)
        assertEquals cae.getAggregate(), 0
        
        // Event by initial producer
        cae.onEvent(intSensor.newEvent(p1, 1))
        assertEquals cae.getAggregate(), 1
        
        // Add producer and fire event
        cae.addProducer(p2)
        cae.onEvent(intSensor.newEvent(p2, 4))
        assertEquals cae.getAggregate(), 5
        
        cae.removeProducer(p2)
        assertEquals cae.getAggregate(), 1
    }
    
    @Test
    public void testAggregatesNewMembersOfGroup() {
        AbstractGroup group = new AbstractGroup(owner:app) {}
        LocallyManagedEntity p1 = [owner: app] 
        LocallyManagedEntity p2 = [owner: app]
        
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(intSensor, target, allMembers:true)
        group.addEnricher(cae)
        
        assertEquals cae.getAggregate(), 0

        group.addMember(p1)
        p1.setAttribute(intSensor, 1)
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals cae.getAggregate(), 1
        }
        
        group.addMember(p2)
        p2.setAttribute(intSensor, 2)
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals cae.getAggregate(), 3
        }
        
        group.removeMember(p2)
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals cae.getAggregate(), 1
        }
    }
    
    @Test
    public void testAggregatesExistingMembersOfGroup() {
        AbstractGroup group = new AbstractGroup(owner:app) {}
        LocallyManagedEntity p1 = [owner: app] 
        LocallyManagedEntity p2 = [owner: app]
        group.addMember(p1)
        group.addMember(p2)
        p1.setAttribute(intSensor, 1)
        
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(intSensor, target, allMembers:true)
        group.addEnricher(cae)
        
        assertEquals cae.getAggregate(), 1

        p2.setAttribute(intSensor, 2)
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals cae.getAggregate(), 3
        }
        
        group.removeMember(p2)
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals cae.getAggregate(), 1
        }
    }
    
    @Test
    public void testAppliesFilterWhenAggregatingMembersOfGroup() {
        AbstractGroup group = new AbstractGroup(owner:app) {}
        LocallyManagedEntity p1 = [owner: app] 
        LocallyManagedEntity p2 = [owner: app]
        LocallyManagedEntity p3 = [owner: app]
        group.addMember(p1)
        group.addMember(p2)
        p1.setAttribute(intSensor, 1)
        p2.setAttribute(intSensor, 2)
        p3.setAttribute(intSensor, 4)
        
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newSummingEnricher(intSensor, target, allMembers:true, filter:{it == p1})
        group.addEnricher(cae)
        
        assertEquals cae.getAggregate(), 1
        
        group.addMember(p3)
        TestUtils.assertSucceedsContinually(timeout:SHORT_WAIT_MS) {
            assertEquals cae.getAggregate(), 1
        }
    }
    
    @Test
    public void testCustomAggregatingFunction() {
        LocallyManagedEntity p1 = [owner: app] 
        Function<Collection<Integer>,Integer> aggregator = { Collection c -> 
            int result = 0; c.each { result += it*it }; return result;
        } as Function
         
        CustomAggregatingEnricher<Integer> cae = CustomAggregatingEnricher.<Integer>newEnricher(
                intSensor, target, aggregator, 0, producers:[p1])

        producer.addEnricher(cae)
        assertEquals cae.getAggregate(), 0
        
        // Event by producer
        cae.onEvent(intSensor.newEvent(p1, 2))
        assertEquals cae.getAggregate(), 4
    }
}
