package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.io.ByteArrayOutputStream
import java.io.PrintStream

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.DynamicGroup
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication

public class LoadBalancingPolicySoakTest {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicySoakTest.class)
    
    private static final long TIMEOUT_MS = 20*1000;
    
    private static final long CONTAINER_STARTUP_DELAY_MS = 100
    
    public static final ConfigKey<Double> LOW_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, "metric.threshold.low", "desc", 0.0)
    public static final ConfigKey<Double> HIGH_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, "metric.threshold.high", "desc", 0.0)
    
    private TestApplication app
    private SimulatedLocation loc
    private DefaultBalanceablePoolModel<Entity, Entity> model;
    private BalanceableWorkerPool pool
    private LoadBalancingPolicy policy
    private Group containerGroup
    private Group itemGroup
    private Random random = new Random()
    
    @BeforeMethod()
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
        itemGroup = new DynamicGroup([name:"containerGroup"], app, { e -> (e instanceof MockItemEntity) })
        pool = new BalanceableWorkerPool([:], app)
        pool.setContents(containerGroup, itemGroup)
        policy = new LoadBalancingPolicy([:], MockItemEntity.TEST_METRIC, model)
        policy.setEntity(pool)
        app.start([loc])
    }

    @Test
    public void testLoadBalancingQuickTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 1
        config.numContainers = 5
        config.numItems = 5
        config.lowThreshold = 200
        config.highThreshold = 300
        config.totalRate = config.numContainers*(0.95*config.highThreshold)
    
        runLoadBalancingSoakTest(config)
    }
    
    @Test
    public void testLoadBalancingManyItemsQuickTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 1
        config.numContainers = 5
        config.numItems = 30
        config.lowThreshold = 200
        config.highThreshold = 300
        config.numContainerStopsPerCycle = 1
        config.numItemStopsPerCycle = 1
        config.totalRate = config.numContainers*(0.95*config.highThreshold)
    
        runLoadBalancingSoakTest(config)
    }
    
    @Test(groups="Acceptance") // acceptance group, because it's slow to run many cycles
    public void testLoadBalancingSoakTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 100
        config.numContainers = 5
        config.numItems = 5
        config.lowThreshold = 200
        config.highThreshold = 300
        config.totalRate = config.numContainers*(0.95*config.highThreshold)
    
        runLoadBalancingSoakTest(config)
    }

    @Test(groups="Acceptance") // acceptance group, because it's slow to run many cycles
    public void testLoadBalancingManyItemsSoakTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 100
        config.numContainers = 5
        config.numItems = 30
        config.lowThreshold = 200
        config.highThreshold = 300
        config.totalRate = config.numContainers*(0.95*config.highThreshold)
        config.numContainerStopsPerCycle = 3
        config.numItemStopsPerCycle = 10
        
        runLoadBalancingSoakTest(config)
    }

    // FIXME code is too inefficient; doesn't balance 1000 items within the timeout period.
    // And that's with only one workrate report per item per cycle, rather than every 500ms!
    @Test(enabled=false, groups="WIP")
    public void testLoadBalancingManyManyItemsTest() {
        RunConfig config = new RunConfig()
        config.numCycles = 1
        config.numContainers = 5
        config.numItems = 1000
        config.lowThreshold = 2000
        config.highThreshold = 3000
        config.numContainerStopsPerCycle = 0
        config.numItemStopsPerCycle = 0
        config.totalRate = config.numContainers*(0.95*config.highThreshold)
    
        runLoadBalancingSoakTest(config)
    }
    
    private void runLoadBalancingSoakTest(RunConfig config) {
        int numCycles = config.numCycles
        int numContainers = config.numContainers
        int numItems = config.numItems
        double lowThreshold = config.lowThreshold
        double highThreshold = config.highThreshold
        int totalRate = config.totalRate
        int numContainerStopsPerCycle = config.numContainerStopsPerCycle
        int numItemStopsPerCycle = config.numItemStopsPerCycle
        
        List<MockContainerEntity> containers = new ArrayList<MockContainerEntity>()
        List<MockItemEntity> items = new ArrayList<MockItemEntity>()
        
        for (int i in 1..numContainers) {
            MockContainerEntity container = newContainer(app, "container-$i", lowThreshold, highThreshold)
            containers.add(container)
        }
        for (int i in 1..numItems) {
            MockItemEntity item = newItem(app, containers.get(0), "item-$i", 5)
            items.add(item)
        }
        
        for (int i in 1..numCycles) {
            LOG.debug(LoadBalancingPolicySoakTest.class.getSimpleName()+": cycle $i")
            
            // Stop items, and start others
            for (j in 1..numItemStopsPerCycle) {
                int itemIndex = random.nextInt(numItems)
                MockItemEntity itemToStop = items.get(itemIndex)
                itemToStop.stop()
                LOG.debug("Unmanaging item {}", itemToStop)
                app.managementContext.unmanage(itemToStop)
                items.set(itemIndex, newItem(app, containers.get(0), "item-"+(itemIndex+1)+".$i.$j", 5))
            }
            
            // Repartition the load across the items
            List<Integer> itemRates = randomlyDivideLoad(numItems, totalRate, 0, (int)highThreshold)
            
            for (int j in 0..(numItems-1)) {
                MockItemEntity item = items.get(j)
                item.setAttribute(MockItemEntity.TEST_METRIC, itemRates.get(j))
            }
                
            // Stop containers, and start others
            for (j in 1..numContainerStopsPerCycle) {
                int containerIndex = random.nextInt(numContainers)
                MockContainerEntity containerToStop = containers.get(containerIndex)
                containerToStop.offloadAndStop(containers.get((containerIndex+1)%numContainers))
                LOG.debug("Unmanaging container {}", containerToStop)
                app.managementContext.unmanage(containerToStop)
                
                MockContainerEntity containerToAdd = newContainer(app, "container-"+(containerIndex+1)+".$i.$j", lowThreshold, highThreshold)
                containers.set(containerIndex, containerToAdd)
            }

            // Assert that the items become balanced again
            executeUntilSucceeds(timeout:TIMEOUT_MS) {
                List<Double> containerRates = containers.collect { it.getWorkrate() }
                List<Set<Entity>> itemDistribution = containers.collect { it.getBalanceableItems() }
                String modelItemDistribution = modelItemDistributionToString()
                String errMsg = "containerRates=$containerRates; itemRates=$itemRates; itemDistribution=$itemDistribution; model=$modelItemDistribution"
                
                assertEquals(sum(containerRates), sum(itemRates), errMsg)
                for (double containerRate : containerRates) {
                    assertTrue(containerRate >= lowThreshold, errMsg)
                    assertTrue(containerRate <= highThreshold, errMsg)
                }
            }
        }
    }
    
    private static class RunConfig {
        int numCycles = 1
        int numContainers = 5
        int numItems = 5
        double lowThreshold = 200
        double highThreshold = 300
        int totalRate = 5*(0.95*highThreshold)
        int numContainerStopsPerCycle = 1
        int numItemStopsPerCycle =1
    }

    // Testing conveniences.
    
    private String modelItemDistributionToString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        model.dumpItemDistribution(new PrintStream(baos));
        return new String(baos.toByteArray());
    }
    
    private double sum(Iterable<Double> vals) {
        double total = 0;
        vals.each { total += it }
        return total;
    }
    
    private MockContainerEntity newContainer(Application app, String name, double lowThreshold, double highThreshold) {
        return newAsyncContainer(app, name, lowThreshold, highThreshold, 0)
    }
    
    private MockContainerEntity newAsyncContainer(Application app, String name, double lowThreshold, double highThreshold, long delay) {
        // Annoyingly, can't set owner until after the threshold config has been defined.
        MockContainerEntity container = new MockContainerEntity([displayName:name], delay)
        container.setConfig(LOW_THRESHOLD_CONFIG_KEY, lowThreshold)
        container.setConfig(HIGH_THRESHOLD_CONFIG_KEY, highThreshold)
        container.setOwner(app)
        LOG.debug("Managing container {}", container)
        app.getManagementContext().manage(container)
        container.start([loc])
        return container
    }
    
    private static MockItemEntity newItem(Application app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = new MockItemEntity([displayName:name], app)
        LOG.debug("Managing item {}", item)
        app.getManagementContext().manage(item)
        item.move(container)
        item.setAttribute(MockItemEntity.TEST_METRIC, workrate)
        return item
    }
    
    private static double getItemWorkrate(MockItemEntity item) {
        Object result = item.getAttribute(MockItemEntity.TEST_METRIC)
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
    
    /**
     * Distributes a given load across a number of items randomly. The variability in load for an item is dictated by the variance,
     * but the total will always equal totalLoad.
     * 
     * The distribution of load is skewed: one side of the list will have bigger values than the other.
     * Which side is skewed will vary, so when balancing a policy will find that things have entirely changed.
     * 
     * TODO This is not particularly good at distributing load, but it's random and skewed enough to force rebalancing.
     */
    private List<Integer> randomlyDivideLoad(int numItems, int totalLoad, int min, int max) {
        List<Integer> result = new ArrayList<Integer>(numItems);
        int totalRemaining = totalLoad;
        int variance = 3
        int skew = 3
        
        for (int i = 0; i < numItems; i++) {
            int itemsRemaining = numItems-i;
            int itemFairShare = (totalRemaining/itemsRemaining)
            double skewFactor = ((double)i/numItems)*2 - 1; // a number between -1 and 1, depending how far through the item set we are
            int itemSkew = (int) (random.nextInt(skew)*skewFactor)
            int itemLoad = itemFairShare + (random.nextInt(variance*2)-variance) + itemSkew;
            itemLoad = Math.max(min, itemLoad)
            itemLoad = Math.min(totalRemaining, itemLoad)
            itemLoad = Math.min(max, itemLoad)
            result.add(itemLoad);
            totalRemaining -= itemLoad;
        }

        if (random.nextBoolean()) Collections.reverse(result)
        
        assertTrue(sum(result) <= totalLoad, "totalLoad=$totalLoad; result=$result")
        
        return result
    }
}
