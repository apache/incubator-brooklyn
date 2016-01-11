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
package org.apache.brooklyn.policy.autoscaling;

import static org.apache.brooklyn.policy.autoscaling.AutoScalerPolicyTest.currentSizeAsserter;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.BasicNotificationSensor;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestCluster;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class AutoScalerPolicyMetricTest {
    
    private static long SHORT_WAIT_MS = 250;
    
    private static final AttributeSensor<Integer> MY_ATTRIBUTE = Sensors.newIntegerSensor("autoscaler.test.intAttrib");
    TestApplication app;
    TestCluster tc;
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        tc = app.createAndManageChild(EntitySpec.create(TestCluster.class)
                .configure("initialSize", 1));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testIncrementsSizeIffUpperBoundExceeded() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);

        tc.sensors().set(MY_ATTRIBUTE, 100);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));

        tc.sensors().set(MY_ATTRIBUTE, 101);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 2));
    }
    
    @Test
    public void testDecrementsSizeIffLowerBoundExceeded() {
        tc.resize(2);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);

        tc.sensors().set(MY_ATTRIBUTE, 50);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 2));

        tc.sensors().set(MY_ATTRIBUTE, 49);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 1));
    }
    
    @Test(groups="Integration")
    public void testIncrementsSizeInProportionToMetric() {
        tc.resize(5);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);
        
        // workload 200 so requires doubling size to 10 to handle: (200*5)/100 = 10
        tc.sensors().set(MY_ATTRIBUTE, 200);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 10));
        
        // workload 5, requires 1 entity: (10*110)/100 = 11
        tc.sensors().set(MY_ATTRIBUTE, 110);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 11));
    }
    
    @Test(groups="Integration")
    public void testDecrementsSizeInProportionToMetric() {
        tc.resize(5);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);
        
        // workload can be handled by 4 servers, within its valid range: (49*5)/50 = 4.9
        tc.sensors().set(MY_ATTRIBUTE, 49);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 4));
        
        // workload can be handled by 4 servers, within its valid range: (25*4)/50 = 2
        tc.sensors().set(MY_ATTRIBUTE, 25);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 2));
        
        tc.sensors().set(MY_ATTRIBUTE, 0);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 1));
    }
    
    @Test(groups="Integration")
    public void testObeysMinAndMaxSize() {
        tc.resize(4);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .minPoolSize(2).maxPoolSize(6)
                .build();
        tc.policies().add(policy);

        // Decreases to min-size only
        tc.sensors().set(MY_ATTRIBUTE, 0);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 2));
        
        // Increases to max-size only
        tc.sensors().set(MY_ATTRIBUTE, 100000);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 6));
    }
    
    @Test(groups="Integration",invocationCount=20)
    public void testWarnsWhenMaxCapReached() {
        final List<MaxPoolSizeReachedEvent> maxReachedEvents = Lists.newCopyOnWriteArrayList();
        tc.resize(1);
        
        BasicNotificationSensor<MaxPoolSizeReachedEvent> maxSizeReachedSensor = AutoScalerPolicy.DEFAULT_MAX_SIZE_REACHED_SENSOR;
        
        app.subscriptions().subscribe(tc, maxSizeReachedSensor, new SensorEventListener<MaxPoolSizeReachedEvent>() {
                @Override public void onEvent(SensorEvent<MaxPoolSizeReachedEvent> event) {
                    maxReachedEvents.add(event.getValue());
                }});
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .maxPoolSize(6)
                .maxSizeReachedSensor(maxSizeReachedSensor)
                .build();
        tc.policies().add(policy);

        // workload can be handled by 6 servers, so no need to notify: 6 <= (100*6)/50
        tc.sensors().set(MY_ATTRIBUTE, 600);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 6));
        assertTrue(maxReachedEvents.isEmpty());
        
        // Increases to above max capacity: would require (100000*6)/100 = 6000
        tc.sensors().set(MY_ATTRIBUTE, 100000);
        
        // Assert our listener gets notified (once)
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(maxReachedEvents.size(), 1);
                assertEquals(maxReachedEvents.get(0).getMaxAllowed(), 6);
                assertEquals(maxReachedEvents.get(0).getCurrentPoolSize(), 6);
                assertEquals(maxReachedEvents.get(0).getCurrentUnbounded(), 6000);
                assertEquals(maxReachedEvents.get(0).getMaxUnbounded(), 6000);
                assertEquals(maxReachedEvents.get(0).getTimeWindow(), 0);
            }});
        Asserts.succeedsContinually(new Runnable() {
                @Override public void run() {
                    assertEquals(maxReachedEvents.size(), 1);
                }});
        currentSizeAsserter(tc, 6).run();
    }
    
    @Test
    public void testDestructionState() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);

        policy.destroy();
        assertTrue(policy.isDestroyed());
        assertFalse(policy.isRunning());
        
        tc.sensors().set(MY_ATTRIBUTE, 100000);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));
        
        // TODO Could assert all subscriptions have been de-registered as well, 
        // but that requires exposing more things just for testing...
    }
    
    @Test
    public void testSuspendState() {
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);
        
        policy.suspend();
        assertFalse(policy.isRunning());
        assertFalse(policy.isDestroyed());
        
        policy.resume();
        assertTrue(policy.isRunning());
        assertFalse(policy.isDestroyed());
    }

    @Test
    public void testPostSuspendActions() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);

        policy.suspend();
        
        tc.sensors().set(MY_ATTRIBUTE, 100000);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));
    }
    
    @Test
    public void testPostResumeActions() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE).metricLowerBound(50).metricUpperBound(100).build();
        tc.policies().add(policy);
        
        policy.suspend();
        policy.resume();
        tc.sensors().set(MY_ATTRIBUTE, 101);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 2));
    }
    
    @Test
    public void testSubscribesToMetricOnSpecifiedEntity() {
        TestEntity entityWithMetric = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder()
                .metric(TestEntity.SEQUENCE)
                .entityWithMetric(entityWithMetric)
                .metricLowerBound(50)
                .metricUpperBound(100)
                .build();
        tc.policies().add(policy);

        // First confirm that tc is not being listened to for this entity
        tc.sensors().set(TestEntity.SEQUENCE, 101);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));

        // Then confirm we listen to the correct "entityWithMetric"
        entityWithMetric.sensors().set(TestEntity.SEQUENCE, 101);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 2));
    }
    
    @Test(groups="Integration")
    public void testOnFailedGrowWillSetHighwaterMarkAndNotResizeAboveThatAgain() {
        tc = app.createAndManageChild(EntitySpec.create(TestCluster.class)
                .configure("initialSize", 1)
                .configure(TestCluster.MAX_SIZE, 2));

        tc.resize(1);
        
        tc.policies().add(AutoScalerPolicy.builder()
                .metric(MY_ATTRIBUTE)
                .metricLowerBound(50)
                .metricUpperBound(100)
                .buildSpec());
        
        // workload 200 so requires doubling size to 2 to handle: (200*1)/100 = 2
        tc.sensors().set(MY_ATTRIBUTE, 200);
        Asserts.succeedsEventually(currentSizeAsserter(tc, 2));
        assertEquals(tc.getDesiredSizeHistory(), ImmutableList.of(1, 2), "desired="+tc.getDesiredSizeHistory());
        assertEquals(tc.getSizeHistory(), ImmutableList.of(0, 1, 2), "sizes="+tc.getSizeHistory());
        tc.sensors().set(MY_ATTRIBUTE, 100);
        
        // workload 110, requires 1 more entity: (2*110)/100 = 2.1
        // But max size is 2, so resize will fail.
        tc.sensors().set(MY_ATTRIBUTE, 110);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 2));
        assertEquals(tc.getDesiredSizeHistory(), ImmutableList.of(1, 2, 3), "desired="+tc.getDesiredSizeHistory()); // TODO succeeds eventually?
        assertEquals(tc.getSizeHistory(), ImmutableList.of(0, 1, 2), "sizes="+tc.getSizeHistory());
        
        // another attempt to resize will not cause it to ask
        tc.sensors().set(MY_ATTRIBUTE, 110);
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 2));
        assertEquals(tc.getDesiredSizeHistory(), ImmutableList.of(1, 2, 3), "desired="+tc.getDesiredSizeHistory());
        assertEquals(tc.getSizeHistory(), ImmutableList.of(0, 1, 2), "sizes="+tc.getSizeHistory());
        
        // but if we shrink down again, then we'll still be able to go up to the previous level.
        // We'll only try to go as high as the previous high-water mark though.
        tc.sensors().set(MY_ATTRIBUTE, 1);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 1));
        assertEquals(tc.getDesiredSizeHistory(), ImmutableList.of(1, 2, 3, 1), "desired="+tc.getDesiredSizeHistory());
        assertEquals(tc.getSizeHistory(), ImmutableList.of(0, 1, 2, 1), "sizes="+tc.getSizeHistory());
        
        tc.sensors().set(MY_ATTRIBUTE, 10000);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", SHORT_WAIT_MS), currentSizeAsserter(tc, 2));
        assertEquals(tc.getDesiredSizeHistory(), ImmutableList.of(1, 2, 3, 1, 2), "desired="+tc.getDesiredSizeHistory());
        assertEquals(tc.getSizeHistory(), ImmutableList.of(0, 1, 2, 1, 2), "sizes="+tc.getSizeHistory());
    }
}
