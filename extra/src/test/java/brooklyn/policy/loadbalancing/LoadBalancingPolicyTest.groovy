package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.Map
import java.util.Set

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application;
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
            LOG.info("Adding item "+item+" to container "+this)
            addMember(item)
            emit(BalanceableContainer.ITEM_ADDED, item)
        }
        public removeItem(Entity item) {
            LOG.info("Removing item "+item+" from container "+this)
            removeMember(item)
            emit(BalanceableContainer.ITEM_REMOVED, item)
        }
        public Set<Entity> getBalanceableItems() {
            Set<Entity> result = new HashSet<Entity>()
            result.addAll(getMembers())
            return result
        }
        public String toString() { return "MockContainer["+getDisplayName()+"]" }
    }
    
    // mock item entity
    public static class MockItemEntity extends AbstractEntity implements Movable {
        private Entity currentContainer;
        public MockItemEntity (Map props=[:], Entity owner=null) { super(props, owner) }
        public String getContainerId() { return currentContainer?.getId() }
        public void move(Entity destination) {
            ((MockContainerEntity) currentContainer)?.removeItem(this)
            currentContainer = destination
            ((MockContainerEntity) currentContainer)?.addItem(this)
        }
        public String toString() { return "MockItem["+getDisplayName()+"]" }
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
        
        policy = new LoadBalancingPolicy([:], TEST_METRIC, new DefaultBalanceablePoolModel<Entity, Entity>("foo") {
            @Override public void moveItem(Entity item, Entity oldNode, Entity newNode) {
                super.moveItem(item, oldNode, newNode)
                ((MockItemEntity) item).move(newNode)
            }
        })
        policy.setEntity(pool)
        
        app.start([loc])
    }
    
    @Test
    public void testSimpleBalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 25)
        MockContainerEntity containerB = newContainer(app, "B", 20, 60)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerA, "3", 10)
        MockItemEntity item4 = newItem(app, containerA, "4", 10)
        
        executeUntilSucceeds(timeout:5000) {
            assertEquals(getContainerWorkrate(containerA), 20d)
            assertEquals(getContainerWorkrate(containerB), 20d)
        }
    }
    
    // TODO: other tests
    
    
    // Testing conveniences.
     
    private static MockContainerEntity newContainer(Application app, String name, double lowThreshold, double highThreshold) {
        // Annoyingly, can't set owner until after the threshold config has been defined.
        MockContainerEntity container = new MockContainerEntity([displayName:name])
        container.setConfig(LOW_THRESHOLD_CONFIG_KEY, lowThreshold)
        container.setConfig(HIGH_THRESHOLD_CONFIG_KEY, highThreshold)
        container.setOwner(app)
        app.getManagementContext().manage(container)
        return container
    }
    
    private static MockItemEntity newItem(Application app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = new MockItemEntity([displayName:name], app)
        app.getManagementContext().manage(item)
        item.move(container)
        item.setAttribute(TEST_METRIC, workrate)
        return item
    }
    
    private static double getItemWorkrate(MockItemEntity item) {
        Object result = item.getAttribute(TEST_METRIC)
        return (result == null ? 0 : ((Number) result).doubleValue())
    }
    
    private static double getContainerWorkrate(MockContainerEntity container) {
        double result = 0.0
        container.getBalanceableItems().each { MockItemEntity item ->
            assertEquals(item.getContainerId(), container.getId())
            result += getItemWorkrate(item)
        }
        return result
    }
    
}
