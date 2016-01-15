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
package org.apache.brooklyn.core.test.qa.performance;

import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.performance.PerformanceTestDescriptor;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class EntityPerformanceTest extends AbstractPerformanceTest {

    private static final long TIMEOUT_MS = 10*1000;
    
    TestEntity entity;
    List<TestEntity> entities;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        entities = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            entities.add(app.createAndManageChild(EntitySpec.create(TestEntity.class)));
        }
        entity = entities.get(0);
        
        app.start(ImmutableList.of(loc));
    }

     protected int numIterations() {
        return 1000;
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testUpdateAttributeWhenNoListeners() {
        int numIterations = numIterations();
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        final AtomicInteger i = new AtomicInteger();
        
        measure(PerformanceTestDescriptor.create()
                .summary("EntityPerformanceTest.testUpdateAttributeWhenNoListeners")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        entity.sensors().set(TestEntity.SEQUENCE, i.getAndIncrement());
                    }}));
    }

    @Test(groups={"Integration", "Acceptance"})
    public void testUpdateAttributeWithNoopListeners() {
        final int numIterations = numIterations();
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        final AtomicInteger i = new AtomicInteger();
        final AtomicInteger lastVal = new AtomicInteger();
        
        app.subscriptions().subscribe(entity, TestEntity.SEQUENCE, new SensorEventListener<Integer>() {
                @Override public void onEvent(SensorEvent<Integer> event) {
                    lastVal.set(event.getValue());
                }});
        
        measure(PerformanceTestDescriptor.create()
                .summary("EntityPerformanceTest.testUpdateAttributeWithNoopListeners")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        entity.sensors().set(TestEntity.SEQUENCE, (i.getAndIncrement()));
                    }}));
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertTrue(lastVal.get() >= numIterations, "lastVal="+lastVal+"; numIterations="+numIterations);
            }});
    }

    @Test(groups={"Integration", "Acceptance"})
    public void testInvokeEffector() {
        int numIterations = numIterations();
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        measure(PerformanceTestDescriptor.create()
                .summary("EntityPerformanceTest.testInvokeEffector")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        entity.myEffector();
                    }}));
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testAsyncEffectorInvocation() {
        int numIterations = numIterations();
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        measure(PerformanceTestDescriptor.create()
                .summary("EntityPerformanceTest.testAsyncEffectorInvocation")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        Task<?> task = entity.invoke(TestEntity.MY_EFFECTOR, MutableMap.<String,Object>of());
                        try {
                            task.get();
                        } catch (Exception e) {
                            throw Exceptions.propagate(e);
                        }
                    }}));
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testMultiEntityConcurrentEffectorInvocation() {
        int numIterations = numIterations();
        double minRatePerSec = 100 * PERFORMANCE_EXPECTATION; // i.e. 1000 invocations (because 10 entities per time)
        
        measure(PerformanceTestDescriptor.create()
                .summary("EntityPerformanceTest.testMultiEntityConcurrentEffectorInvocation")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        Task<?> task = Entities.invokeEffector(app, entities, TestEntity.MY_EFFECTOR);
                        try {
                            task.get();
                        } catch (Exception e) {
                            throw Exceptions.propagate(e);
                        }
                    }}));
    }
}
