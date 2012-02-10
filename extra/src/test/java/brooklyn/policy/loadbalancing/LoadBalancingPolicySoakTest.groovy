package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.io.ByteArrayOutputStream
import java.io.PrintStream

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.test.entity.TestApplication

public class LoadBalancingPolicySoakTest extends AbstractLoadBalancingPolicyTest {

    protected static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicySoakTest.class)
    
    private static final long TIMEOUT_MS = 40*1000;
    
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
    
    @Test(groups="Integration") // acceptance group, because it's slow to run many cycles
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

    @Test(groups="Integration") // acceptance group, because it's slow to run many cycles
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
    @Test(groups="Integration")
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
        config.verbose = false
        
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
        boolean verbose = config.verbose
        
        MockItemEntity.totalMoveCount.set(0)
        
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
            LOG.info(LoadBalancingPolicySoakTest.class.getSimpleName()+": cycle $i")
            
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
                String errMsg
                if (verbose) {
                    errMsg = verboseDumpToString(containers)+"; itemRates=$itemRates"
                } else {
                    errMsg = "$containerRates; totalMoves=${MockItemEntity.totalMoveCount}"
                }
                                
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
        int numItemStopsPerCycle = 1
        boolean verbose = true
    }

    // Testing conveniences.
    
    private double sum(Iterable<Double> vals) {
        double total = 0;
        vals.each { total += it }
        return total;
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
