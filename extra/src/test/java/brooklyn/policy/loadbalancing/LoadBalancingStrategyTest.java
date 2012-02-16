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
        policy.rebalance();
        
        assertEquals((Object)pool.getItemsForContainer("nodeA"), ImmutableSet.of("item1", "item2"), pool.itemDistributionToString());
        assertEquals((Object)pool.getItemsForContainer("nodeB"), ImmutableSet.of("item3", "item4"), pool.itemDistributionToString());
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
        policy.rebalance();
        
        assertEquals((Object)pool.getItemsForContainer("nodeA"), ImmutableSet.of("item1", "item2"), pool.itemDistributionToString());
        assertEquals((Object)pool.getItemsForContainer("nodeB"), ImmutableSet.of("item3", "item4"), pool.itemDistributionToString());
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
        policy.rebalance();
        
        assertEquals((Object)pool.getItemsForContainer("nodeA"), ImmutableSet.of("item1", "item3", "item4"), pool.itemDistributionToString());
        assertEquals((Object)pool.getItemsForContainer("nodeB"), ImmutableSet.of("item2"), pool.itemDistributionToString());
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
        policy.rebalance();
        
        // non-deterministic which items will be moved; but can assert how many (given they all have same workrate)
        assertEquals(pool.getItemsForContainer("nodeA").size(), 5, pool.itemDistributionToString());
        assertEquals(pool.getItemsForContainer("nodeB").size(), 5, pool.itemDistributionToString());
    }
    
    // TODO: test heterogeneous thresholds
    // TODO: test >2 containers
    // TODO: test location constraint
    
}
