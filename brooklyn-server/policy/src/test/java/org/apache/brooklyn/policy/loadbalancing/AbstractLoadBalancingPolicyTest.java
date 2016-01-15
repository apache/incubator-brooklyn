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
package org.apache.brooklyn.policy.loadbalancing;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AbstractLoadBalancingPolicyTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLoadBalancingPolicyTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100;
    
    public static final AttributeSensor<Integer> TEST_METRIC =
        Sensors.newIntegerSensor("test.metric", "Dummy workrate for test entities");
    
    public static final ConfigKey<Double> LOW_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, TEST_METRIC.getName()+".threshold.low", "desc", 0.0);
    public static final ConfigKey<Double> HIGH_THRESHOLD_CONFIG_KEY = new BasicConfigKey<Double>(Double.class, TEST_METRIC.getName()+".threshold.high", "desc", 0.0);
    
    protected TestApplication app;
    protected SimulatedLocation loc;
    protected BalanceableWorkerPool pool;
    protected DefaultBalanceablePoolModel<Entity, Entity> model;
    protected LoadBalancingPolicy policy;
    protected Group containerGroup;
    protected Group itemGroup;
    protected Random random = new Random();
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        LOG.debug("In AbstractLoadBalancingPolicyTest.before()");
        
        MockItemEntityImpl.totalMoveCount.set(0);
        MockItemEntityImpl.lastMoveTime.set(0);
        
        loc = new SimulatedLocation(MutableMap.of("name", "loc"));
        
        model = new DefaultBalanceablePoolModel<Entity, Entity>("pool-model");
        
        app = TestApplication.Factory.newManagedInstanceForTests();
        containerGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("containerGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockContainerEntity.class)));
        itemGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("itemGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockItemEntity.class)));
        pool = app.createAndManageChild(EntitySpec.create(BalanceableWorkerPool.class));
        pool.setContents(containerGroup, itemGroup);
        policy = new LoadBalancingPolicy(MutableMap.of("minPeriodBetweenExecs", 1), TEST_METRIC, model);
        pool.policies().add(policy);
        app.start(ImmutableList.of(loc));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (policy != null) policy.destroy();
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    // Using this utility, as it gives more info about the workrates of all containers rather than just the one that differs    
    protected void assertWorkrates(Collection<MockContainerEntity> containers, Collection<Double> expectedC, double precision) {
        Iterable<Double> actual = Iterables.transform(containers, new Function<MockContainerEntity, Double>() {
            public Double apply(MockContainerEntity input) {
                return getContainerWorkrate(input);
            }});
        
        List<Double> expected = Lists.newArrayList(expectedC);
        String errMsg = "actual="+actual+"; expected="+expected;
        assertEquals(containers.size(), expected.size(), errMsg);
        for (int i = 0; i < containers.size(); i++) {
            assertEquals(Iterables.get(actual, i), expected.get(i), precision, errMsg);
        }
    }
    
    protected void assertWorkratesEventually(Collection<MockContainerEntity> containers, Iterable<? extends Movable> items, Collection<Double> expected) {
        assertWorkratesEventually(containers, items, expected, 0d);
    }

    /**
     * Asserts that the given container have the given expected workrates (by querying the containers directly).
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertWorkratesEventually(final Collection<MockContainerEntity> containers, final Iterable<? extends Movable> items, final Collection<Double> expected, final double precision) {
        try {
            Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    assertWorkrates(containers, expected, precision);
                }});
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString(containers, items);
            throw new RuntimeException(errMsg, e);
        }
    }

    // Using this utility, as it gives more info about the workrates of all containers rather than just the one that differs    
    protected void assertWorkratesContinually(List<MockContainerEntity> containers, Iterable<? extends Movable> items, List<Double> expected) {
        assertWorkratesContinually(containers, items, expected, 0d);
    }

    /**
     * Asserts that the given containers have the given expected workrates (by querying the containers directly)
     * continuously for SHORT_WAIT_MS.
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertWorkratesContinually(final List<MockContainerEntity> containers, Iterable<? extends Movable> items, final List<Double> expected, final double precision) {
        try {
            Asserts.succeedsContinually(MutableMap.of("timeout", SHORT_WAIT_MS), new Runnable() {
                public void run() {
                    assertWorkrates(containers, expected, precision);
                }});
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString(containers, items);
            throw new RuntimeException(errMsg, e);
        }
    }

    protected String verboseDumpToString(Iterable<MockContainerEntity> containers, Iterable<? extends Movable> items) {
        Iterable<Double> containerRates = Iterables.transform(containers, new Function<MockContainerEntity, Double>() {
            @Override public Double apply(MockContainerEntity input) {
                return (double) input.getWorkrate();
            }});
        
        Map<MockContainerEntity, Set<Movable>> itemDistributionByContainer = Maps.newLinkedHashMap();
        for (MockContainerEntity container : containers) {
            itemDistributionByContainer.put(container, container.getBalanceableItems());
        }
        
        Map<Movable, BalanceableContainer<?>> itemDistributionByItem = Maps.newLinkedHashMap();
        for (Movable item : items) {
            itemDistributionByItem.put(item, item.getAttribute(Movable.CONTAINER));
        }

        String modelItemDistribution = model.itemDistributionToString();
        return "containers="+containers+"; containerRates="+containerRates
                +"; itemDistributionByContainer="+itemDistributionByContainer
                +"; itemDistributionByItem="+itemDistributionByItem
                +"; model="+modelItemDistribution
                +"; totalMoves="+MockItemEntityImpl.totalMoveCount
                +"; lastMoveTime="+Time.makeDateString(MockItemEntityImpl.lastMoveTime.get());
    }
    
    protected MockContainerEntity newContainer(TestApplication app, String name, double lowThreshold, double highThreshold) {
        return newAsyncContainer(app, name, lowThreshold, highThreshold, 0);
    }
    
    /**
     * Creates a new container that will take "delay" millis to complete its start-up.
     */
    protected MockContainerEntity newAsyncContainer(TestApplication app, String name, double lowThreshold, double highThreshold, long delay) {
        MockContainerEntity container = app.createAndManageChild(EntitySpec.create(MockContainerEntity.class)
                .displayName(name)
                .configure(MockContainerEntity.DELAY, delay)
                .configure(LOW_THRESHOLD_CONFIG_KEY, lowThreshold)
                .configure(HIGH_THRESHOLD_CONFIG_KEY, highThreshold));
        LOG.debug("Managed new container {}", container);
        container.start(ImmutableList.of(loc));
        return container;
    }
    
    protected static MockItemEntity newItem(TestApplication app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = app.createAndManageChild(EntitySpec.create(MockItemEntity.class)
                .displayName(name));
        LOG.debug("Managing new item {} on container {}", item, container);
        item.move(container);
        item.sensors().set(TEST_METRIC, (int)workrate);
        return item;
    }
    
    protected static MockItemEntity newLockedItem(TestApplication app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = app.createAndManageChild(EntitySpec.create(MockItemEntity.class)
                .displayName(name)
                .configure(Movable.IMMOVABLE, true));
        LOG.debug("Managed new item {} on container {}", item, container);
        item.move(container);
        item.sensors().set(TEST_METRIC, (int)workrate);
        return item;
    }
    
    /**
     * Asks the item directly for its workrate.
     */
    protected static double getItemWorkrate(MockItemEntity item) {
        Object result = item.getAttribute(TEST_METRIC);
        return (result == null ? 0 : ((Number) result).doubleValue());
    }
    
    /**
     * Asks the container for its items, and then each of those items directly for their workrates; returns the total.
     */
    protected static double getContainerWorkrate(MockContainerEntity container) {
        double result = 0.0;
        Preconditions.checkNotNull(container, "container");
        for (Movable item : container.getBalanceableItems()) {
            Preconditions.checkNotNull(item, "item in container");
            assertEquals(item.getContainerId(), container.getId());
            result += getItemWorkrate((MockItemEntity)item);
        }
        return result;
    }
}
