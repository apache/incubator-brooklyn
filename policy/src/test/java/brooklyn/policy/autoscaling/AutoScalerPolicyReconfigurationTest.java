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
package brooklyn.policy.autoscaling;

import static brooklyn.policy.autoscaling.AutoScalerPolicyTest.currentSizeAsserter;
import static brooklyn.test.TestUtils.executeUntilSucceeds;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestCluster;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableMap;

public class AutoScalerPolicyReconfigurationTest {
    
    private static long TIMEOUT_MS = 10000;
    
    private static final AttributeSensor<Integer> MY_ATTRIBUTE = Sensors.newIntegerSensor("autoscaler.test.intAttrib");
    TestApplication app;
    TestCluster tc;
    
    @BeforeMethod(alwaysRun=true)
    public void before() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
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
        tc.addPolicy(policy);

        policy.setConfig(AutoScalerPolicy.MIN_POOL_SIZE, 3);
        
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 3));
    }
    
    @Test
    public void testDecreaseMinPoolSizeAllowsSubsequentShrink() {
        tc.resize(4);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .minPoolSize(2)
                .build();
        tc.addPolicy(policy);
        
        // 25*4 = 100 -> 2 nodes at 50 each
        tc.setAttribute(MY_ATTRIBUTE, 25);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));

        // Decreases to new min-size
        policy.setConfig(AutoScalerPolicy.MIN_POOL_SIZE, 1);
        tc.setAttribute(MY_ATTRIBUTE, 0);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 1));
    }
    
    @Test
    public void testDecreaseMaxPoolSizeCausesImmediateShrink() {
        tc.resize(6);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .maxPoolSize(6)
                .build();
        tc.addPolicy(policy);

        policy.setConfig(AutoScalerPolicy.MAX_POOL_SIZE, 4);
        
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 4));
    }
    
    @Test
    public void testIncreaseMaxPoolSizeAllowsSubsequentGrowth() {
        tc.resize(3);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .maxPoolSize(6)
                .build();
        tc.addPolicy(policy);

        // 200*3 = 600 -> 6 nodes at 100 each
        tc.setAttribute(MY_ATTRIBUTE, 200);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 6));
        
        policy.setConfig(AutoScalerPolicy.MAX_POOL_SIZE, 8);
        
        // Increases to max-size only
        tc.setAttribute(MY_ATTRIBUTE, 100000);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 8));
    }
    
    @Test
    public void testReconfigureMetricLowerBound() {
        tc.resize(2);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .build();
        tc.addPolicy(policy);

        policy.setConfig(AutoScalerPolicy.METRIC_LOWER_BOUND, 51);

        tc.setAttribute(MY_ATTRIBUTE, 50);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 1));
    }

    @Test
    public void testReconfigureMetricUpperBound() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .build();
        tc.addPolicy(policy);

        policy.setConfig(AutoScalerPolicy.METRIC_UPPER_BOUND, 99);

        tc.setAttribute(MY_ATTRIBUTE, 100);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
    }

    @Test
    public void testReconfigureResizeUpStabilizationDelay() {
        tc.resize(1);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .resizeUpStabilizationDelay(100000)
                .build();
        tc.addPolicy(policy);

        policy.setConfig(AutoScalerPolicy.RESIZE_UP_STABILIZATION_DELAY, Duration.ZERO);

        tc.setAttribute(MY_ATTRIBUTE, 101);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 2));
    }
    
    @Test
    public void testReconfigureResizeDownStabilizationDelay() {
        tc.resize(2);
        
        AutoScalerPolicy policy = new AutoScalerPolicy.Builder().metric(MY_ATTRIBUTE)
                .metricLowerBound(50).metricUpperBound(100)
                .resizeDownStabilizationDelay(100000)
                .build();
        tc.addPolicy(policy);

        policy.setConfig(AutoScalerPolicy.RESIZE_DOWN_STABILIZATION_DELAY, Duration.ZERO);

        tc.setAttribute(MY_ATTRIBUTE, 1);
        executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(tc, 1));
    }
}
