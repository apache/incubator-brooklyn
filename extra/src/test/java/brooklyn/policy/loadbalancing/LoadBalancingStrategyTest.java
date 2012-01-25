package brooklyn.policy.loadbalancing;

import static brooklyn.policy.loadbalancing.MockWorkerPool.containers;
import static brooklyn.policy.loadbalancing.MockWorkerPool.items;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

public class LoadBalancingStrategyTest {
    
    @Test
    public void testNoopBalancing() {
        MockWorkerPool pool = new MockWorkerPool(
                containers(
                        "nodeA", 20, 80,
                        "nodeB", 20, 80),
                items(
                        "item1", "nodeA", 10,
                        "item2", "nodeA", 30,
                        "item3", "nodeB", 20,
                        "item4", "nodeB", 20)
        );
        
        BalancingStrategy<String, String> policy = new BalancingStrategy<String, String>("Test", pool);
        System.out.println("\nStarting configuraton:");
        pool.dumpItemDistribution();
        policy.checkAndApplyOn(pool.getPoolContents());
        System.out.println("\nResulting configuraton:");
        pool.dumpItemDistribution();
        
        assertEquals(pool.getItemsForContainer("nodeA"), ImmutableSet.of("item1", "item2"));
        assertEquals(pool.getItemsForContainer("nodeB"), ImmutableSet.of("item3", "item4"));
    }
    
    @Test
    public void testSimpleBalancing() {
        MockWorkerPool pool = new MockWorkerPool(
                containers(
                        "nodeA", 20, 50,
                        "nodeB", 20, 50),
                items(
                        "item1", "nodeA", 0,
                        "item2", "nodeB", 40,
                        "item3", "nodeB", 20,
                        "item4", "nodeB", 20)
        );
        
        BalancingStrategy<String, String> policy = new BalancingStrategy<String, String>("Test", pool);
        System.out.println("\nStarting configuraton:");
        pool.dumpItemDistribution();
        policy.checkAndApplyOn(pool.getPoolContents());
        System.out.println("\nResulting configuraton:");
        pool.dumpItemDistribution();
        
        assertEquals(pool.getItemsForContainer("nodeA"), ImmutableSet.of("item1", "item2"));
        assertEquals(pool.getItemsForContainer("nodeB"), ImmutableSet.of("item3", "item4"));
    }
    
    @Test
    public void testAdjustedItemNotMoved() {
        MockWorkerPool pool = new MockWorkerPool(
                containers(
                        "nodeA", 20, 50,
                        "nodeB", 20, 50),
                items(
                        "item1", "nodeA", 0,
                        "item2", "nodeB", -40,
                        "item3", "nodeB", 20,
                        "item4", "nodeB", 20)
        );
        
        BalancingStrategy<String, String> policy = new BalancingStrategy<String, String>("Test", pool);
        System.out.println("\nStarting configuraton:");
        pool.dumpItemDistribution();
        policy.checkAndApplyOn(pool.getPoolContents());
        System.out.println("\nResulting configuraton:");
        pool.dumpItemDistribution();
        
        assertEquals(pool.getItemsForContainer("nodeA"), ImmutableSet.of("item1", "item3", "item4"));
        assertEquals(pool.getItemsForContainer("nodeB"), ImmutableSet.of("item2"));
    }
    
    @Test
    public void testMultiMoveBalancing() {
        MockWorkerPool pool = new MockWorkerPool(
                containers(
                        "nodeA", 20, 50,
                        "nodeB", 20, 50),
                items(
                        "item1", "nodeB", 10,
                        "item2", "nodeB", 10,
                        "item3", "nodeB", 10,
                        "item4", "nodeB", 10,
                        "item5", "nodeB", 10,
                        "item6", "nodeB", 10,
                        "item7", "nodeB", 10,
                        "item8", "nodeB", 10,
                        "item9", "nodeB", 10,
                        "item10", "nodeB", 10)
        );
        
        BalancingStrategy<String, String> policy = new BalancingStrategy<String, String>("Test", pool);
        System.out.println("\nStarting configuraton:");
        pool.dumpItemDistribution();
        policy.checkAndApplyOn(pool.getPoolContents());
        System.out.println("\nResulting configuraton:");
        pool.dumpItemDistribution();
        
        assertEquals(pool.getItemsForContainer("nodeA"), ImmutableSet.of("item1", "item2", "item3", "item4", "item5"));
        assertEquals(pool.getItemsForContainer("nodeB"), ImmutableSet.of("item6", "item7", "item8", "item9", "item10"));
    }
    
    // TODO: testMixedContainerThresholds()
    // TODO: testNoBalancingBelowNodeWatermark()
    // TODO: testLocationConstraint()
    
}
