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
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.Executors;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.BasicEnricherTest;
import org.apache.brooklyn.core.feed.AbstractFeed;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.policy.basic.BasicPolicyTest;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class EntityConcurrencyTest extends BrooklynAppUnitTestSupport {
    TestEntity entity;
    ListeningExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.addChild(EntitySpec.create(TestEntity.class));
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (executor != null) executor.shutdownNow();
    }
    
    @Test
    public void testConcurrentSetAttribute() throws Exception {
        final int NUM_TASKS = Math.min(500 * Runtime.getRuntime().availableProcessors(), 1000);
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            final AttributeSensor<Integer> nextSensor = Sensors.newIntegerSensor("EntityConcurrencyTest.exampleSensor"+i);
            final int val = i;
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.sensors().set(nextSensor, val);
                }});
            futures.add(future);
        }
        
        Futures.allAsList(futures).get();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            AttributeSensor<Integer> nextSensor = Sensors.newIntegerSensor("EntityConcurrencyTest.exampleSensor"+i);
            assertEquals(entity.sensors().get(nextSensor), (Integer)i, "i="+i);
        }
    }
    
    @Test
    public void testConcurrentSetConfig() throws Exception {
        final int NUM_TASKS = Math.min(500 * Runtime.getRuntime().availableProcessors(), 1000);
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            final ConfigKey<Integer> nextKey = ConfigKeys.newIntegerConfigKey("EntityConcurrencyTest.exampleConfig"+i);
            final int val = i;
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.config().set(nextKey, val);
                }});
            futures.add(future);
        }
        
        Futures.allAsList(futures).get();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            final ConfigKey<Integer> nextKey = ConfigKeys.newIntegerConfigKey("EntityConcurrencyTest.exampleConfig"+i);
            assertEquals(entity.config().get(nextKey), (Integer)i, "i="+i);
        }
    }
    
    @Test
    public void testConcurrentAddTag() throws Exception {
        final int NUM_TASKS = Math.min(500 * Runtime.getRuntime().availableProcessors(), 1000);
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        List<Integer> tags = Lists.newArrayList();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            final int val = i;
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.tags().addTag(val);
                }});
            futures.add(future);
            tags.add(val);
        }

        Futures.allAsList(futures).get();
        
        Asserts.assertEqualsIgnoringOrder(entity.tags().getTags(), tags);
    }
    
    @Test
    public void testConcurrentAddGroup() throws Exception {
        final int NUM_TASKS = 100;
        
        List<BasicGroup> groups = Lists.newArrayList();
        for (int i = 0; i < NUM_TASKS; i++) {
            groups.add(app.addChild(EntitySpec.create(BasicGroup.class)));
        }
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (final BasicGroup group : groups) {
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    group.addMember(entity);
                }});
            futures.add(future);
        }

        Futures.allAsList(futures).get();
        
        Asserts.assertEqualsIgnoringOrder(entity.groups(), groups);
    }
    
    @Test
    public void testConcurrentAddChild() throws Exception {
        final int NUM_TASKS = 100;
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.addChild(EntitySpec.create(BasicEntity.class));
                }});
            futures.add(future);
        }

        Futures.allAsList(futures).get();
        
        assertEquals(entity.getChildren().size(), NUM_TASKS);
        Asserts.assertEqualsIgnoringOrder(entity.getChildren(), mgmt.getEntityManager().findEntities(Predicates.instanceOf(BasicEntity.class)));
    }
    
    @Test
    public void testConcurrentAddLocation() throws Exception {
        final int NUM_TASKS = 100;
        
        List<Location> locs = Lists.newArrayList();
        for (int i = 0; i < NUM_TASKS; i++) {
            locs.add(mgmt.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class)));
        }
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (final Location loc : locs) {
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.addLocations(ImmutableList.of(loc));
                }});
            futures.add(future);
        }

        Futures.allAsList(futures).get();
        
        Asserts.assertEqualsIgnoringOrder(entity.getLocations(), locs);
    }
    
    @Test
    public void testConcurrentAddPolicy() throws Exception {
        final int NUM_TASKS = 100;
        
        int numPrePolicies = entity.policies().size();
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.policies().add(PolicySpec.create(BasicPolicyTest.MyPolicy.class));
                }});
            futures.add(future);
        }

        Futures.allAsList(futures).get();
        
        assertEquals(entity.policies().size(), NUM_TASKS+numPrePolicies);
    }
    
    @Test
    public void testConcurrentAddEnricher() throws Exception {
        final int NUM_TASKS = 100;
        
        int numPreEnrichers = entity.enrichers().size();
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.enrichers().add(EnricherSpec.create(BasicEnricherTest.MyEnricher.class));
                }});
            futures.add(future);
        }

        Futures.allAsList(futures).get();
        
        assertEquals(entity.enrichers().size(), NUM_TASKS+numPreEnrichers);
    }
    
    @Test
    public void testConcurrentAddFeed() throws Exception {
        final int NUM_TASKS = 100;
        
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        
        for (int i = 0; i < NUM_TASKS; i++) {
            ListenableFuture<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    entity.feeds().addFeed(new MyFeed());
                }});
            futures.add(future);
        }

        Futures.allAsList(futures).get();
        
        assertEquals(entity.feeds().getFeeds().size(), NUM_TASKS);
    }
    private static class MyFeed extends AbstractFeed {
    }
}
