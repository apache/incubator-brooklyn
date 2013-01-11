package brooklyn.entity.group

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.test.TestUtils
import brooklyn.test.entity.BlockingEntity
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.test.entity.TestEntityImpl
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
        app.startManagement();
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
        DynamicFabric fabric = new DynamicFabric(factory:{ properties -> return new TestEntityImpl(properties) }, app)
        app.manage(fabric);
        app.start(locs)
        
        assertEquals(fabric.children.size(), locs.size(), Joiner.on(",").join(fabric.children))
        fabric.children.each {
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
                factory:{ properties, parent -> 
                        def result = new TestEntityImpl(properties, parent)
                        entitiesAdded.add(result)
                        result },
                app)
        app.manage(fabric);
        
        app.start([loc1,loc2])
        
        assertEquals(entitiesAdded.size(), 2)
        assertEquals(entitiesAdded as Set, fabric.children as Set)
    }
    
    @Test
    public void testSizeEnricher() {
        Collection<Location> locs = [ new SimulatedLocation(), new SimulatedLocation(), new SimulatedLocation() ]
        DynamicFabric fabric = new DynamicFabric(factory:{ fabricProperties, parent ->
            return new DynamicCluster(parent:parent, initialSize:0,
                factory:{ clusterProperties -> return new TestEntityImpl(clusterProperties) })
            }, app)
        app.manage(fabric);
        app.start(locs)
        
        int i = 0, total = 0
        
        assertEquals(fabric.children.size(), locs.size(), Joiner.on(",").join(fabric.children))
        fabric.children.each { Cluster child ->
            total += ++i
            child.resize(i)
        }
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(fabric.getAttribute(DynamicFabric.FABRIC_SIZE), total)
        }
    }
    
    @Test
    public void testDynamicFabricStartsEntitiesInParallel() {
        List<CountDownLatch> latches = [] as CopyOnWriteArrayList<CountDownLatch>
        DynamicFabric fabric = new DynamicFabric(
                factory:{ properties -> 
                        CountDownLatch latch = new CountDownLatch(1); 
                        latches.add(latch); 
                        return new BlockingEntity(properties, latch) 
                }, 
                app)
        app.manage(fabric);
        Collection<Location> locs = [ loc1, loc2 ]
        
        Task task = fabric.invoke(Startable.START, [ locations:locs ])

        new Repeater("Wait until each task is executing")
                .repeat()
                .every(100 * MILLISECONDS)
                .limitTimeTo(30 * SECONDS)
                .until { latches.size() == locs.size() }
                .run()

        assertFalse(task.isDone())
        
        latches.each { it.countDown() }
               
        new Repeater("Wait until complete")
                .repeat()
                .every(100 * MILLISECONDS)
                .limitTimeTo(30 * SECONDS)
                .until { task.isDone() }
                .run()

        assertEquals(fabric.children.size(), locs.size(), Joiner.on(",").join(fabric.children))
                
        fabric.children.each {
            assertEquals(it.counter.get(), 1)
        }
    }

    @Test(groups="Integration")
    public void testDynamicFabricStopsEntitiesInParallelManyTimes() {
        for (int i=0; i<100; i++) {
            log.info("running testDynamicFabricStopsEntitiesInParallel iteration $i");
            testDynamicFabricStopsEntitiesInParallel();
        }
    }
    
    @Test
    public void testDynamicFabricStopsEntitiesInParallel() {
        List<CountDownLatch> shutdownLatches = [] as CopyOnWriteArrayList<CountDownLatch>
        List<CountDownLatch> executingShutdownNotificationLatches = [] as CopyOnWriteArrayList<CountDownLatch>
        DynamicFabric fabric = new DynamicFabric(
                factory:{ properties -> 
                        CountDownLatch shutdownLatch = new CountDownLatch(1); 
                        CountDownLatch executingShutdownNotificationLatch = new CountDownLatch(1); 
                        shutdownLatches.add(shutdownLatch);
                        executingShutdownNotificationLatches.add(executingShutdownNotificationLatch)
                        return new BlockingEntity.Builder(properties)
                                .shutdownLatch(shutdownLatch)
                                .executingShutdownNotificationLatch(executingShutdownNotificationLatch)
                                .build() 
                }, 
                app)
        app.manage(fabric);
        Collection<Location> locs = [ loc1, loc2 ]
        
        // Start the fabric (and check we have the required num things to concurrently stop)
        fabric.start(locs)
        
        assertEquals(shutdownLatches.size(), locs.size())
        assertEquals(executingShutdownNotificationLatches.size(), locs.size())
        assertEquals(fabric.children.size(), locs.size())
        Collection<BlockingEntity> children = fabric.children
        
        // On stop, expect each child to get as far as blocking on its latch
        Task task = fabric.invoke(Startable.STOP)

        executingShutdownNotificationLatches.each {
            assertTrue(it.await(10*1000, TimeUnit.MILLISECONDS))
        }
        assertFalse(task.isDone())
        
        // When we release the latches, expect shutdown to complete
        shutdownLatches.each { it.countDown() }
        
        executeUntilSucceeds(timeout:10*1000) {
            task.isDone()
        }

        executeUntilSucceeds(timeout:10*1000) {
            fabric.children.each {
                def count = it.counter.get();
                assertEquals(count, 0, "$it counter reports $count")
            }
        }
    }
    
    @Test
    public void testDynamicFabricDoesNotAcceptUnstartableChildren() {
        DynamicFabric fabric = new DynamicFabric(
                factory:{ properties -> return new AbstractEntity(properties) {} }, 
                app)
        app.manage(fabric);
        
        try {
            fabric.start([loc1])
            assertEquals(fabric.children.size(), 1)
        } catch (ExecutionException e) {
            Throwable unwrapped = unwrapThrowable(e)
            if (unwrapped instanceof IllegalStateException && (unwrapped.getMessage()?.contains("is not Startable"))) {
                // success
            } else {
                throw e
            }
        }
    }
    
    // For follow-the-sun, a valid pattern is to associate the FollowTheSunModel as a child of the dynamic-fabric.
    // Thus we have "unstoppable" entities. Let's be relaxed about it, rather than blowing up.
    @Test
    public void testDynamicFabricIgnoresExtraUnstoppableChildrenOnStop() {
        DynamicFabric fabric = new DynamicFabric(
                factory:{ properties -> return new TestEntityImpl(properties) }, 
                app)
        app.manage(fabric);
        fabric.start([loc1])
        
        AbstractEntity extraChild = new AbstractEntity(parent:fabric) {}
        
        fabric.stop()
    }
    
	@Test
    public void testDynamicFabricPropagatesProperties() {
		Closure entityFactory = { properties -> 
            def entityProperties = properties + [b: "avail"]
            return new TestEntityImpl(entityProperties) 
        }
        Closure clusterFactory = { properties -> 
            def clusterProperties = properties + [factory:entityFactory, a: "ignored"]
            new DynamicCluster(clusterProperties) {
                protected Map getCustomChildFlags() { [fromCluster: "passed to base entity"] }
            }
        }
		DynamicFabric fabric = new DynamicFabric(factory:clusterFactory, app) {
            protected Map getCustomChildFlags() { [fromFabric: "passed to cluster but not base entity"] }
        }
        //available through inheritance (as a PortRange)
        fabric.setConfig(Attributes.HTTP_PORT, 1234)
        app.manage(fabric);
        
		app.start([ new SimulatedLocation() ])
        
		assertEquals(fabric.children.size(), 1)
		assertEquals(fabric.children[0].children.size(), 1)
		assertEquals(fabric.children[0].children[0].getConfig(Attributes.HTTP_PORT)?.toString(), "1234")
		assertEquals(fabric.children[0].children[0].constructorProperties.a, null)
		assertEquals(fabric.children[0].children[0].constructorProperties.b, "avail")
		assertEquals(fabric.children[0].children[0].constructorProperties.fromCluster, "passed to base entity")
		assertEquals(fabric.children[0].children[0].constructorProperties.fromFabric, null)
        
        fabric.children[0].resize(2)
        assertEquals(fabric.children[0].children[1].getConfig(Attributes.HTTP_PORT)?.toString(), "1234")
        assertEquals(fabric.children[0].children[1].constructorProperties.a, null)
        assertEquals(fabric.children[0].children[1].constructorProperties.b, "avail")
        assertEquals(fabric.children[0].children[1].constructorProperties.fromCluster, "passed to base entity")
        assertEquals(fabric.children[0].children[1].constructorProperties.fromFabric, null)
	}
}
