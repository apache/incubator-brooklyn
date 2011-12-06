package brooklyn.entity.group

import static org.testng.AssertJUnit.*

import java.util.Collection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Changeable
import brooklyn.event.EntityStartException
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.test.TestUtils
import brooklyn.test.entity.NoopStartable
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.TimeExtras

class DynamicClusterTest {
    
    static { TimeExtras.init() }
    
    @Test(expectedExceptions = [IllegalArgumentException, NullPointerException])
    public void constructorRequiresThatNewEntityArgumentIsGiven() {
        new DynamicCluster(initialSize:1, new TestApplication())
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatNewEntityArgumentIsAnEntity() {
        new DynamicCluster([ initialSize:1, newEntity:new NoopStartable() ],
            new TestApplication()
        )
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatNewEntityArgumentIsStartable() {
        new DynamicCluster([ initialSize:1, newEntity:new AbstractEntity() { } ], new TestApplication())
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatPostStartEntityIsClosure() {
        new DynamicCluster([ initialSize:1, newEntity:{ new TestEntity() }, postStartEntity:"notaclosure" ], new TestApplication()
        )
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void startMethodFailsIfLocationsParameterIsMissing() {
        DynamicCluster cluster = new DynamicCluster(newEntity:{ new TestEntity() }, new TestApplication())
        cluster.start(null)
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void startMethodFailsIfLocationsParameterIsEmpty() {
        DynamicCluster cluster = new DynamicCluster(newEntity:{ new TestEntity() }, new TestApplication())
        cluster.start([])
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void startMethodFailsIfLocationsParameterHasMoreThanOneElement() {
        DynamicCluster cluster = new DynamicCluster(newEntity:{ new TestEntity() }, new TestApplication())
        cluster.start([ new GeneralPurposeLocation(), new GeneralPurposeLocation() ])
        fail "Did not throw expected exception"
    }

    @Test
    public void resizeFromZeroToOneStartsANewEntityAndSetsItsOwner() {
        Collection<Location> locations = [new GeneralPurposeLocation()]
        TestEntity entity
        Application app = new TestApplication()
        DynamicCluster cluster = new DynamicCluster(newEntity:{ properties -> entity = new TestEntity(properties) }, app)
        cluster.start(locations)

        cluster.resize(1)
        assertEquals 1, entity.counter.get()
        assertEquals cluster, entity.owner
        assertEquals app, entity.application
    }

    @Test
    public void currentSizePropertyReflectsActualClusterSize() {
        List<Location> locations = [ new GeneralPurposeLocation() ]

        Application app = new AbstractApplication() { }
        DynamicCluster cluster = new DynamicCluster(newEntity:{ properties -> new TestEntity(properties) }, app)
        assertEquals 0, cluster.currentSize

        cluster.start(locations)
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
        List<Location> locations = [ new GeneralPurposeLocation() ]
        Application app = new TestApplication()
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> new TestEntity(properties) }, initialSize:2 ], app)
        cluster.start(locations)
        assertEquals cluster.currentSize, 2
        assertEquals cluster.members.size(), 2
        assertEquals cluster.getAttribute(Changeable.GROUP_SIZE), 2
    }

    @Test
    public void clusterLocationIsPassedOnToEntityStart() {
        Collection<Location> locations = [ new GeneralPurposeLocation() ]
        Application app = new TestApplication()
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
        Application app = new TestApplication()
        TestEntity entity
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> entity = new TestEntity(properties) }, initialSize:1 ], app)
        cluster.start([new GeneralPurposeLocation()])
        assertEquals 1, cluster.currentSize
        assertEquals 1, entity.counter.get()
        cluster.resize(0)
        assertEquals 0, cluster.currentSize
        assertEquals 0, entity.counter.get()
    }

    @Test(enabled = false)
    public void stoppingTheClusterStopsTheEntity() {
        Application app = new TestApplication()
        TestEntity entity
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> entity = new TestEntity(properties) }, initialSize:1 ], app)
        cluster.start([new GeneralPurposeLocation()])
        assertEquals 1, entity.counter.get()
        cluster.stop()
        assertEquals 0, entity.counter.get()
    }
    
    /**
     * This tests the fix for ENGR-1826.
     */
    @Test
    public void failingEntitiesDontBreakClusterActions() {
        Application app = new TestApplication()
        TestEntity entity
        final int failNum = 2
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                }, initialSize:0 ], app)
        
        cluster.start([new GeneralPurposeLocation()])
        cluster.resize(3)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.ownedChildren.size(), 2)
        cluster.ownedChildren.each {
            assertEquals(((FailingEntity)it).fail, false)
        }
    }
    
    @Test
    public void shutsDownNewestFirstWhenResizing() {
        Application app = new TestApplication()
        TestEntity entity
        final int failNum = 2
        final List<Entity> creationOrder = []
        DynamicCluster cluster = new DynamicCluster([ newEntity:{ properties -> 
                    Entity result = new TestEntity(properties)
                    creationOrder << result
                    return result
                }, initialSize:0 ], app)
        
        cluster.start([new GeneralPurposeLocation()])
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
                ], new TestApplication())
        
        cluster.start([new GeneralPurposeLocation()])
        
        TestUtils.executeUntilSucceeds(timeout:2*TimeUnit.SECONDS) {
            assertEquals(called.size(), 2)
            assertEquals(called, created)
        }
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
