package brooklyn.entity.group

import org.testng.annotations.Test
import brooklyn.entity.trait.Startable
import brooklyn.entity.basic.AbstractEntity
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import org.gmock.GMockTestCase
import brooklyn.util.task.ExecutionContext
import brooklyn.util.task.BasicExecutionManager
import java.util.concurrent.Future

class DynamicClusterTest extends GMockTestCase {

    @Test
    public void constructorRequiresThatNewEntityArgumentIsGiven() {
        try {
            new DynamicCluster(initialSize: 1)
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatNewEntityArgumentIsAnEntity() {
        try {
            new DynamicCluster(initialSize: 1, newEntity: new Startable(){ void start(Collection<? extends Location> loc) {} })
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatNewEntityArgumentIsStartable() {
        try {
            new DynamicCluster(initialSize: 1, newEntity: new AbstractEntity(){})
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatInitialSizeArgumentIsGiven() {
        try {
            new DynamicCluster(newEntity: {new TestEntity()})
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void constructorRequiresThatInitialSizeArgumentIsAnInteger() {
        try {
            new DynamicCluster(newEntity: {new TestEntity()}, initialSize: "foo")
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterIsMissing() {
        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0)
        try {
            cluster.start(null)
            fail "Did not throw expected exception"
        } catch(NullPointerException e) {
            // expected behaviour
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterIsEmpty() {
        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0)
        try {
            cluster.start([])
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterHasMoreThanOneElement() {
        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0)
        try {
            cluster.start([new GeneralPurposeLocation(), new GeneralPurposeLocation()])
            fail "Did not throw expected exception"
        } catch(IllegalArgumentException e) {
            // expected behaviour
        }
    }

    @Test
    public void resizeFromZeroToOneStartsANewEntityAndSetsItsOwner() {
        ExecutionContext execCtx = new ExecutionContext(new BasicExecutionManager())
        Collection<Location> locations = [new GeneralPurposeLocation()]

        def entity = mock(TestEntity)
        entity.getExecutionContext().returns(execCtx).stub()
        entity.start(locations).once()
        DynamicCluster cluster = new DynamicCluster(newEntity: {entity}, initialSize: 0)
        entity.setOwner(cluster).once()

        cluster.start(locations)

        play {
            cluster.resize(1).each { Future<?> future -> future.get() }
        }
    }

    @Test
    public void currentSizePropertyReflectsActualClusterSize() {
        Collection<Location> locations = [new GeneralPurposeLocation()]

        DynamicCluster cluster = new DynamicCluster(newEntity: {new TestEntity()}, initialSize: 0)
        cluster.start(locations)

        assertEquals 0, cluster.currentSize
        cluster.resize(1).each { Future<?> future -> future.get() }
        assertEquals 1, cluster.currentSize
        cluster.resize(4).each { Future<?> future -> future.get() }
        assertEquals 4, cluster.currentSize
    }

    private static class TestEntity extends AbstractEntity implements Startable {
        private ExecutionContext execCtx = new ExecutionContext(new BasicExecutionManager())

        void start(Collection<? extends Location> loc) {}
        @Override protected synchronized ExecutionContext getExecutionContext() { return execCtx }
    }

}
