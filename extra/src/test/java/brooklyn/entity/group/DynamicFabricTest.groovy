package brooklyn.entity.group

import java.util.Collection
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.management.Task
import brooklyn.test.entity.BlockingEntity
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.Repeater

class DynamicFabricTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamicFabricTest)
    
    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenSingleLocation() {
        Collection<Location> locs = [new GeneralPurposeLocation()]
        runWithLocations(locs)
    }

    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenManyLocations() {
        Collection<Location> locs = [new GeneralPurposeLocation(), new GeneralPurposeLocation(), new GeneralPurposeLocation()]
        runWithLocations(locs)
    }
    
    private void runWithLocations(Collection<Location> locs) {
        Application app = new AbstractApplication() {}
        DynamicFabric fabric = new DynamicFabric(newEntity:{ properties -> return new TestEntity(properties) }, app)
        
        fabric.start(locs)
        
        Assert.assertEquals(fabric.ownedChildren.size(), locs.size(), ""+fabric.ownedChildren)
        fabric.ownedChildren.each {
            TestEntity child = it
            Assert.assertEquals(child.counter.get(), 1)
            Assert.assertEquals(child.locations.size(), 1, ""+child.locations)
            Assert.assertTrue(locs.removeAll(child.locations))
        }
        Assert.assertTrue(locs.isEmpty(), ""+locs)
    }
    
    @Test
    public void testDynamicFabricStartsEntitiesInParallel() {
        List<CountDownLatch> startupLatches = [] as CopyOnWriteArrayList<CountDownLatch>
        Application app = new AbstractApplication() {}
        DynamicFabric fabric = new DynamicFabric(
                newEntity:{ properties -> 
                        CountDownLatch latch = new CountDownLatch(1); 
                        startupLatches.add(latch); 
                        return new BlockingEntity(properties, latch) 
                }, 
                app)
        Collection<Location> locs = [new GeneralPurposeLocation(), new GeneralPurposeLocation()]
        
        Task task = fabric.invoke(Startable.START, [locations:locs])

        new Repeater("Wait until each task is executing")
                .repeat( {} )
                .every(100, TimeUnit.MILLISECONDS)
                .limitTimeTo(30, TimeUnit.SECONDS)
                .until( {startupLatches.size() == locs.size()} )
                .run()

        Assert.assertFalse(task.isDone())
        
        startupLatches.each { it.countDown() }
               
        new Repeater("Wait until complete")
                .repeat( {} )
                .every(100, TimeUnit.MILLISECONDS)
                .limitTimeTo(30, TimeUnit.SECONDS)
                .until( {task.isDone()} )
                .run()

        Assert.assertEquals(fabric.ownedChildren.size(), locs.size(), ""+fabric.ownedChildren)
                
        fabric.ownedChildren.each {
            Assert.assertEquals(it.counter.get(), 1)
        }
    }
}
