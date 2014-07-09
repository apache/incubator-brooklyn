/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.loadbalancing;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class LoadBalancingPolicyTest extends AbstractLoadBalancingPolicyTest {
    
    // Expect no balancing to occur as container A isn't above the high threshold.
    @Test
    public void testNoopWhenWithinThresholds() {
        MockContainerEntity containerA = newContainer(app, "A", 10, 100);
        MockContainerEntity containerB = newContainer(app, "B", 20, 60);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 10);
        MockItemEntity item3 = newItem(app, containerA, "3", 10);
        MockItemEntity item4 = newItem(app, containerA, "4", 10);

        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4), 
                ImmutableList.of(40d, 0d));
    }
    
    @Test
    public void testNoopWhenAlreadyBalanced() {
        MockContainerEntity containerA = newContainer(app, "A", 20, 80);
        MockContainerEntity containerB = newContainer(app, "B", 20, 80);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 30);
        MockItemEntity item3 = newItem(app, containerB, "3", 20);
        MockItemEntity item4 = newItem(app, containerB, "4", 20);

        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4), 
                ImmutableList.of(40d, 40d));
        assertEquals(containerA.getBalanceableItems(), ImmutableSet.of(item1, item2));
        assertEquals(containerB.getBalanceableItems(), ImmutableSet.of(item3, item4));
    }
    
    // Expect 20 units of workload to be migrated from hot container (A) to cold (B).
    @Test
    public void testSimpleBalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 25);
        MockContainerEntity containerB = newContainer(app, "B", 20, 60);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 10);
        MockItemEntity item3 = newItem(app, containerA, "3", 10);
        MockItemEntity item4 = newItem(app, containerA, "4", 10);

        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4), 
                ImmutableList.of(20d, 20d));
    }
    
    @Test
    public void testSimpleBalancing2() {
        MockContainerEntity containerA = newContainer(app, "A", 20, 40);
        MockContainerEntity containerB = newContainer(app, "B", 20, 40);
        MockItemEntity item1 = newItem(app, containerA, "1", 0);
        MockItemEntity item2 = newItem(app, containerB, "2", 40);
        MockItemEntity item3 = newItem(app, containerB, "3", 20);
        MockItemEntity item4 = newItem(app, containerB, "4", 20);

        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4), 
                ImmutableList.of(40d, 40d));
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
        MockContainerEntity containerA = newContainer(app, "A", 20, 50);
        MockContainerEntity containerB = newContainer(app, "B", 20, 50);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 10);
        MockItemEntity item3 = newItem(app, containerA, "3", 10);
        MockItemEntity item4 = newItem(app, containerA, "4", 10);
        MockItemEntity item5 = newItem(app, containerA, "5", 10);
        MockItemEntity item6 = newItem(app, containerA, "6", 10);
        MockItemEntity item7 = newItem(app, containerA, "7", 10);
        MockItemEntity item8 = newItem(app, containerA, "8", 10);
        MockItemEntity item9 = newItem(app, containerA, "9", 10);
        MockItemEntity item10 = newItem(app, containerA, "10", 10);

        // non-deterministic which items will be moved; but can assert how many (given they all have same workrate)
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4, item5, item6, item7, item8, item9, item10), 
                ImmutableList.of(50d, 50d));
        assertEquals(containerA.getBalanceableItems().size(), 5);
        assertEquals(containerB.getBalanceableItems().size(), 5);
    }
    
    @Test
    public void testRebalanceWhenWorkratesChange() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50);
        MockContainerEntity containerB = newContainer(app, "B", 10, 50);
        MockItemEntity item1 = newItem(app, containerA, "1", 0);
        MockItemEntity item2 = newItem(app, containerA, "2", 0);

        ((EntityLocal)item1).setAttribute(MockItemEntity.TEST_METRIC, 40);
        ((EntityLocal)item2).setAttribute(MockItemEntity.TEST_METRIC, 40);
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2), 
                ImmutableList.of(40d, 40d));
    }
    
    // Expect no balancing to occur in hot pool (2 containers over-threshold at 40).
    // On addition of new container, expect hot containers to offload 10 each.
    @Test
    public void testAddContainerWhenHot() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 30);
        MockContainerEntity containerB = newContainer(app, "B", 10, 30);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 10);
        MockItemEntity item3 = newItem(app, containerA, "3", 10);
        MockItemEntity item4 = newItem(app, containerA, "4", 10);
        MockItemEntity item5 = newItem(app, containerB, "5", 10);
        MockItemEntity item6 = newItem(app, containerB, "6", 10);
        MockItemEntity item7 = newItem(app, containerB, "7", 10);
        MockItemEntity item8 = newItem(app, containerB, "8", 10);
        // Both containers are over-threshold at this point; should not rebalance.
        
        MockContainerEntity containerC = newAsyncContainer(app, "C", 10, 30, CONTAINER_STARTUP_DELAY_MS);
        // New container allows hot ones to offload work.
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB, containerC), 
                ImmutableList.of(item1, item2, item3, item4, item5, item6, item7, item8), 
                ImmutableList.of(30d, 30d, 20d));
    }

    // On addition of new container, expect no rebalancing to occur as no existing container is hot.
    @Test
    public void testAddContainerWhenCold() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50);
        MockContainerEntity containerB = newContainer(app, "B", 10, 50);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 10);
        MockItemEntity item3 = newItem(app, containerA, "3", 10);
        MockItemEntity item4 = newItem(app, containerA, "4", 10);
        MockItemEntity item5 = newItem(app, containerB, "5", 10);
        MockItemEntity item6 = newItem(app, containerB, "6", 10);
        MockItemEntity item7 = newItem(app, containerB, "7", 10);
        MockItemEntity item8 = newItem(app, containerB, "8", 10);
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4, item5, item6, item7, item8), 
                ImmutableList.of(40d, 40d));
        
        MockContainerEntity containerC = newAsyncContainer(app, "C", 10, 50, CONTAINER_STARTUP_DELAY_MS);

        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB, containerC), 
                ImmutableList.of(item1, item2, item3, item4, item5, item6, item7, item8), 
                ImmutableList.of(40d, 40d, 0d));
    }
    
    // Expect no balancing to occur in cool pool (2 containers under-threshold at 30).
    // On addition of new item, expect over-threshold container (A) to offload 20 to B.
    @Test
    public void testAddItem() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50);
        MockContainerEntity containerB = newContainer(app, "B", 10, 50);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 10);
        MockItemEntity item3 = newItem(app, containerA, "3", 10);
        MockItemEntity item4 = newItem(app, containerB, "4", 10);
        MockItemEntity item5 = newItem(app, containerB, "5", 10);
        MockItemEntity item6 = newItem(app, containerB, "6", 10);
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4, item5, item6), 
                ImmutableList.of(30d, 30d));
        
        MockItemEntity item7 = newItem(app, containerA, "7", 40);
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4, item5, item6), 
                ImmutableList.of(50d, 50d));
    }
    
    // FIXME Failed in build repeatedly (e.g. #1035), but couldn't reproduce locally yet with invocationCount=100
    @Test(groups="WIP")
    public void testRemoveContainerCausesRebalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 30);
        MockContainerEntity containerB = newContainer(app, "B", 10, 30);
        MockContainerEntity containerC = newContainer(app, "C", 10, 30);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);
        MockItemEntity item2 = newItem(app, containerA, "2", 10);
        MockItemEntity item3 = newItem(app, containerB, "3", 10);
        MockItemEntity item4 = newItem(app, containerB, "4", 10);
        MockItemEntity item5 = newItem(app, containerC, "5", 10);
        MockItemEntity item6 = newItem(app, containerC, "6", 10);

        Entities.unmanage(containerC);
        item5.move(containerA);
        item6.move(containerA);
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4, item5, item6), 
                ImmutableList.of(30d, 30d));
    }

    @Test
    public void testRemoveItemCausesRebalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 30);
        MockContainerEntity containerB = newContainer(app, "B", 10, 30);
        MockItemEntity item1 = newItem(app, containerA, "1", 30);
        MockItemEntity item2 = newItem(app, containerB, "2", 20);
        MockItemEntity item3 = newItem(app, containerB, "3", 20);
        
        item1.stop();
        Entities.unmanage(item1);
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3), 
                ImmutableList.of(20d, 20d));
    }

    @Test
    public void testRebalancesAfterManualMove() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50);
        MockContainerEntity containerB = newContainer(app, "B", 10, 50);
        MockItemEntity item1 = newItem(app, containerA, "1", 20);
        MockItemEntity item2 = newItem(app, containerA, "2", 20);
        MockItemEntity item3 = newItem(app, containerB, "3", 20);
        MockItemEntity item4 = newItem(app, containerB, "4", 20);

        // Move everything onto containerA, and expect it to be automatically re-balanced
        item3.move(containerA);
        item4.move(containerA);

        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3, item4), 
                ImmutableList.of(40d, 40d));
    }
    
    @Test
    public void testModelIncludesItemsAndContainersStartedBeforePolicyCreated() {
        pool.removePolicy(policy);
        policy.destroy();
        
        // Set-up containers and items.
        final MockContainerEntity containerA = newContainer(app, "A", 10, 100);
        MockItemEntity item1 = newItem(app, containerA, "1", 10);

        policy = new LoadBalancingPolicy(MutableMap.of(), TEST_METRIC, model);
        pool.addPolicy(policy);
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(model.getContainerWorkrates(), ImmutableMap.of(containerA, 10d));
            }
        });
    }
    
    @Test
    public void testLockedItemsNotMoved() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50);
        MockContainerEntity containerB = newContainer(app, "B", 10, 50);
        MockItemEntity item1 = newLockedItem(app, containerA, "1", 40);
        MockItemEntity item2 = newLockedItem(app, containerA, "2", 40);

        assertWorkratesContinually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2), 
                ImmutableList.of(80d, 0d));
    }
    
    @Test
    public void testLockedItemsContributeToOverloadedMeasurements() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50);
        MockContainerEntity containerB = newContainer(app, "B", 10, 50);
        MockItemEntity item1 = newLockedItem(app, containerA, "1", 40);
        MockItemEntity item2 = newItem(app, containerA, "2", 25);
        MockItemEntity item3 = newItem(app, containerA, "3", 25);
        
        assertWorkratesEventually(
                ImmutableList.of(containerA, containerB),
                ImmutableList.of(item1, item2, item3), 
                ImmutableList.of(40d, 50d));
    }
    
    @Test
    public void testOverloadedLockedItemsPreventMoreWorkEnteringContainer() throws Exception {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, "A", 10, 50);
        MockContainerEntity containerB = newContainer(app, "B", 10, 50);
        MockItemEntity item1 = newLockedItem(app, containerA, "1", 50);
        Thread.sleep(1); // increase chances of item1's workrate having been received first
        MockItemEntity item2 = newItem(app, containerB, "2", 30);
        MockItemEntity item3 = newItem(app, containerB, "3", 30);
        
        assertWorkratesContinually(
                ImmutableList.of(containerA, containerB), 
                ImmutableList.of(item1, item2, item3), 
                ImmutableList.of(50d, 60d));
    }
    
    @Test
    public void testPolicyUpdatesModel() {
        final MockContainerEntity containerA = newContainer(app, "A", 10, 20);
        final MockContainerEntity containerB = newContainer(app, "B", 11, 21);
        final MockItemEntity item1 = newItem(app, containerA, "1", 12);
        final MockItemEntity item2 = newItem(app, containerB, "2", 13);
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(model.getPoolSize(), 2);
                assertEquals(model.getPoolContents(), ImmutableSet.of(containerA, containerB));
                assertEquals(model.getItemWorkrate(item1), 12d);
                assertEquals(model.getItemWorkrate(item2), 13d);
                
                assertEquals(model.getParentContainer(item1), containerA);
                assertEquals(model.getParentContainer(item2), containerB);
                assertEquals(model.getContainerWorkrates(), ImmutableMap.of(containerA, 12d, containerB, 13d));
                
                assertEquals(model.getPoolLowThreshold(), 10+11d);
                assertEquals(model.getPoolHighThreshold(), 20+21d);
                assertEquals(model.getCurrentPoolWorkrate(), 12+13d);
                assertFalse(model.isHot());
                assertFalse(model.isCold());
            }
        });
    }
}
