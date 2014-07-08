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
package brooklyn.qa.performance;

import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionManager;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SubscriptionPerformanceTest extends AbstractPerformanceTest {

    private static final long LONG_TIMEOUT_MS = 30*1000;
    private static final int NUM_ITERATIONS = 10000;
    
    TestEntity entity;
    List<TestEntity> entities;
    SubscriptionManager subscriptionManager;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        entities = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            entities.add(app.createAndManageChild(EntitySpec.create(TestEntity.class)));
        }
        entity = entities.get(0);
        app.start(ImmutableList.of(loc));
        
        subscriptionManager = app.getManagementContext().getSubscriptionManager();
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testManyPublishedOneSubscriber() throws Exception {
        int numSubscribers = 1;
        int numIterations = NUM_ITERATIONS;
        double minRatePerSec = 100 * PERFORMANCE_EXPECTATION; // i.e. 100*10 events delivered per sec
        final AtomicInteger iter = new AtomicInteger();
        final int expectedCount = numIterations*numSubscribers;
        
        final AtomicInteger listenerCount = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        
        for (int i = 0; i < numSubscribers; i++) {
            subscriptionManager.subscribe(MutableMap.<String, Object>of("subscriber", i), entity, TestEntity.SEQUENCE, new SensorEventListener<Integer>() {
                public void onEvent(SensorEvent<Integer> event) {
                    int count = listenerCount.incrementAndGet();
                    if (count >= expectedCount) completionLatch.countDown();
                }});
        }
        
        measureAndAssert("updateAttributeWithManyPublishedOneSubscriber", numIterations, minRatePerSec,
                new Runnable() {
                    public void run() {
                        entity.setAttribute(TestEntity.SEQUENCE, (iter.getAndIncrement()));
                    }
                },
                new Runnable() {
                    public void run() {
                        try {
                            completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        }
                        assertTrue(completionLatch.getCount() <= 0);
                    }
                });
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testManyListenersForSensorEvent() throws Exception {
        int numSubscribers = 10;
        int numIterations = NUM_ITERATIONS;
        double minRatePerSec = 100 * PERFORMANCE_EXPECTATION; // i.e. 100*10 events delivered per sec
        final AtomicInteger iter = new AtomicInteger();
        final int expectedCount = numIterations*numSubscribers;
        
        final AtomicInteger listenerCount = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        
        for (int i = 0; i < numSubscribers; i++) {
            subscriptionManager.subscribe(MutableMap.<String, Object>of("subscriber", i), entity, TestEntity.SEQUENCE, new SensorEventListener<Integer>() {
                public void onEvent(SensorEvent<Integer> event) {
                    int count = listenerCount.incrementAndGet();
                    if (count >= expectedCount) completionLatch.countDown();
                }});
        }
        
        measureAndAssert("updateAttributeWithManyListeners", numIterations, minRatePerSec,
                new Runnable() {
                    @Override public void run() {
                        entity.setAttribute(TestEntity.SEQUENCE, (iter.getAndIncrement()));
                    }},
                new Runnable() {
                        public void run() {
                            try {
                                completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e) {
                                throw Exceptions.propagate(e);
                            } 
                            assertTrue(completionLatch.getCount() <= 0);
                        }});
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testUpdateAttributeWithNoListenersButManyUnrelatedListeners() throws Exception {
        int numUnrelatedSubscribers = 1000;
        int numIterations = NUM_ITERATIONS;
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        final AtomicInteger iter = new AtomicInteger();
        final AtomicReference<RuntimeException> exception = new AtomicReference<RuntimeException>();
        
        for (int i = 0; i < (numUnrelatedSubscribers/2); i++) {
            subscriptionManager.subscribe(MutableMap.<String, Object>of("subscriber", i), entities.get(1), TestEntity.SEQUENCE, new SensorEventListener<Integer>() {
                public void onEvent(SensorEvent<Integer> event) {
                        exception.set(new RuntimeException("Unrelated subscriber called with "+event));
                        throw exception.get();
                    }});
            subscriptionManager.subscribe(MutableMap.<String, Object>of("subscriber", i), entity, TestEntity.MY_NOTIF, new SensorEventListener<Integer>() {
                public void onEvent(SensorEvent<Integer> event) {
                    exception.set(new RuntimeException("Unrelated subscriber called with "+event));
                    throw exception.get();
                }});
        }
        
        measureAndAssert("updateAttributeWithUnrelatedListeners", numIterations, minRatePerSec, new Runnable() {
            @Override public void run() {
                entity.setAttribute(TestEntity.SEQUENCE, (iter.incrementAndGet()));
            }});
        
        if (exception.get() != null) {
            throw exception.get();
        }
    }
}
