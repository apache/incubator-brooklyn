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

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestCluster;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class AutoScalerPolicyReconfigurationTest {
    
    private static long TIMEOUT_MS = 10000;
    
    private static final AttributeSensor<Integer> MY_ATTRIBUTE = Sensors.newIntegerSensor("autoscaler.test.intAttrib");
    TestApplication app;
    TestCluster tc;
    
    @BeforeMethod(alwaysRun=true)
    public void before() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        tc = app.createAndManageChild(EntitySpec.create(TestCluster.class)
                .configure("initialSize", 1));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testIncreaseMinPoolSizeCausesImmediateGrowth() {
        tc.resize(2);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .minPoolSize(2)
                .build();
        tc.policies().add(policy);

        policy.config().set(AutoScalerPolicy.MIN_POOL_SIZE, 3);
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 3));
    }
    
    @Test
    public void testDecreaseMinPoolSizeAllowsSubsequentShrink() {
        tc.resize(4);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .minPoolSize(2)
                .build();
        tc.policies().add(policy);
        
        // 25*4 = 100 -> 2 nodes at 50 each
        tc.sensors().set(MY_ATTRIBUTE, 25);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));

        // Decreases to new min-size
        policy.config().set(AutoScalerPolicy.MIN_POOL_SIZE, 1);
        tc.sensors().set(MY_ATTRIBUTE, 0);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 1));
    }
    
    @Test
    public void testDecreaseMaxPoolSizeCausesImmediateShrink() {
        tc.resize(6);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .maxPoolSize(6)
                .build();
        tc.policies().add(policy);

        policy.config().set(AutoScalerPolicy.MAX_POOL_SIZE, 4);
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 4));
    }
    
    @Test
    public void testIncreaseMaxPoolSizeAllowsSubsequentGrowth() {
        tc.resize(3);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .maxPoolSize(6)
                .build();
        tc.policies().add(policy);

        // 200*3 = 600 -> 6 nodes at 100 each
        tc.sensors().set(MY_ATTRIBUTE, 200);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 6));
        
        policy.config().set(AutoScalerPolicy.MAX_POOL_SIZE, 8);
        
        // Increases to max-size only
        tc.sensors().set(MY_ATTRIBUTE, 100000);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 8));
    }
    
    @Test
    public void testReconfigureMetricLowerBound() {
        tc.resize(2);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .build();
        tc.policies().add(policy);

        policy.config().set(AutoScalerPolicy.METRIC_LOWER_BOUND, 51);

        tc.sensors().set(MY_ATTRIBUTE, 50);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 1));
    }

    @Test
    public void testReconfigureMetricUpperBound() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .build();
        tc.policies().add(policy);

        policy.config().set(AutoScalerPolicy.METRIC_UPPER_BOUND, 99);

        tc.sensors().set(MY_ATTRIBUTE, 100);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
    }

    @Test
    public void testReconfigureResizeUpStabilizationDelay() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .resizeUpStabilizationDelay(Duration.TWO_MINUTES)
                .build();
        tc.policies().add(policy);

        policy.config().set(AutoScalerPolicy.RESIZE_UP_STABILIZATION_DELAY, Duration.ZERO);

        tc.sensors().set(MY_ATTRIBUTE, 101);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
    }
    
    @Test
    public void testReconfigureResizeDownStabilizationDelay() {
        tc.resize(2);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .resizeDownStabilizationDelay(Duration.TWO_MINUTES)
                .build();
        tc.policies().add(policy);

        policy.config().set(AutoScalerPolicy.RESIZE_DOWN_STABILIZATION_DELAY, Duration.ZERO);

        tc.sensors().set(MY_ATTRIBUTE, 1);
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 1));
    }
}
