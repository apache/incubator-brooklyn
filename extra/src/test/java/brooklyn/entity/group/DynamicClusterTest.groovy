package brooklyn.entity.group

import static org.testng.AssertJUnit.*

import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

class DynamicClusterTest {
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatNewEntityArgumentIsGiven() {
        new DynamicCluster(initialSize:1, new TestApplication())
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatNewEntityArgumentIsAnEntity() {
        new DynamicCluster([ initialSize:1,
            newEntity:new Startable() {
                void start(Collection<? extends Location> loc) { };
                void stop() { }
                void restart() { }
            } ],
            new TestApplication()
        )
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void constructorRequiresThatNewEntityArgumentIsStartable() {
        new DynamicCluster([ initialSize:1, newEntity:new AbstractEntity() { } ], new TestApplication())
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
}
