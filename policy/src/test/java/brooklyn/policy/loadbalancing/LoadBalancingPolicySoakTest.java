package brooklyn.policy.loadbalancing;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.test.Asserts;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

public class LoadBalancingPolicySoakTest extends AbstractLoadBalancingPolicyTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicySoakTest.class);
    
    private static final long TIMEOUT_MS = 40*1000;
    
    @Test
    public void testLoadBalancingQuickTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 1;
        config.numContainers = 5;
        config.numItems = 5;
        config.lowThreshold = 200;
        config.highThreshold = 300;
        config.totalRate = (int) (config.numContainers*(0.95*config.highThreshold));
    
        runLoadBalancingSoakTest(config);
    }
    
    @Test
    public void testLoadBalancingManyItemsQuickTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 1;
        config.numContainers = 5;
        config.numItems = 30;
        config.lowThreshold = 200;
        config.highThreshold = 300;
        config.numContainerStopsPerCycle = 1;
        config.numItemStopsPerCycle = 1;
        config.totalRate = (int) (config.numContainers*(0.95*config.highThreshold));
    
        runLoadBalancingSoakTest(config);
    }
    
    @Test(groups={"Integration","Acceptance"}) // acceptance group, because it's slow to run many cycles
    public void testLoadBalancingSoakTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 100;
        config.numContainers = 5;
        config.numItems = 5;
        config.lowThreshold = 200;
        config.highThreshold = 300;
        config.totalRate = (int) (config.numContainers*(0.95*config.highThreshold));
    
        runLoadBalancingSoakTest(config);
    }

    @Test(groups={"Integration","Acceptance"}) // acceptance group, because it's slow to run many cycles
    public void testLoadBalancingManyItemsSoakTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 100;
        config.numContainers = 5;
        config.numItems = 30;
        config.lowThreshold = 200;
        config.highThreshold = 300;
        config.totalRate = (int) (config.numContainers*(0.95*config.highThreshold));
        config.numContainerStopsPerCycle = 3;
        config.numItemStopsPerCycle = 10;
        
        runLoadBalancingSoakTest(config);
    }

    @Test(groups={"Integration","Acceptance"})
    public void testLoadBalancingManyManyItemsTest() {
        RunConfig config = new RunConfig();
        config.numCycles = 1;
        config.numContainers = 5;
        config.numItems = 1000;
        config.lowThreshold = 2000;
        config.highThreshold = 3000;
        config.numContainerStopsPerCycle = 0;
        config.numItemStopsPerCycle = 0;
        config.totalRate = (int) (config.numContainers*(0.95*config.highThreshold));
        config.verbose = false;
        
        runLoadBalancingSoakTest(config);
    }
    
    private void runLoadBalancingSoakTest(RunConfig config) {
        final int numCycles = config.numCycles;
        final int numContainers = config.numContainers;
        final int numItems = config.numItems;
        final double lowThreshold = config.lowThreshold;
        final double highThreshold = config.highThreshold;
        final int totalRate = config.totalRate;
        final int numContainerStopsPerCycle = config.numContainerStopsPerCycle;
        final int numItemStopsPerCycle = config.numItemStopsPerCycle;
        final boolean verbose = config.verbose;
        
        MockItemEntityImpl.totalMoveCount.set(0);
        
        final List<MockContainerEntity> containers = new ArrayList<MockContainerEntity>();
        final List<MockItemEntity> items = new ArrayList<MockItemEntity>();

        for (int i = 1; i <= numContainers; i++) {
            MockContainerEntity container = newContainer(app, "container-"+i, lowThreshold, highThreshold);
            containers.add(container);
        }
        for (int i = 1; i <= numItems; i++) {
            MockItemEntity item = newItem(app, containers.get(0), "item-"+i, 5);
            items.add(item);
        }
        
        for (int i = 1; i <= numCycles; i++) {
            LOG.info(LoadBalancingPolicySoakTest.class.getSimpleName()+": cycle "+i);
            
            // Stop items, and start others
            for (int j = 1; j <= numItemStopsPerCycle; j++) {
                int itemIndex = random.nextInt(numItems);
                MockItemEntity itemToStop = items.get(itemIndex);
                itemToStop.stop();
                LOG.debug("Unmanaging item {}", itemToStop);
                Entities.unmanage(itemToStop);
                items.set(itemIndex, newItem(app, containers.get(0), "item-"+(itemIndex+1)+"."+i+"."+j, 5));
            }
            
            // Repartition the load across the items
            final List<Integer> itemRates = randomlyDivideLoad(numItems, totalRate, 0, (int)highThreshold);
            
            for (int j = 0; j < numItems; j++) {
                MockItemEntity item = items.get(j);
                ((EntityLocal)item).setAttribute(MockItemEntity.TEST_METRIC, itemRates.get(j));
            }
                
            // Stop containers, and start others
            for (int j = 1; j <= numContainerStopsPerCycle; j++) {
                int containerIndex = random.nextInt(numContainers);
                MockContainerEntity containerToStop = containers.get(containerIndex);
                containerToStop.offloadAndStop(containers.get((containerIndex+1)%numContainers));
                LOG.debug("Unmanaging container {}", containerToStop);
                app.getManagementContext().unmanage(containerToStop);
                
                MockContainerEntity containerToAdd = newContainer(app, "container-"+(containerIndex+1)+"."+i+"."+j, lowThreshold, highThreshold);
                containers.set(containerIndex, containerToAdd);
            }

            // Assert that the items become balanced again
            Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    Iterable<Double> containerRates = Iterables.transform(containers, new Function<MockContainerEntity, Double>() {
                        public Double apply(MockContainerEntity input) {
                            return (double) input.getWorkrate();
                        }});
                    
                    String errMsg;
                    if (verbose) {
                        errMsg = verboseDumpToString(containers)+"; itemRates="+itemRates;
                    } else {
                        errMsg = containerRates+"; totalMoves="+MockItemEntityImpl.totalMoveCount;
                    }
                                    
                    assertEquals(sum(containerRates), sum(itemRates), errMsg);
                    for (double containerRate : containerRates) {
                        assertTrue(containerRate >= lowThreshold, errMsg);
                        assertTrue(containerRate <= highThreshold, errMsg);
                    }
                }});
        }
    }
    
    private static class RunConfig {
        int numCycles = 1;
        int numContainers = 5;
        int numItems = 5;
        double lowThreshold = 200;
        double highThreshold = 300;
        int totalRate = (int) (5*(0.95*highThreshold));
        int numContainerStopsPerCycle = 1;
        int numItemStopsPerCycle = 1;
        boolean verbose = true;
    }

    // Testing conveniences.
    
    private double sum(Iterable<? extends Number> vals) {
        double total = 0;;
        for (Number val : vals) {
            total += val.doubleValue();
        }
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
        int variance = 3;
        int skew = 3;
        
        for (int i = 0; i < numItems; i++) {
            int itemsRemaining = numItems-i;
            int itemFairShare = (totalRemaining/itemsRemaining);
            double skewFactor = ((double)i/numItems)*2 - 1; // a number between -1 and 1, depending how far through the item set we are
            int itemSkew = (int) (random.nextInt(skew)*skewFactor);
            int itemLoad = itemFairShare + (random.nextInt(variance*2)-variance) + itemSkew;
            itemLoad = Math.max(min, itemLoad);
            itemLoad = Math.min(totalRemaining, itemLoad);
            itemLoad = Math.min(max, itemLoad);
            result.add(itemLoad);
            totalRemaining -= itemLoad;
        }

        if (random.nextBoolean()) Collections.reverse(result);
        
        assertTrue(sum(result) <= totalLoad, "totalLoad="+totalLoad+"; result="+result);
        
        return result;
    }
}
