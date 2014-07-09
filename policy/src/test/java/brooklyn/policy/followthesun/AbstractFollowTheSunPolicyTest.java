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

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.policy.loadbalancing.BalanceableContainer;
import brooklyn.policy.loadbalancing.MockContainerEntity;
import brooklyn.policy.loadbalancing.MockItemEntity;
import brooklyn.policy.loadbalancing.MockItemEntityImpl;
import brooklyn.policy.loadbalancing.Movable;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class AbstractFollowTheSunPolicyTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractFollowTheSunPolicyTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100;
    
    protected TestApplication app;
    protected ManagementContext managementContext;
    protected SimulatedLocation loc1;
    protected SimulatedLocation loc2;
    protected FollowTheSunPool pool;
    protected DefaultFollowTheSunModel<Entity, Movable> model;
    protected FollowTheSunPolicy policy;
    protected Group containerGroup;
    protected Group itemGroup;
    protected Random random = new Random();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        LOG.debug("In AbstractFollowTheSunPolicyTest.setUp()");

        MockItemEntityImpl.totalMoveCount.set(0);
        MockItemEntityImpl.lastMoveTime.set(0);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        managementContext = app.getManagementContext();
        
        loc1 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class).configure("name", "loc1"));
        loc2 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class).configure("name", "loc2"));
        
        containerGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("containerGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockContainerEntity.class)));
        
        itemGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("itemGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockItemEntity.class)));
        model = new DefaultFollowTheSunModel<Entity, Movable>("pool-model");
        pool = app.createAndManageChild(EntitySpec.create(FollowTheSunPool.class));
        pool.setContents(containerGroup, itemGroup);
        policy = new FollowTheSunPolicy(MockItemEntity.ITEM_USAGE_METRIC, model, FollowTheSunParameters.newDefault());
        pool.addPolicy(policy);
        app.start(ImmutableList.of(loc1, loc2));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (pool != null && policy != null) pool.removePolicy(policy);
        if (app != null) Entities.destroyAll(app.getManagementContext());
        MockItemEntityImpl.totalMoveCount.set(0);
        MockItemEntityImpl.lastMoveTime.set(0);
    }
    
    /**
     * Asserts that the given container have the given expected workrates (by querying the containers directly).
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertItemDistributionEventually(final Map<MockContainerEntity, ? extends Collection<MockItemEntity>> expected) {
        try {
            Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    assertItemDistribution(expected);
                }});
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString();
            throw new RuntimeException(errMsg, e);
        }
    }

    protected void assertItemDistributionContinually(final Map<MockContainerEntity, Collection<MockItemEntity>> expected) {
        try {
            Asserts.succeedsContinually(MutableMap.of("timeout", SHORT_WAIT_MS), new Runnable() {
                public void run() {
                    assertItemDistribution(expected);
                }});
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString();
            throw new RuntimeException(errMsg, e);
        }
    }

    protected void assertItemDistribution(Map<MockContainerEntity, ? extends Collection<MockItemEntity>> expected) {
        String errMsg = verboseDumpToString();
        for (Map.Entry<MockContainerEntity, ? extends Collection<MockItemEntity>> entry : expected.entrySet()) {
            MockContainerEntity container = entry.getKey();
            Collection<MockItemEntity> expectedItems = entry.getValue();
            
            assertEquals(ImmutableSet.copyOf(container.getBalanceableItems()), ImmutableSet.copyOf(expectedItems), errMsg);
        }
    }

    protected String verboseDumpToString() {
        Iterable<MockContainerEntity> containers = Iterables.filter(app.getManagementContext().getEntityManager().getEntities(), MockContainerEntity.class);
        Iterable<MockItemEntity> items = Iterables.filter(app.getManagementContext().getEntityManager().getEntities(), MockItemEntity.class);
        
        Iterable<Double> containerRates = Iterables.transform(containers, new Function<MockContainerEntity, Double>() {
            @Override public Double apply(MockContainerEntity input) {
                return (double) input.getWorkrate();
            }});
        
        Iterable<Map<Entity, Double>> containerItemUsages = Iterables.transform(containers, new Function<MockContainerEntity, Map<Entity, Double>>() {
            @Override public Map<Entity, Double> apply(MockContainerEntity input) {
                return input.getItemUsage();
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
                +"; containerItemUsages="+containerItemUsages
                +"; itemDistributionByContainer="+itemDistributionByContainer
                +"; itemDistributionByItem="+itemDistributionByItem
                +"; model="+modelItemDistribution
                +"; totalMoves="+MockItemEntityImpl.totalMoveCount
                +"; lastMoveTime="+Time.makeDateString(MockItemEntityImpl.lastMoveTime.get());
    }
    
    protected MockContainerEntity newContainer(TestApplication app, Location loc, String name) {
        return newAsyncContainer(app, loc, name, 0);
    }
    
    /**
     * Creates a new container that will take "delay" millis to complete its start-up.
     */
    protected MockContainerEntity newAsyncContainer(TestApplication app, Location loc, String name, long delay) {
        // FIXME Is this comment true?
        // Annoyingly, can't set parent until after the threshold config has been defined.
        MockContainerEntity container = app.createAndManageChild(EntitySpec.create(MockContainerEntity.class)
                .displayName(name)
                .configure(MockContainerEntity.DELAY, delay));
        LOG.debug("Managed new container {}", container);
        container.start(ImmutableList.of(loc));
        return container;
    }

    protected static MockItemEntity newLockedItem(TestApplication app, MockContainerEntity container, String name) {
        MockItemEntity item = app.createAndManageChild(EntitySpec.create(MockItemEntity.class)
                .displayName(name)
                .configure(MockItemEntity.IMMOVABLE, true));
        LOG.debug("Managed new locked item {}", container);
        if (container != null) {
            item.move(container);
        }
        return item;
    }
    
    protected static MockItemEntity newItem(TestApplication app, MockContainerEntity container, String name) {
        MockItemEntity item = app.createAndManageChild(EntitySpec.create(MockItemEntity.class)
                .displayName(name));
        LOG.debug("Managed new item {}", container);
        if (container != null) {
            item.move(container);
        }
        return item;
    }
    
    protected static MockItemEntity newItem(TestApplication app, MockContainerEntity container, String name, Map<? extends Entity, Double> workpattern) {
        MockItemEntity item = newItem(app, container, name);
        if (workpattern != null) {
            ((EntityLocal)item).setAttribute(MockItemEntity.ITEM_USAGE_METRIC, (Map) workpattern);
        }
        return item;
    }
}
