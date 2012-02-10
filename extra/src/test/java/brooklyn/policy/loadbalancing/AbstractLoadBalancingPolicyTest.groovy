package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.Random

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.DynamicGroup
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication

public class AbstractLoadBalancingPolicyTest {
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractLoadBalancingPolicyTest.class)
    
    protected static final long TIMEOUT_MS = 10*1000;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100
    
    public static final AttributeSensor<Integer> TEST_METRIC =
        new BasicAttributeSensor<Integer>(Integer.class, "test.metric", "Dummy workrate for test entities")
    
    public static final ConfigKey<Double> LOW_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, "metric.threshold.low", "desc", 0.0)
    public static final ConfigKey<Double> HIGH_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, "metric.threshold.high", "desc", 0.0)
    
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
        // TODO: improve the default impl to avoid the need for this anonymous overrider of 'moveItem'
        model = new DefaultBalanceablePoolModel<Entity, Entity>("pool-model") {
            @Override public void moveItem(Entity item, Entity oldContainer, Entity newContainer) {
                ((Movable) item).move(newContainer)
                onItemMoved(item, newContainer)
            }
        }
        
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
        if (app != null) app.stop()
    }
    
    // Using this utility, as it gives more info about the workrates of all containers rather than just the one that differs    
    protected void assertWorkratesEventually(List<MockContainerEntity> containers, List<Double> expected) {
        assertWorkratesEventually(containers, expected, 0d)
    }

    /**
     * Asserts that the given container have the given expected workrates (by querying the containers directly).
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertWorkratesEventually(List<MockContainerEntity> containers, List<Double> expected, double precision) {
        try {
            executeUntilSucceeds(timeout:TIMEOUT_MS) {
                List<Double> actual = containers.collect { getContainerWorkrate(it) }
                String errMsg = "actual=$actual; expected=$expected"
                assertEquals(containers.size(), expected.size(), errMsg)
                for (int i = 0; i < containers.size(); i++) {
                    assertEquals(actual.get(i), expected.get(i), precision, errMsg)
                }
            }
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString(containers)
            throw new RuntimeException(errMsg, e);
        }
    }

    protected String verboseDumpToString(List<MockContainerEntity> containers) {
        List<Double> containerRates = containers.collect { it.getWorkrate() }
        List<Set<Entity>> itemDistribution = containers.collect { it.getBalanceableItems() }
        String modelItemDistribution = modelItemDistributionToString()
        return "containers=$containers; containerRates=$containerRates; itemDistribution=$itemDistribution; model=$modelItemDistribution; "+
                "totalMoves=${MockItemEntity.totalMoveCount}"
    }
    
    protected String modelItemDistributionToString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        model.dumpItemDistribution(new PrintStream(baos));
        return new String(baos.toByteArray());
    }

    protected MockContainerEntity newContainer(Application app, String name, double lowThreshold, double highThreshold) {
        return newAsyncContainer(app, name, lowThreshold, highThreshold, 0)
    }
    
    /**
     * Creates a new container that will take "delay" millis to complete its start-up.
     */
    protected MockContainerEntity newAsyncContainer(Application app, String name, double lowThreshold, double highThreshold, long delay) {
        // Annoyingly, can't set owner until after the threshold config has been defined.
        MockContainerEntity container = new MockContainerEntity([displayName:name], delay)
        container.setConfig(LOW_THRESHOLD_CONFIG_KEY, lowThreshold)
        container.setConfig(HIGH_THRESHOLD_CONFIG_KEY, highThreshold)
        container.setOwner(app)
        LOG.debug("Managing new container {}", container)
        app.getManagementContext().manage(container)
        container.start([loc])
        return container
    }
    
    protected static MockItemEntity newItem(Application app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = new MockItemEntity([displayName:name], app)
        LOG.debug("Managing new item {}", container)
        app.getManagementContext().manage(item)
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
        container.getBalanceableItems().each { MockItemEntity item ->
            assertEquals(item.getContainerId(), container.getId())
            result += getItemWorkrate(item)
        }
        return result
    }
    
}
