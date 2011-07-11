package brooklyn.entity.group

import org.testng.annotations.Test
import brooklyn.entity.trait.Startable
import brooklyn.entity.basic.AbstractEntity
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import org.gmock.GMockTestCase
import brooklyn.util.task.BasicExecutionManager
import brooklyn.management.ExecutionContext
import brooklyn.util.task.BasicExecutionContext
import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.trait.ResizeResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class DynamicClusterTest extends GMockTestCase {

    @Test
    public void constructorRequiresThatNewEntityArgumentIsGiven() {
        try {
            new DynamicCluster(initialSize: 1, new TestApplication())
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatNewEntityArgumentIsAnEntity() {
        try {
            new DynamicCluster(initialSize: 1,
                newEntity: new Startable(){
                    void start(Collection<? extends Location> loc) {};
                    void stop() {}
                },
                new TestApplication()
            )
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatNewEntityArgumentIsStartable() {
        try {
            new DynamicCluster(initialSize: 1, newEntity: new AbstractEntity(){}, new TestApplication())
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatInitialSizeArgumentIsGiven() {
        try {
            new DynamicCluster(newEntity: {new TestEntity()}, new TestApplication())
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatInitialSizeArgumentIsAnInteger() {
        try {
            new DynamicCluster(newEntity: {new TestEntity()}, initialSize: "foo", new TestApplication())
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterIsMissing() {
        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0, new TestApplication())
        try {
            cluster.start(null)
            fail "Did not throw expected exception"
        } catch(NullPointerException e) {
            // expected behaviour
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterIsEmpty() {
        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0, new TestApplication())
        try {
            cluster.start([])
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterHasMoreThanOneElement() {
        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0, new TestApplication())
        try {
            cluster.start([new GeneralPurposeLocation(), new GeneralPurposeLocation()])
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void resizeFromZeroToOneStartsANewEntityAndSetsItsOwner() {
        ExecutionContext execCtx = new BasicExecutionContext(new BasicExecutionManager())
        Collection<Location> locations = [new GeneralPurposeLocation()]

        def entity = new TestEntity()
        Application app = new TestApplication()
        DynamicCluster cluster = new DynamicCluster(newEntity: {entity}, initialSize: 0, app)

        cluster.start(locations)
        cluster.resize(1)
        assertEquals 1, entity.counter.get()
        assertEquals cluster, entity.owner
        assertEquals app, entity.application
    }

    @Test
    public void currentSizePropertyReflectsActualClusterSize() {
        Collection<Location> locations = [new GeneralPurposeLocation()]

        Application app = new AbstractApplication(){}
        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0, app)
        cluster.start(locations)

        assertEquals 0, cluster.currentSize

        ResizeResult rr = cluster.resize(1)
        assertEquals 1, rr.delta
        assertEquals 1, cluster.currentSize

        rr = cluster.resize(4)
        assertEquals 3, rr.delta
        assertEquals 4, cluster.currentSize
    }

    private static class TestApplication extends AbstractApplication {
        @Override String toString() { return "Application["+id[-8..-1]+"]" }
    }
    private static class TestEntity extends AbstractEntity implements Startable {
        private static final Logger logger = LoggerFactory.getLogger(DynamicCluster)
        AtomicInteger counter = new AtomicInteger(0)
        void start(Collection<? extends Location> loc) {logger.trace "Start"; counter.incrementAndGet()}
        void stop() {}
        @Override String toString() { return "Entity["+id[-8..-1]+"]" }
    }

}
