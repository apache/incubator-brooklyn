package brooklyn.entity.group

import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test
import org.testng.annotations.BeforeMethod

import brooklyn.entity.Entity
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.test.TestUtils
import brooklyn.test.entity.BlockingEntity
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Joiner

class DynamicFabricTest {
    private static final Logger logger = LoggerFactory.getLogger(DynamicFabricTest)

    private static final int TIMEOUT_MS = 5*1000
    
    static { TimeExtras.init() }
    
    TestApplication app
    Location loc1
    Location loc2
    Location loc3
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = new TestApplication()
        loc1 = new SimulatedLocation()
        loc2 = new SimulatedLocation()
        loc3 = new SimulatedLocation()
    }
    
    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenSingleLocation() {
        runWithLocations([loc1])
    }

    @Test
    public void testDynamicFabricCreatesAndStartsEntityWhenGivenManyLocations() {
        runWithLocations([loc1,loc2,loc3])
    }
    
    private void runWithLocations(Collection<Location> locs) {
        DynamicFabric fabric = new DynamicFabric(newEntity:{ properties -> return new TestEntity(properties) }, app)
        app.start(locs)
        
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
    public void testNotifiesPostStartListener() {
        List<Entity> entitiesAdded = new CopyOnWriteArrayList<Entity>()
        
        DynamicFabric fabric = new DynamicFabric(
                newEntity:{ properties -> return new TestEntity(properties) },
                postStartEntity:{ Entity e -> entitiesAdded.add(e) },
                app)
        
        app.start([loc1,loc2])
        
        assertEquals(entitiesAdded.size(), 2)
        assertEquals(entitiesAdded as Set, fabric.ownedChildren as Set)
    }
    
    @Test
    public void testSizeEnricher() {
        Collection<Location> locs = [ new SimulatedLocation(), new SimulatedLocation(), new SimulatedLocation() ]
        DynamicFabric fabric = new DynamicFabric(newEntity:{ fabricProperties, owner ->
            return new DynamicCluster(owner:owner, initialSize:0,
                newEntity:{ clusterProperties -> return new TestEntity(clusterProperties) })
            }, app)
        
        app.start(locs)
        
        int i = 0, total = 0
        
        assertEquals(fabric.ownedChildren.size(), locs.size(), Joiner.on(",").join(fabric.ownedChildren))
        fabric.ownedChildren.each { Cluster child ->
            total += ++i
            child.resize(i)
        }
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(fabric.getAttribute(DynamicFabric.FABRIC_SIZE), total)
        }
    }
    
    @Test
    public void testDynamicFabricStartsEntitiesInParallel() {
        List<CountDownLatch> startupLatches = [] as CopyOnWriteArrayList<CountDownLatch>
        DynamicFabric fabric = new DynamicFabric(
                newEntity:{ properties -> 
                        CountDownLatch latch = new CountDownLatch(1); 
                        startupLatches.add(latch); 
                        return new BlockingEntity(properties, latch) 
                }, 
                app)
        Collection<Location> locs = [ loc1, loc2 ]
        
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
	
	@Test
    public void testDynamicFabricPropagatesProperties() {
		Closure entityFactory = { properties -> return new TestEntity(properties) }
        Closure clusterFactory = { properties -> 
            def clusterProperties = properties + [initialSize:1, newEntity:entityFactory]
            new DynamicCluster(clusterProperties)
        }
		DynamicFabric fabric = new DynamicFabric(initialSize:1, httpPort: 8080, newEntity:clusterFactory, app)
		
		app.start([ new SimulatedLocation() ])
        
		assertEquals(fabric.ownedChildren.size(), 1)
		assertEquals(fabric.ownedChildren[0].ownedChildren.size(), 1)
		assertEquals(fabric.ownedChildren[0].ownedChildren[0].constructorProperties.httpPort, 8080)
        
        fabric.ownedChildren[0].resize(2)
        assertEquals(fabric.ownedChildren[0].ownedChildren[1].constructorProperties.httpPort, 8080)
	}
}
