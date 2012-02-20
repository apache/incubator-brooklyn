package brooklyn.policy.followthesun

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.List
import java.util.Random

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.DynamicGroup
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.policy.loadbalancing.MockContainerEntity
import brooklyn.policy.loadbalancing.MockItemEntity
import brooklyn.policy.loadbalancing.Movable
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater

public class AbstractFollowTheSunPolicyTest {
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractFollowTheSunPolicyTest.class)
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100
    
    public static final AttributeSensor<Integer> TEST_METRIC =
        new BasicAttributeSensor<Map<Entity,Double>>(Map.class, "test.metric", "Dummy workrate for test entities")

    protected TestApplication app
    protected SimulatedLocation loc1
    protected SimulatedLocation loc2
    protected FollowTheSunPool pool
    protected DefaultFollowTheSunModel<Entity, Entity> model
    protected FollowTheSunPolicy policy
    protected Group containerGroup
    protected Group itemGroup
    protected Random random = new Random()
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        LOG.debug("In AbstractFollowTheSunPolicyTest.before()");

        MockItemEntity.totalMoveCount.set(0)
        
        loc1 = new SimulatedLocation(name:"loc1")
        loc2 = new SimulatedLocation(name:"loc2")
        
        // TODO: improve the default impl to avoid the need for this anonymous overrider of 'moveItem'
        model = new DefaultFollowTheSunModel<Entity, Entity>("pool-model") {
            @Override public void moveItem(Entity item, Entity newContainer) {
                ((Movable) item).move(newContainer)
                onItemMoved(item, newContainer)
            }
        }
        
        app = new TestApplication()
        containerGroup = new DynamicGroup([name:"containerGroup"], app, { e -> (e instanceof MockContainerEntity) })
        itemGroup = new DynamicGroup([name:"itemGroup"], app, { e -> (e instanceof MockItemEntity) })
        pool = new FollowTheSunPool([:], app)
        pool.setContents(containerGroup, itemGroup)
        policy = new FollowTheSunPolicy([:], TEST_METRIC, model, FollowTheSunParameters.newDefault())
        pool.addPolicy(policy)
        app.start([loc1, loc2])
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (pool != null && policy != null) pool.removePolicy(policy)
        if (app != null) app.stop()
        MockItemEntity.totalMoveCount.set(0)
    }
    
    // Using this utility, as it gives more info about the workrates of all containers rather than just the one that differs    
    protected void assertWorkratesEventually(List<MockContainerEntity> containers, List<Double> expected) {
        assertWorkratesEventually(containers, expected, 0d)
    }

    /**
     * Asserts that the given container have the given expected workrates (by querying the containers directly).
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertItemDistributionEventually(Map<MockContainerEntity, Collection<MockItemEntity>> expected) {
        try {
            executeUntilSucceeds(timeout:TIMEOUT_MS) {
                assertItemDistribution(expected)
            }
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString()
            throw new RuntimeException(errMsg, e);
        }
    }

    protected void assertItemDistributionContinually(Map<MockContainerEntity, Collection<MockItemEntity>> expected) {
        try {
            new Repeater()
                .every((long)(SHORT_WAIT_MS/10))
                .limitIterationsTo(10)
                .rethrowExceptionImmediately()
                .until({false})
                .repeat( { assertItemDistribution(expected) } )
                .run()
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString()
            throw new RuntimeException(errMsg, e);
        }
    }

    protected void assertItemDistribution(Map<MockContainerEntity, Collection<MockItemEntity>> expected) {
        String errMsg = verboseDumpToString()
        for (Map.Entry<MockContainerEntity, Collection<MockItemEntity>> entry : expected.entrySet()) {
            MockContainerEntity container = entry.getKey()
            Collection<MockItemEntity> expectedItems = entry.getValue()
            
            assertEquals(container.getBalanceableItems() as Set, expectedItems as Set)
        }
    }

    protected String verboseDumpToString() {
        Collection<MockContainerEntity> containers = app.managementContext.entities.findAll { it instanceof MockContainerEntity }
        List<Set<Entity>> itemDistribution = containers.collect { it.getBalanceableItems() }
        String modelItemDistribution = model.itemDistributionToString()
        return "containers=$containers; itemDistribution=$itemDistribution; model=$modelItemDistribution; "+
                "totalMoves=${MockItemEntity.totalMoveCount}"
    }
    
    protected MockContainerEntity newContainer(Application app, Location loc, String name) {
        return newAsyncContainer(app, loc, name, 0)
    }
    
    /**
     * Creates a new container that will take "delay" millis to complete its start-up.
     */
    protected MockContainerEntity newAsyncContainer(Application app, Location loc, String name, long delay) {
        // Annoyingly, can't set owner until after the threshold config has been defined.
        MockContainerEntity container = new MockContainerEntity([displayName:name], delay)
        container.setOwner(app)
        LOG.debug("Managing new container {}", container)
        app.getManagementContext().manage(container)
        container.start([loc])
        return container
    }

    protected static MockItemEntity newLockedItem(Application app, MockContainerEntity container, String name) {
        MockItemEntity item = new MockItemEntity([displayName:name, immovable:true], app)
        LOG.debug("Managing new locked item {}", container)
        app.getManagementContext().manage(item)
        if (container != null) {
            item.move(container)
        }
        return item
    }
    
    protected static MockItemEntity newItem(Application app, MockContainerEntity container, String name) {
        MockItemEntity item = new MockItemEntity([displayName:name], app)
        LOG.debug("Managing new item {}", container)
        app.getManagementContext().manage(item)
        if (container != null) {
            item.move(container)
        }
        return item
    }
    
    protected static MockItemEntity newItem(Application app, MockContainerEntity container, String name, Map<MockItemEntity, Double> workpattern) {
        MockItemEntity item = newItem(app, container, name)
        if (workpattern != null) {
            item.setAttribute(TEST_METRIC, workpattern)
        }
        return item
    }
}
