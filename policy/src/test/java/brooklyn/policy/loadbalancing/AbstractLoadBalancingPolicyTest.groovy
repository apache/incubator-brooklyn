package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod

import brooklyn.config.ConfigKey
import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Entities
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater

import com.google.common.base.Preconditions

public class AbstractLoadBalancingPolicyTest {
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractLoadBalancingPolicyTest.class)
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100
    
    public static final AttributeSensor<Integer> TEST_METRIC =
        new BasicAttributeSensor<Integer>(Integer.class, "test.metric", "Dummy workrate for test entities")
    
    public static final ConfigKey<Double> LOW_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, TEST_METRIC.getName()+".threshold.low", "desc", 0.0)
    public static final ConfigKey<Double> HIGH_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, TEST_METRIC.getName()+".threshold.high", "desc", 0.0)
    
    protected TestApplication app
    protected SimulatedLocation loc
    protected BalanceableWorkerPool pool
    protected DefaultBalanceablePoolModel<Entity, Entity> model
    protected LoadBalancingPolicy policy
    protected Group containerGroup
    protected Group itemGroup
    protected Random random = new Random()
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        LOG.debug("In AbstractLoadBalancingPolicyTest.before()");
        
        MockItemEntity.totalMoveCount.set(0)
        
        loc = new SimulatedLocation(name:"loc")
        
        model = new DefaultBalanceablePoolModel<Entity, Entity>("pool-model");
        
        app = new TestApplication()
        containerGroup = new DynamicGroup([name:"containerGroup"], app, { e -> (e instanceof MockContainerEntity) })
        itemGroup = new DynamicGroup([name:"itemGroup"], app, { e -> (e instanceof MockItemEntity) })
        pool = new BalanceableWorkerPool([:], app)
        pool.setContents(containerGroup, itemGroup)
        policy = new LoadBalancingPolicy([:], TEST_METRIC, model)
        pool.addPolicy(policy)
        app.start([loc])
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (policy != null) policy.destroy();
        if (app != null) Entities.destroyAll(app);
    }
    
    // Using this utility, as it gives more info about the workrates of all containers rather than just the one that differs    
    protected void assertWorkrates(Collection<MockContainerEntity> containers, Collection<Double> expectedC, double precision) {
        List<Double> actual = containers.collect { getContainerWorkrate(it) }
        List<Double> expected = [] + expectedC
        String errMsg = "actual=$actual; expected=$expected"
        assertEquals(containers.size(), expected.size(), errMsg)
        for (int i = 0; i < containers.size(); i++) {
            assertEquals(actual.get(i), expected.get(i), precision, errMsg)
        }
    }
    
    protected void assertWorkratesEventually(Collection<MockContainerEntity> containers, Collection<Double> expected) {
        assertWorkratesEventually(containers, expected, 0d)
    }

    /**
     * Asserts that the given container have the given expected workrates (by querying the containers directly).
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertWorkratesEventually(Collection<MockContainerEntity> containers, Collection<Double> expected, double precision) {
        try {
            executeUntilSucceeds(timeout:TIMEOUT_MS) {
                assertWorkrates(containers, expected, precision)
            }
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString(containers)
            throw new RuntimeException(errMsg, e);
        }
    }

    // Using this utility, as it gives more info about the workrates of all containers rather than just the one that differs    
    protected void assertWorkratesContinually(List<MockContainerEntity> containers, List<Double> expected) {
        assertWorkratesContinually(containers, expected, 0d)
    }

    /**
     * Asserts that the given containers have the given expected workrates (by querying the containers directly)
     * continuously for SHORT_WAIT_MS.
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertWorkratesContinually(List<MockContainerEntity> containers, List<Double> expected, double precision) {
        try {
            new Repeater()
                .every((long)(SHORT_WAIT_MS/10))
                .limitIterationsTo(10)
                .rethrowExceptionImmediately()
                .until({false})
                .repeat( { assertWorkrates(containers, expected, precision) } )
                .run()
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString(containers)
            throw new RuntimeException(errMsg, e);
        }
    }

    protected String verboseDumpToString(List<MockContainerEntity> containers) {
        List<Double> containerRates = containers.collect { it.getWorkrate() }
        List<Set<Entity>> itemDistribution = containers.collect { it.getBalanceableItems() }
        String modelItemDistribution = model.itemDistributionToString()
        return "containers=$containers; containerRates=$containerRates; itemDistribution=$itemDistribution; model=$modelItemDistribution; "+
                "totalMoves=${MockItemEntity.totalMoveCount}"
    }
    
    protected MockContainerEntity newContainer(Application app, String name, double lowThreshold, double highThreshold) {
        return newAsyncContainer(app, name, lowThreshold, highThreshold, 0)
    }
    
    /**
     * Creates a new container that will take "delay" millis to complete its start-up.
     */
    protected MockContainerEntity newAsyncContainer(Application app, String name, double lowThreshold, double highThreshold, long delay) {
        // Annoyingly, can't set parent until after the threshold config has been defined.
        MockContainerEntity container = new MockContainerEntity([displayName:name], delay)
        container.setConfig(LOW_THRESHOLD_CONFIG_KEY, lowThreshold)
        container.setConfig(HIGH_THRESHOLD_CONFIG_KEY, highThreshold)
        container.setParent(app)
        LOG.debug("Managing new container {}", container)
        Entities.manage(container);
        container.start([loc])
        return container
    }
    
    protected static MockItemEntity newItem(Application app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = new MockItemEntity([displayName:name], app)
        LOG.debug("Managing new item {}", container)
        Entities.manage(item);
        item.move(container)
        item.setAttribute(TEST_METRIC, workrate)
        return item
    }
    
    protected static MockItemEntity newLockedItem(Application app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = new MockItemEntity([displayName:name])
        item.setConfig(Movable.IMMOVABLE, true)
        item.setParent(app)
        LOG.debug("Managing new item {}", container)
        Entities.manage(item);
        item.move(container)
        item.setAttribute(TEST_METRIC, workrate)
        return item
    }
    
    /**
     * Asks the item directly for its workrate.
     */
    protected static double getItemWorkrate(MockItemEntity item) {
        Object result = item.getAttribute(TEST_METRIC)
        return (result == null ? 0 : ((Number) result).doubleValue())
    }
    
    /**
     * Asks the container for its items, and then each of those items directly for their workrates; returns the total.
     */
    protected static double getContainerWorkrate(MockContainerEntity container) {
        double result = 0.0
        Preconditions.checkNotNull(container, "container");
        container.getBalanceableItems().each { MockItemEntity item ->
            Preconditions.checkNotNull(item, "item in container");
            assertEquals(item.getContainerId(), container.getId())
            result += getItemWorkrate(item)
        }
        return result
    }
    
}
