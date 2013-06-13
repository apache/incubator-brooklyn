package brooklyn.enricher

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.BasicGroup
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpecs
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

import com.google.common.base.Function

class CustomAggregatingEnricherTest {

    public static final Logger log = LoggerFactory.getLogger(CustomAggregatingEnricherTest.class);
            
    private static final long TIMEOUT_MS = 10*1000
    private static final long SHORT_WAIT_MS = 250
    
    TestApplication app
    TestEntity producer
    
    AttributeSensor<Integer> intSensor
    AttributeSensor<Integer> target

    @BeforeMethod(alwaysRun=true)
    public void before() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        producer = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor")
        target = new BasicAttributeSensor<Integer>(Long.class, "target sensor")
        
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
        List<TestEntity> producers = [
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class)), 
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class)),
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class))
                ]
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
        List<TestEntity> producers = [ 
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class))
                ]
        CustomAggregatingEnricher<Double> cae = CustomAggregatingEnricher.<Double>newAveragingEnricher(
                intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), producers:producers)
        producer.addEnricher(cae)
        assertEquals cae.getAggregate(), 0d
    }

    @Test
    public void testAveragingEnricherWhenNullSensorValue() {
        List<TestEntity> producers = [
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class))
                ]
        CustomAggregatingEnricher<Double> cae = CustomAggregatingEnricher.<Double>newAveragingEnricher(
                intSensor, new BasicAttributeSensor<Double>(Double.class, "target sensor"), producers:producers)
        producer.addEnricher(cae)
        
        cae.onEvent(intSensor.newEvent(producers[0], null))
        assertEquals cae.getAggregate(), 0d
    }

    @Test
    public void testMultipleProducersAverage() {
        List<TestEntity> producers = [
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class)), 
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class)),
                app.createAndManageChild(EntitySpecs.spec(TestEntity.class))
                ]
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
        TestEntity p1 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class)); 
        TestEntity p2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        
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
        try {
            BasicGroup group = app.createAndManageChild(EntitySpecs.spec(BasicGroup.class));
            TestEntity p1 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class))
            TestEntity p2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class))
            log.debug("created $group and the entities it will contain $p1 $p2")

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
        } catch (Exception e) {
            log.error("testAggregatesNewMembersOfGroup failed (now cleaning up): "+e)
            throw e;
        }
    }
    
    @Test(groups = "Integration")
    public void testAggregatesGroupMembersFiftyTimes() {
        for (int i=0; i<50; i++) {
            log.debug "testAggregatesNewMembersOfGroup $i"
            testAggregatesNewMembersOfGroup();
        }
    }
    
    @Test
    public void testAggregatesExistingMembersOfGroup() {
        BasicGroup group = app.addChild(EntitySpecs.spec(BasicGroup.class));
        TestEntity p1 = app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(TestEntity.class).parent(group)); 
        TestEntity p2 = app.getManagementContext().getEntityManager().createEntity(EntitySpecs.spec(TestEntity.class).parent(group)); 
        group.addMember(p1)
        group.addMember(p2)
        p1.setAttribute(intSensor, 1)
        Entities.manage(group);
        
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
        BasicGroup group = app.createAndManageChild(EntitySpecs.spec(BasicGroup.class));
        TestEntity p1 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        TestEntity p2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        TestEntity p3 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
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
        TestEntity p1 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class)); 
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
