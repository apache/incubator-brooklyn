package brooklyn.entity.group

import static org.testng.AssertJUnit.*

import java.util.Collection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Changeable
import brooklyn.entity.trait.Resizable
import brooklyn.event.EntityStartException
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.test.TestUtils
import brooklyn.test.entity.NoopStartable
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.Iterables

class DynamicClusterTest {
    
    static { TimeExtras.init() }
    
    TestApplication app
    SimulatedLocation loc
    SimulatedLocation loc2
    
    @BeforeMethod
    public void setUp() {
        app = new TestApplication()
        loc = new SimulatedLocation()
        loc2 = new SimulatedLocation()
    }
    
    @Test(expectedExceptions = [IllegalArgumentException, NullPointerException])
    public void constructorRequiresThatNewEntityArgumentIsGiven() {
        new DynamicCluster(initialSize:1, app)
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatNewEntityArgumentIsAnEntity() {
        new DynamicCluster([ initialSize:1, newEntity:new NoopStartable() ], app)
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatNewEntityArgumentIsStartable() {
        new DynamicCluster([ initialSize:1, newEntity:new AbstractEntity() { } ], app)
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatPostStartEntityIsClosure() {
        new DynamicCluster([ initialSize:1, newEntity:{ new TestEntity() }, postStartEntity:"notaclosure" ], app)
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void startMethodFailsIfLocationsParameterIsMissing() {
        DynamicCluster cluster = new DynamicCluster(newEntity:{ new TestEntity() }, app)
        cluster.start(null)
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void startMethodFailsIfLocationsParameterIsEmpty() {
        DynamicCluster cluster = new DynamicCluster(newEntity:{ new TestEntity() }, app)
        cluster.start([])
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void startMethodFailsIfLocationsParameterHasMoreThanOneElement() {
        DynamicCluster cluster = new DynamicCluster(newEntity:{ new TestEntity() }, app)
        cluster.start([ loc, loc2 ])
        fail "Did not throw expected exception"
    }

    @Test
    public void resizeFromZeroToOneStartsANewEntityAndSetsItsOwner() {
        TestEntity entity
        DynamicCluster cluster = new DynamicCluster(newEntity:{ properties -> entity = new TestEntity(properties) }, app)
        cluster.start([loc])

        cluster.resize(1)
        assertEquals 1, entity.counter.get()
        assertEquals cluster, entity.owner
        assertEquals app, entity.application
    }

    @Test
    public void currentSizePropertyReflectsActualClusterSize() {
        Application app = new AbstractApplication() { }
        DynamicCluster cluster = new DynamicCluster(newEntity:{ properties -> new TestEntity(properties) }, app)
        assertEquals 0, cluster.currentSize

        cluster.start([loc])
        assertEquals 0, cluster.currentSize
        assertEquals 0, cluster.getAttribute(Changeable.GROUP_SIZE)

        int newSize = cluster.resize(1)
        assertEquals newSize, 1
        assertEquals newSize, cluster.currentSize
        assertEquals newSize, cluster.members.size()
        assertEquals newSize, cluster.getAttribute(Changeable.GROUP_SIZE)

        newSize = cluster.resize(4)
        assertEquals newSize, 4
        assertEquals newSize, cluster.currentSize
        assertEquals newSize, cluster.members.size()
        assertEquals newSize, cluster.getAttribute(Changeable.GROUP_SIZE)
    }

    @Test
    public void clusterSizeAfterStartIsInitialSize() {
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> new TestEntity(properties) }, initialSize:2 ], app)
        cluster.start([loc])
        assertEquals cluster.currentSize, 2
        assertEquals cluster.members.size(), 2
        assertEquals cluster.getAttribute(Changeable.GROUP_SIZE), 2
    }

    @Test
    public void clusterLocationIsPassedOnToEntityStart() {
        Collection<Location> locations = [ loc ]
        TestEntity entity
        def newEntity = { properties ->
            entity = new TestEntity(owner:app) {
	            List<Location> stashedLocations = null
	            @Override
	            void start(Collection<? extends Location> loc) {
	                super.start(loc)
	                stashedLocations = loc
	            }
	        }
        }
        DynamicCluster cluster = new DynamicCluster([ newEntity:newEntity, initialSize:1 ], app)
        cluster.start(locations)

        assertNotNull entity.stashedLocations
        assertEquals 1, entity.stashedLocations.size()
        assertEquals locations[0], entity.stashedLocations[0]
    }

    @Test
    public void resizeFromOneToZeroChangesClusterSize() {
        TestEntity entity
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> entity = new TestEntity(properties) }, initialSize:1 ], app)
        cluster.start([loc])
        assertEquals 1, cluster.currentSize
        assertEquals 1, entity.counter.get()
        cluster.resize(0)
        assertEquals 0, cluster.currentSize
        assertEquals 0, entity.counter.get()
    }

    @Test(enabled = false)
    public void stoppingTheClusterStopsTheEntity() {
        TestEntity entity
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> entity = new TestEntity(properties) }, initialSize:1 ], app)
        cluster.start([loc])
        assertEquals 1, entity.counter.get()
        cluster.stop()
        assertEquals 0, entity.counter.get()
    }
    
    /**
     * This tests the fix for ENGR-1826.
     */
    @Test
    public void failingEntitiesDontBreakClusterActions() {
        TestEntity entity
        final int failNum = 2
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                }, initialSize:0 ], app)
        
        cluster.start([loc])
        cluster.resize(3)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.ownedChildren.size(), 2)
        cluster.ownedChildren.each {
            assertEquals(((FailingEntity)it).fail, false)
        }
    }
    
    @Test
    public void shutsDownNewestFirstWhenResizing() {
        TestEntity entity
        final int failNum = 2
        final List<Entity> creationOrder = []
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> 
                    Entity result = new TestEntity(properties)
                    creationOrder << result
                    return result
                }, initialSize:0 ], app)
        
        cluster.start([loc])
        cluster.resize(1)
        cluster.resize(2)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.ownedChildren as List, creationOrder)
        
        // Now stop one
        cluster.resize(1)
        assertEquals(cluster.currentSize, 1)
        assertEquals(cluster.ownedChildren, creationOrder.subList(0, 1))
    }
    
    @Test
    public void postStartEntityCalledForEachEntity() {
        final Set<Entity> created = [] as Set
        final Set<Entity> called = [] as Set
        DynamicCluster cluster = new DynamicCluster([ 
                        newEntity:{ def result = new TestEntity(); created.add(result); return result },
                        postStartEntity:{ entity -> called.add(entity) },
                        initialSize:2
                ], app)
        
        cluster.start([loc])
        
        TestUtils.executeUntilSucceeds(timeout:2*TimeUnit.SECONDS) {
            assertEquals(called.size(), 2)
            assertEquals(called, created)
        }
    }
    
    @Test
    public void resizeLoggedAsEffectorCall() {
        Resizable cluster = new DynamicCluster(newEntity:{ properties -> return new TestEntity(properties) }, app)
        app.start([loc])
        cluster.resize(1)
        
        Set<Task> tasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags([cluster,"EFFECTOR"])
        assertEquals(tasks.size(), 2)
        assertTrue(Iterables.get(tasks, 0).getDescription().contains("start"))
        assertTrue(Iterables.get(tasks, 1).getDescription().contains("resize"))
        
    }
}

class FailingEntity extends TestEntity {
    boolean fail
    
    FailingEntity(Map flags, boolean fail) {
        super(flags)
        this.fail = fail
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        if (fail) {
            throw new EntityStartException("Simulating entity start failure for test")
        }
    }
}
