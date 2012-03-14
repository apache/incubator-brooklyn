package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.test.entity.TestApplication

import com.google.common.collect.ImmutableMap

public class LoadBalancingPolicyTest extends AbstractLoadBalancingPolicyTest {
    
    // Expect no balancing to occur as container A isn't above the high threshold.
    @Test
    public void testNoopWhenWithinThresholds() {
        MockContainerEntity containerA = newContainer(app, "A", 10, 100)
        MockContainerEntity containerB = newContainer(app, "B", 20, 60)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerA, "3", 10)
        MockItemEntity item4 = newItem(app, containerA, "4", 10)

        assertWorkratesEventually([containerA, containerB], [40d, 0d])
    }
    
    @Test
    public void testNoopWhenAlreadyBalanced() {
        MockContainerEntity containerA = newContainer(app, "A", 20, 80)
        MockContainerEntity containerB = newContainer(app, "B", 20, 80)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 30)
        MockItemEntity item3 = newItem(app, containerB, "3", 20)
        MockItemEntity item4 = newItem(app, containerB, "4", 20)

        assertWorkratesEventually([containerA, containerB], [40d, 40d])
        assertEquals(containerA.getBalanceableItems(), [item1, item2] as Set)
        assertEquals(containerB.getBalanceableItems(), [item3, item4] as Set)
    }
    
    // Expect 20 units of workload to be migrated from hot container (A) to cold (B).
    @Test
    public void testSimpleBalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 25)
        MockContainerEntity containerB = newContainer(app, "B", 20, 60)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerA, "3", 10)
        MockItemEntity item4 = newItem(app, containerA, "4", 10)

        assertWorkratesEventually([containerA, containerB], [20d, 20d])
    }
    
    @Test
    public void testSimpleBalancing2() {
        MockContainerEntity containerA = newContainer(app, "A", 20, 40)
        MockContainerEntity containerB = newContainer(app, "B", 20, 40)
        MockItemEntity item1 = newItem(app, containerA, "1", 0)
        MockItemEntity item2 = newItem(app, containerB, "2", 40)
        MockItemEntity item3 = newItem(app, containerB, "3", 20)
        MockItemEntity item4 = newItem(app, containerB, "4", 20)

        assertWorkratesEventually([containerA, containerB], [40d, 40d])
    }
    
//    @Test
//    public void testAdjustedItemNotMoved() {
//        MockBalancingModel pool = new MockBalancingModel(
//                containers(
//                        containerA, 20, 50,
//                        containerB, 20, 50),
//                items(
//                        "item1", containerA, 0,
//                        "item2", containerB, -40,
//                        "item3", containerB, 20,
//                        "item4", containerB, 20)
//        );
//        
//        BalancingStrategy<String, String> policy = new BalancingStrategy<String, String>("Test", pool);
//        policy.rebalance();
//        
//        assertEquals((Object)pool.getItemsForContainer(containerA), ImmutableSet.of("item1", "item3", "item4"), pool.itemDistributionToString());
//        assertEquals((Object)pool.getItemsForContainer(containerB), ImmutableSet.of("item2"), pool.itemDistributionToString());
//    }

    @Test
    public void testMultiMoveBalancing() {
        MockContainerEntity containerA = newContainer(app, "A", 20, 50)
        MockContainerEntity containerB = newContainer(app, "B", 20, 50)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerA, "3", 10)
        MockItemEntity item4 = newItem(app, containerA, "4", 10)
        MockItemEntity item5 = newItem(app, containerA, "5", 10)
        MockItemEntity item6 = newItem(app, containerA, "6", 10)
        MockItemEntity item7 = newItem(app, containerA, "7", 10)
        MockItemEntity item8 = newItem(app, containerA, "8", 10)
        MockItemEntity item9 = newItem(app, containerA, "9", 10)
        MockItemEntity item10 = newItem(app, containerA, "10", 10)

        // non-deterministic which items will be moved; but can assert how many (given they all have same workrate)
        assertWorkratesEventually([containerA, containerB], [50d, 50d])
        assertEquals(containerA.getBalanceableItems().size(), 5)
        assertEquals(containerB.getBalanceableItems().size(), 5)
    }
    
    @Test
    public void testRebalanceWhenWorkratesChange() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50)
        MockContainerEntity containerB = newContainer(app, "B", 10, 50)
        MockItemEntity item1 = newItem(app, containerA, "1", 0)
        MockItemEntity item2 = newItem(app, containerA, "2", 0)

        item1.setAttribute(MockItemEntity.TEST_METRIC, 40)
        item2.setAttribute(MockItemEntity.TEST_METRIC, 40)
        
        assertWorkratesEventually([containerA, containerB], [40d, 40d])
    }
    
    // Expect no balancing to occur in hot pool (2 containers over-threshold at 40).
    // On addition of new container, expect hot containers to offload 10 each.
    @Test
    public void testAddContainerWhenHot() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 30)
        MockContainerEntity containerB = newContainer(app, "B", 10, 30)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerA, "3", 10)
        MockItemEntity item4 = newItem(app, containerA, "4", 10)
        MockItemEntity item5 = newItem(app, containerB, "5", 10)
        MockItemEntity item6 = newItem(app, containerB, "6", 10)
        MockItemEntity item7 = newItem(app, containerB, "7", 10)
        MockItemEntity item8 = newItem(app, containerB, "8", 10)
        // Both containers are over-threshold at this point; should not rebalance.
        
        MockContainerEntity containerC = newAsyncContainer(app, "C", 10, 30, CONTAINER_STARTUP_DELAY_MS)
        // New container allows hot ones to offload work.
        
        assertWorkratesEventually([containerA, containerB, containerC], [30d, 30d, 20d])
    }

    // On addition of new container, expect no rebalancing to occur as no existing container is hot.
    @Test
    public void testAddContainerWhenCold() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50)
        MockContainerEntity containerB = newContainer(app, "B", 10, 50)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerA, "3", 10)
        MockItemEntity item4 = newItem(app, containerA, "4", 10)
        MockItemEntity item5 = newItem(app, containerB, "5", 10)
        MockItemEntity item6 = newItem(app, containerB, "6", 10)
        MockItemEntity item7 = newItem(app, containerB, "7", 10)
        MockItemEntity item8 = newItem(app, containerB, "8", 10)
        
        assertWorkratesEventually([containerA, containerB], [40d, 40d])
        
        MockContainerEntity containerC = newAsyncContainer(app, "C", 10, 50, CONTAINER_STARTUP_DELAY_MS)

        assertWorkratesEventually([containerA, containerB, containerC], [40d, 40d, 0d])
    }
    
    // Expect no balancing to occur in cool pool (2 containers under-threshold at 30).
    // On addition of new item, expect over-threshold container (A) to offload 20 to B.
    @Test
    public void testAddItem() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50)
        MockContainerEntity containerB = newContainer(app, "B", 10, 50)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerA, "3", 10)
        MockItemEntity item4 = newItem(app, containerB, "4", 10)
        MockItemEntity item5 = newItem(app, containerB, "5", 10)
        MockItemEntity item6 = newItem(app, containerB, "6", 10)
        
        assertWorkratesEventually([containerA, containerB], [30d, 30d])
        
        MockItemEntity item7 = newItem(app, containerA, "7", 40)
        
        assertWorkratesEventually([containerA, containerB], [50d, 50d])
    }
    
    // FIXME Failed in build repeatedly (e.g. #1035), but couldn't reproduce locally yet with invocationCount=100
    @Test(groups="WIP")
    public void testRemoveContainerCausesRebalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 30)
        MockContainerEntity containerB = newContainer(app, "B", 10, 30)
        MockContainerEntity containerC = newContainer(app, "C", 10, 30)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)
        MockItemEntity item2 = newItem(app, containerA, "2", 10)
        MockItemEntity item3 = newItem(app, containerB, "3", 10)
        MockItemEntity item4 = newItem(app, containerB, "4", 10)
        MockItemEntity item5 = newItem(app, containerC, "5", 10)
        MockItemEntity item6 = newItem(app, containerC, "6", 10)

        app.getManagementContext().unmanage(containerC)
        item5.move(containerA)
        item6.move(containerA)
        
        assertWorkratesEventually([containerA, containerB], [30d, 30d])
    }

    @Test
    public void testRemoveItemCausesRebalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 30)
        MockContainerEntity containerB = newContainer(app, "B", 10, 30)
        MockItemEntity item1 = newItem(app, containerA, "1", 30)
        MockItemEntity item2 = newItem(app, containerB, "2", 20)
        MockItemEntity item3 = newItem(app, containerB, "3", 20)
        
        item1.stop()
        app.getManagementContext().unmanage(item1)
        
        assertWorkratesEventually([containerA, containerB], [20d, 20d])
    }

    @Test
    public void testRebalancesAfterManualMove() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50)
        MockContainerEntity containerB = newContainer(app, "B", 10, 50)
        MockItemEntity item1 = newItem(app, containerA, "1", 20)
        MockItemEntity item2 = newItem(app, containerA, "2", 20)
        MockItemEntity item3 = newItem(app, containerB, "3", 20)
        MockItemEntity item4 = newItem(app, containerB, "4", 20)

        // Move everything onto containerA, and expect it to be automatically re-balanced
        item3.move(containerA)
        item4.move(containerA)

        assertWorkratesEventually([containerA, containerB], [40d, 40d])
    }
    
    @Test
    public void testModelIncludesItemsAndContainersStartedBeforePolicyCreated() {
        pool.removePolicy(policy)
        policy.destroy()
        
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 100)
        MockItemEntity item1 = newItem(app, containerA, "1", 10)

        policy = new LoadBalancingPolicy([:], TEST_METRIC, model)
        pool.addPolicy(policy)
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(model.getContainerWorkrates(), ImmutableMap.of(containerA, 10d))
        }
    }
    
    @Test
    public void testLockedItemsNotMoved() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50)
        MockContainerEntity containerB = newContainer(app, "B", 10, 50)
        MockItemEntity item1 = newLockedItem(app, containerA, "1", 40)
        MockItemEntity item2 = newLockedItem(app, containerA, "2", 40)

        assertWorkratesContinually([containerA, containerB], [80d, 0d])
    }
    
    @Test
    public void testLockedItemsContributeToOverloadedMeasurements() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50)
        MockContainerEntity containerB = newContainer(app, "B", 10, 50)
        MockItemEntity item1 = newLockedItem(app, containerA, "1", 40)
        MockItemEntity item2 = newItem(app, containerA, "2", 25)
        MockItemEntity item3 = newItem(app, containerA, "3", 25)
        
        assertWorkratesEventually([containerA, containerB], [40d, 50d])
    }
    
    @Test
    public void testOverloadedLockedItemsPreventMoreWorkEnteringContainer() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50)
        MockContainerEntity containerB = newContainer(app, "B", 10, 50)
        MockItemEntity item1 = newLockedItem(app, containerA, "1", 50)
        Thread.sleep(1); // increase chances of item1's workrate having been received first
        MockItemEntity item2 = newItem(app, containerB, "2", 30)
        MockItemEntity item3 = newItem(app, containerB, "3", 30)
        
        assertWorkratesContinually([containerA, containerB], [50d, 60d])
    }
    
    @Test
    public void testPolicyUpdatesModel() {
        MockContainerEntity containerA = newContainer(app, "A", 10, 20)
        MockContainerEntity containerB = newContainer(app, "B", 11, 21)
        MockItemEntity item1 = newItem(app, containerA, "1", 12)
        MockItemEntity item2 = newItem(app, containerB, "2", 13)
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(model.getPoolSize(), 2)
            assertEquals(model.getPoolContents(), [containerA,containerB] as Set)
            assertEquals(model.getItemWorkrate(item1), 12d)
            assertEquals(model.getItemWorkrate(item2), 13d)
            
            assertEquals(model.getParentContainer(item1), containerA)
            assertEquals(model.getParentContainer(item2), containerB)
            assertEquals(model.getContainerWorkrates(), [(containerA):12d, (containerB):13d])
            
            assertEquals(model.getPoolLowThreshold(), 10+11d)
            assertEquals(model.getPoolHighThreshold(), 20+21d)
            assertEquals(model.getCurrentPoolWorkrate(), 12+13d)
            assertFalse(model.isHot())
            assertFalse(model.isCold())
        }
    }
}
