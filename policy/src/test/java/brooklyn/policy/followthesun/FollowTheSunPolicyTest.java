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
package brooklyn.policy.followthesun;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.loadbalancing.MockContainerEntity;
import brooklyn.policy.loadbalancing.MockItemEntity;
import brooklyn.policy.loadbalancing.Movable;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class FollowTheSunPolicyTest extends AbstractFollowTheSunPolicyTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(FollowTheSunPolicyTest.class);

    @Test
    public void testPolicyUpdatesModel() {
        final MockContainerEntity containerA = newContainer(app, loc1, "A");
        final MockItemEntity item1 = newItem(app, containerA, "1");
        final MockItemEntity item2 = newItem(app, containerA, "2");
        ((EntityLocal)item2).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item1, 11d));
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertEquals(ImmutableSet.of(item1, item2), model.getItems());
                assertEquals(model.getItemContainer(item1), containerA);
                assertEquals(model.getItemLocation(item1), loc1);
                assertEquals(model.getContainerLocation(containerA), loc1);
                assertEquals(model.getDirectSendsToItemByLocation(), ImmutableMap.of(item2, ImmutableMap.of(loc1, 11d)));
            }});
    }
    
    @Test
    public void testPolicyAcceptsLocationFinder() {
        pool.removePolicy(policy);
        
        Function<Entity, Location> customLocationFinder = new Function<Entity, Location>() {
            @Override public Location apply(Entity input) {
                return new SimulatedLocation(MutableMap.of("name", "custom location for "+input));
            }};
        
        FollowTheSunPolicy customPolicy = new FollowTheSunPolicy(
                MutableMap.of("minPeriodBetweenExecs", 0, "locationFinder", customLocationFinder), 
                MockItemEntity.ITEM_USAGE_METRIC, 
                model, 
                FollowTheSunParameters.newDefault());
        
        pool.addPolicy(customPolicy);
        
        final MockContainerEntity containerA = newContainer(app, loc1, "A");
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertEquals(model.getContainerLocation(containerA).getDisplayName(), "custom location for "+containerA);
            }});
    }
    
    @Test
    public void testNoopBalancing() throws Exception {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1", Collections.<Entity, Double>emptyMap());
        MockItemEntity item2 = newItem(app, containerB, "2", Collections.<Entity, Double>emptyMap());
        
        Thread.sleep(SHORT_WAIT_MS);
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1), containerB, ImmutableList.of(item2)));
    }
    
    @Test
    public void testMovesItemToFollowDemand() throws Exception {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerB, "2");

        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 100d));
        
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.<MockItemEntity>of(), containerB, ImmutableList.of(item1, item2)));
    }
    
    @Test
    public void testNoopIfDemandIsTiny() throws Exception {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerB, "2");

        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 0.1d));
        
        Thread.sleep(SHORT_WAIT_MS);
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1), containerB, ImmutableList.of(item2)));
    }
    
    @Test
    public void testNoopIfDemandIsSimilarToCurrentLocation() throws Exception {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerA, "2");
        MockItemEntity item3 = newItem(app, containerB, "3");
        
        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 100d, item3, 100.1d));
        
        Thread.sleep(SHORT_WAIT_MS);
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1, item2), containerB, ImmutableList.of(item3)));
    }
    
    @Test
    public void testMoveDecisionIgnoresDemandFromItself() throws Exception {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerB, "2");
        
        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item1, 100d));
        ((EntityLocal)item2).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 100d));
        
        Thread.sleep(SHORT_WAIT_MS);
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1), containerB, ImmutableList.of(item2)));
    }
    
    @Test
    public void testItemRemovedCausesRecalculationOfOptimalLocation() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerA, "2");
        MockItemEntity item3 = newItem(app, containerB, "3");
        
        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 100d, item3, 1000d));
        
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item2), containerB, ImmutableList.of(item1, item3)));
        
        item3.stop();
        Entities.unmanage(item3);
        
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1, item2), containerB, ImmutableList.<MockItemEntity>of()));
    }
    
    @Test
    public void testItemMovedCausesRecalculationOfOptimalLocationForOtherItems() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerA, "2");
        MockItemEntity item3 = newItem(app, containerB, "3");
        
        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 100d, item3, 1000d));
        
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item2), containerB, ImmutableList.of(item1, item3)));
        
        item3.move(containerA);
        
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1, item2, item3), containerB, ImmutableList.<MockItemEntity>of()));
    }
    
    @Test
    public void testImmovableItemIsNotMoved() {
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newLockedItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerB, "2");
        
        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 100d));
        
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1), containerB, ImmutableList.of(item2)));
    }
    
    @Test
    public void testImmovableItemContributesTowardsLoad() {
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newLockedItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerA, "2");
        
        ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item1, 100d));
        
        assertItemDistributionEventually(ImmutableMap.of(containerA, ImmutableList.of(item1, item2), containerB, ImmutableList.<MockItemEntity>of()));
    }

    // Marked as "Acceptance" due to time-sensitive nature :-(
    @Test(groups={"Integration", "Acceptance"}, invocationCount=20)
    public void testRepeatedRespectsMinPeriodBetweenExecs() throws Exception {
        testRespectsMinPeriodBetweenExecs();
    }
    
    @Test(groups="Integration")
    public void testRespectsMinPeriodBetweenExecs() throws Exception {
        // Failed in jenkins several times, e.g. with event times [2647, 2655] and [1387, 2001].
        // Aled's guess is that there was a delay notifying the listener the first time
        // (which happens async), causing the listener to be notified in rapid 
        // succession. The policy execs probably did happen with a 1000ms separation.
        // 
        // Therefore try up to three times to see if we get the desired separation. If the 
        // minPeriodBetweenExecs wasn't being respected, we'd expect the default 100ms; this 
        // test would therefore hardly ever pass.
        final int MAX_ATTEMPTS = 3;

        final long minPeriodBetweenExecs = 1000;
        final long timePrecision = 250;
        
        pool.removePolicy(policy);
        
        MockContainerEntity containerA = newContainer(app, loc1, "A");
        MockContainerEntity containerB = newContainer(app, loc2, "B");
        MockItemEntity item1 = newItem(app, containerA, "1");
        MockItemEntity item2 = newItem(app, containerB, "2");
        MockItemEntity item3 = newItem(app, containerA, "3");
        
        FollowTheSunPolicy customPolicy = new FollowTheSunPolicy(
            MutableMap.of("minPeriodBetweenExecs", minPeriodBetweenExecs),
            MockItemEntity.ITEM_USAGE_METRIC,
            model,
            FollowTheSunParameters.newDefault());
    
        pool.addPolicy(customPolicy);
        
        // Record times that things are moved, by lisening to the container sensor being set
        final Stopwatch stopwatch = Stopwatch.createStarted();
        
        final List<Long> eventTimes = Lists.newCopyOnWriteArrayList();
        final Semaphore semaphore = new Semaphore(0);
        
        app.subscribe(item1, Movable.CONTAINER, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                long eventTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
                LOG.info("Received {} at {}", event, eventTime);
                eventTimes.add(eventTime);
                semaphore.release();
            }});

        String errmsg = "";
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            // Set the workrate, causing the policy to move item1 to item2's location, and wait for it to happen
            ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item2, 100d));
            assertTrue(semaphore.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals(item1.getAttribute(Movable.CONTAINER), containerB);
            
            // now cause item1 to be moved to item3's location, and wait for it to happen
            ((EntityLocal)item1).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, ImmutableMap.<Entity,Double>of(item3, 100d));
            assertTrue(semaphore.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertEquals(item1.getAttribute(Movable.CONTAINER), containerA);
            
            LOG.info("testRepeatedRespectsMinPeriodBetweenExecs event times: "+eventTimes);
            assertEquals(eventTimes.size(), 2);
            if (eventTimes.get(1) - eventTimes.get(0) > (minPeriodBetweenExecs-timePrecision)) {
                return; // success
            } else {
                errmsg += eventTimes;
                eventTimes.clear();
            }
        }
        
        fail("Event times never had sufficient gap: "+errmsg);
    }
}
