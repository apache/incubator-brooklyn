package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.Map
import java.util.Set

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.DynamicGroup
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication

public class LoadBalancingPolicyTest {
    
    // mock container entity
    public static class MockContainerEntity extends AbstractGroup implements BalanceableContainer<Entity> {
        public MockContainerEntity (Map props=[:], Entity owner=null) {
            super(props, owner)
        }
        public addItem(Entity item) {
            addMember(item)
            emit(BalanceableContainer.ITEM_ADDED, item)
        }
        public removeItem(Entity item) {
            removeMember(item)
            emit(BalanceableContainer.ITEM_REMOVED, item)
        }
        public Set<Entity> getBalanceableItems() {
            Set<Entity> result = new HashSet<Entity>()
            result.addAll(getMembers())
            return result
        }
    }
    
    // mock item entity
    public static class MockItemEntity extends AbstractEntity implements Movable {
        private Entity currentContainer;
        public MockItemEntity (Map props=[:], Entity owner=null) { super(props, owner) }
        public String getContainerId() { return currentContainer?.getId() }
        public void move(Entity destination) {
            ((MockContainerEntity) currentContainer)?.removeItem(this)
            currentContainer = destination
            ((MockContainerEntity) destination)?.addItem(this)
        }
    }
    
    
    public static final AttributeSensor<Integer> TEST_METRIC =
        new BasicAttributeSensor<Integer>(Integer.class, "test.metric", "Dummy workrate for test entities")
    
    public static final ConfigKey<Double> LOW_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, "metric.threshold.low", "desc", 0.0)
    public static final ConfigKey<Double> HIGH_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, "metric.threshold.high", "desc", 0.0)
    
    private TestApplication app
    private SimulatedLocation loc
    private BalanceableWorkerPool pool
    private LoadBalancingPolicy policy
    private Group containerGroup
    
    
    @BeforeMethod()
    public void before() {
        app = new TestApplication()
        pool = new BalanceableWorkerPool([:], app)
        containerGroup = new DynamicGroup([name:"containerGroup"], app, { e -> (e instanceof MockContainerEntity) })
        pool.setContents(containerGroup)
        
        policy = new LoadBalancingPolicy([:], TEST_METRIC, new DefaultBalanceablePoolModel("foo"))
        policy.setEntity(pool)
        
        app.start([loc])
    }
    
    @Test
    public void testSimpleBalancing() {
        // Create containers. Annoyingly, can't set owner until after the threshold config has been defined.
        MockContainerEntity containerA = new MockContainerEntity([:])
        MockContainerEntity containerB = new MockContainerEntity([:])
        containerA.setConfig(LOW_THRESHOLD_CONFIG_KEY, new Double(20.0))
        containerA.setConfig(HIGH_THRESHOLD_CONFIG_KEY, new Double(60.0))
        containerB.setConfig(LOW_THRESHOLD_CONFIG_KEY, new Double(20.0))
        containerB.setConfig(HIGH_THRESHOLD_CONFIG_KEY, new Double(60.0))
        containerA.setOwner(app)
        containerB.setOwner(app)
        app.getManagementContext().manage(containerA)
        app.getManagementContext().manage(containerB)
        
        // Create items.
        MockItemEntity item1 = new MockItemEntity([:], app)
        MockItemEntity item2 = new MockItemEntity([:], app)
        MockItemEntity item3 = new MockItemEntity([:], app)
        MockItemEntity item4 = new MockItemEntity([:], app)
        app.getManagementContext().manage(item1)
        app.getManagementContext().manage(item2)
        app.getManagementContext().manage(item3)
        app.getManagementContext().manage(item4)
        
        // Assign item workrates.
        item1.setAttribute(TEST_METRIC, 10)
        item2.setAttribute(TEST_METRIC, 10)
        item3.setAttribute(TEST_METRIC, 10)
        item4.setAttribute(TEST_METRIC, 10)
        
        // Assign to containers
        [ item1, item2, item3, item4 ].each { i -> i.move(containerA) }
        
        item4.setAttribute(TEST_METRIC, 60)
        
        executeUntilSucceeds(timeout:5000) {
            assertEquals(item1.getContainerId(), containerA.getId())
            assertEquals(item2.getContainerId(), containerA.getId())
            assertEquals(item3.getContainerId(), containerA.getId())
            assertEquals(item4.getContainerId(), containerB.getId())
        }
    }
    
    // TODO: other tests
    
}
