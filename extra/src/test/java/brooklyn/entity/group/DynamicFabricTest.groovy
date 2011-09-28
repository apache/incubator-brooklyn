package brooklyn.entity.group

import static org.testng.Assert.*
import static java.util.concurrent.TimeUnit.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import com.google.common.base.Joiner;

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.management.Task
import brooklyn.test.entity.BlockingEntity
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

class DynamicFabricTest {
    private static final Logger logger = LoggerFactory.getLogger(DynamicFabricTest)

    static { TimeExtras.init() }
    
    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenSingleLocation() {
        Collection<Location> locs = [ new GeneralPurposeLocation() ]
        runWithLocations(locs)
    }

    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenManyLocations() {
        Collection<Location> locs = [ new GeneralPurposeLocation(), new GeneralPurposeLocation(), new GeneralPurposeLocation() ]
        runWithLocations(locs)
    }
    
    private void runWithLocations(Collection<Location> locs) {
        Application app = new AbstractApplication() {}
        DynamicFabric fabric = new DynamicFabric(newEntity:{ properties -> return new TestEntity(properties) }, app)
        
        fabric.start(locs)
        
        assertEquals(fabric.ownedChildren.size(), locs.size(), Joiner.on(",").join(fabric.ownedChildren))
        fabric.ownedChildren.each {
            TestEntity child = it
            assertEquals(child.counter.get(), 1)
            assertEquals(child.locations.size(), 1, Joiner.on(",").join(child.locations))
            assertTrue(locs.removeAll(child.locations))
        }
        assertTrue(locs.isEmpty(), Joiner.on(",").join(locs))
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
        Collection<Location> locs = [ new GeneralPurposeLocation(), new GeneralPurposeLocation() ]
        
        Task task = fabric.invoke(Startable.START, [ locations:locs ])

        new Repeater("Wait until each task is executing")
                .repeat()
                .every(100 * MILLISECONDS)
                .limitTimeTo(30 * SECONDS)
                .until { startupLatches.size() == locs.size() }
                .run()

        assertFalse(task.isDone())
        
        startupLatches.each { it.countDown() }
               
        new Repeater("Wait until complete")
                .repeat()
                .every(100 * MILLISECONDS)
                .limitTimeTo(30 * SECONDS)
                .until { task.isDone() }
                .run()

        assertEquals(fabric.ownedChildren.size(), locs.size(), Joiner.on(",").join(fabric.ownedChildren))
                
        fabric.ownedChildren.each {
            assertEquals(it.counter.get(), 1)
        }
    }
}
