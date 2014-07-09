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

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.Lists;

public class LoadBalancingPolicyConcurrencyTest extends AbstractLoadBalancingPolicyTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicyConcurrencyTest.class);

    private static final double WORKRATE_JITTER = 2d;
    private static final int NUM_CONTAINERS = 20;
    private static final int WORKRATE_UPDATE_PERIOD_MS = 1000;
    
    private ScheduledExecutorService scheduledExecutor;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void before() {
        scheduledExecutor = Executors.newScheduledThreadPool(10);
        super.before();
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void after() {
        if (scheduledExecutor != null) scheduledExecutor.shutdownNow();
        super.after();
    }
    
    @Test
    public void testSimplePeriodicWorkrateUpdates() {
        List<MockItemEntity> items = Lists.newArrayList();
        List<MockContainerEntity> containers = Lists.newArrayList();
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 10, 30));
        }
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            newItemWithPeriodicWorkrates(app, containers.get(0), "item"+i, 20);
        }

        assertWorkratesEventually(containers, items, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER);
    }
    
    @Test
    public void testConcurrentlyAddContainers() {
        final Queue<MockContainerEntity> containers = new ConcurrentLinkedQueue<MockContainerEntity>();
        final List<MockItemEntity> items = Lists.newArrayList();
        
        containers.add(newContainer(app, "container-orig", 10, 30));
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            items.add(newItemWithPeriodicWorkrates(app, containers.iterator().next(), "item"+i, 20));
        }
        for (int i = 0; i < NUM_CONTAINERS-1; i++) {
            final int index = i;
            scheduledExecutor.submit(new Callable<Void>() {
                @Override public Void call() {
                    containers.add(newContainer(app, "container"+index, 10, 30));
                    return null;
                }});
        }

        assertWorkratesEventually(containers, items, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER);
    }
    
    @Test
    public void testConcurrentlyAddItems() {
        final Queue<MockItemEntity> items = new ConcurrentLinkedQueue<MockItemEntity>();
        final List<MockContainerEntity> containers = Lists.newArrayList();
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 10, 30));
        }
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            final int index = i;
            scheduledExecutor.submit(new Callable<Void>() {
                @Override public Void call() {
                    items.add(newItemWithPeriodicWorkrates(app, containers.get(0), "item"+index, 20));
                    return null;
                }});
        }
        assertWorkratesEventually(containers, items, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER);
    }
    
    // TODO Got IndexOutOfBoundsException from containers.last()
    @Test(groups="WIP", invocationCount=100)
    public void testConcurrentlyRemoveContainers() {
        List<MockItemEntity> items = Lists.newArrayList();
        final List<MockContainerEntity> containers = Lists.newArrayList();
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 15, 45));
        }
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            items.add(newItemWithPeriodicWorkrates(app, containers.get(i), "item"+i, 20));
        }
        
        final List<MockContainerEntity> containersToStop = Lists.newArrayList();
        for (int i = 0; i < NUM_CONTAINERS/2; i++) {
            containersToStop.add(containers.remove(0));
        }
        for (final MockContainerEntity containerToStop : containersToStop) {
            scheduledExecutor.submit(new Callable<Void>() {
                @Override public Void call() {
                    try {
                        containerToStop.offloadAndStop(containers.get(containers.size()-1));
                        Entities.unmanage(containerToStop);
                    } catch (Throwable t) {
                        LOG.error("Error stopping container "+containerToStop, t);
                    }
                    return null;
                }});
        }
        
        assertWorkratesEventually(containers, items, Collections.nCopies((int)(NUM_CONTAINERS/2), 40d), WORKRATE_JITTER*2);
    }
    
    @Test(groups="WIP")
    public void testConcurrentlyRemoveItems() {
        List<MockItemEntity> items = Lists.newArrayList();
        List<MockContainerEntity> containers = Lists.newArrayList();
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 15, 45));
        }
        for (int i = 0; i < NUM_CONTAINERS*2; i++) {
            items.add(newItemWithPeriodicWorkrates(app, containers.get(i%NUM_CONTAINERS), "item"+i, 20));
        }
        // should now have item0 and item{0+NUM_CONTAINERS} on container0, etc
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            // not removing consecutive items as that would leave it balanced!
            int indexToStop = (i < NUM_CONTAINERS/2) ? NUM_CONTAINERS : 0; 
            final MockItemEntity itemToStop = items.remove(indexToStop);
            scheduledExecutor.submit(new Callable<Void>() {
                @Override public Void call() {
                    try {
                        itemToStop.stop();
                        Entities.unmanage(itemToStop);
                    } catch (Throwable t) {
                        LOG.error("Error stopping item "+itemToStop, t);
                    }
                    return null;
                }});
        }
        
        assertWorkratesEventually(containers, items, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER);
    }
    
    protected MockItemEntity newItemWithPeriodicWorkrates(TestApplication app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = newItem(app, container, name, workrate);
        scheduleItemWorkrateUpdates(item, workrate, WORKRATE_JITTER);
        return item;
    }
    
    private void scheduleItemWorkrateUpdates(final MockItemEntity item, final double workrate, final double jitter) {
        final AtomicReference<Future<?>> futureRef = new AtomicReference<Future<?>>();
        Future<?> future = scheduledExecutor.scheduleAtFixedRate(
                new Runnable() {
                    @Override public void run() {
                        if (item.isStopped() && futureRef.get() != null) {
                            futureRef.get().cancel(true);
                            return;
                        }
                        double jitteredWorkrate = workrate + (random.nextDouble()*jitter*2 - jitter);
                        ((EntityLocal)item).setAttribute(TEST_METRIC, (int) Math.max(0, jitteredWorkrate));
                    }
                },
                0, WORKRATE_UPDATE_PERIOD_MS, TimeUnit.MILLISECONDS);
        futureRef.set(future);
    }
}
