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

import static org.testng.Assert.assertEquals;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class AutoScalerPolicyRebindTest extends RebindTestFixtureWithApp {

    public static BasicNotificationSensor<Map> POOL_HOT_SENSOR = new BasicNotificationSensor<Map>(
            Map.class, "AutoScalerPolicyRebindTest.resizablepool.hot", "Pool is over-utilized; it has insufficient resource for current workload");
    public static BasicNotificationSensor<Map> POOL_COLD_SENSOR = new BasicNotificationSensor<Map>(
            Map.class, "AutoScalerPolicyRebindTest.resizablepool.cold", "Pool is under-utilized; it has too much resource for current workload");
    public static BasicNotificationSensor<Map> POOL_OK_SENSOR = new BasicNotificationSensor<Map>(
            Map.class, "AutoScalerPolicyRebindTest.resizablepool.cold", "Pool utilization is ok; the available resources are fine for the current workload");
    public static BasicNotificationSensor<MaxPoolSizeReachedEvent> MAX_SIZE_REACHED_SENSOR = new BasicNotificationSensor<MaxPoolSizeReachedEvent>(
            MaxPoolSizeReachedEvent.class, "AutoScalerPolicyRebindTest.maxSizeReached");
    public static AttributeSensor<Integer> METRIC_SENSOR = Sensors.newIntegerSensor("AutoScalerPolicyRebindTest.metric");
            
    private DynamicCluster origCluster;
    private SimulatedLocation origLoc;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        origCluster = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("memberSpec", EntitySpec.create(TestEntity.class)));
    }
    
    @Test
    public void testRestoresAutoScalerConfig() throws Exception {
        origCluster.addPolicy(AutoScalerPolicy.builder()
                .name("myname")
                .metric(METRIC_SENSOR)
                .entityWithMetric(origCluster)
                .metricUpperBound(1)
                .metricLowerBound(2)
                .minPoolSize(0)
                .maxPoolSize(3)
                .minPeriodBetweenExecs(4)
                .resizeUpStabilizationDelay(5)
                .resizeDownStabilizationDelay(6)
                .poolHotSensor(POOL_HOT_SENSOR)
                .poolColdSensor(POOL_COLD_SENSOR)
                .poolOkSensor(POOL_OK_SENSOR)
                .maxSizeReachedSensor(MAX_SIZE_REACHED_SENSOR)
                .maxReachedNotificationDelay(7)
                .buildSpec());
        
        TestApplication newApp = rebind();
        DynamicCluster newCluster = (DynamicCluster) Iterables.getOnlyElement(newApp.getChildren());
        AutoScalerPolicy newPolicy = (AutoScalerPolicy) Iterables.getOnlyElement(newCluster.getPolicies());

        assertEquals(newPolicy.getName(), "myname");
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.METRIC), METRIC_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.ENTITY_WITH_METRIC), newCluster);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.METRIC_UPPER_BOUND), 1);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.METRIC_LOWER_BOUND), 2);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MIN_POOL_SIZE), (Integer)0);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MAX_POOL_SIZE), (Integer)3);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MIN_PERIOD_BETWEEN_EXECS), Duration.of(4, TimeUnit.MILLISECONDS));
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.RESIZE_UP_STABILIZATION_DELAY), Duration.of(5, TimeUnit.MILLISECONDS));
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.RESIZE_DOWN_STABILIZATION_DELAY), Duration.of(6, TimeUnit.MILLISECONDS));
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.POOL_HOT_SENSOR), POOL_HOT_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.POOL_COLD_SENSOR), POOL_COLD_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.POOL_OK_SENSOR), POOL_OK_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MAX_SIZE_REACHED_SENSOR), MAX_SIZE_REACHED_SENSOR);
        assertEquals(newPolicy.getConfig(AutoScalerPolicy.MAX_REACHED_NOTIFICATION_DELAY), Duration.of(7, TimeUnit.MILLISECONDS));
    }
    
    @Test
    public void testAutoScalerResizesAfterRebind() throws Exception {
        origCluster.start(ImmutableList.of(origLoc));
        origCluster.addPolicy(AutoScalerPolicy.builder()
                .name("myname")
                .metric(METRIC_SENSOR)
                .entityWithMetric(origCluster)
                .metricUpperBound(10)
                .metricLowerBound(100)
                .minPoolSize(1)
                .maxPoolSize(3)
                .buildSpec());
        
        TestApplication newApp = rebind();
        DynamicCluster newCluster = (DynamicCluster) Iterables.getOnlyElement(newApp.getChildren());

        assertEquals(newCluster.getCurrentSize(), (Integer)1);
        
        ((EntityInternal)newCluster).setAttribute(METRIC_SENSOR, 1000);
        EntityTestUtils.assertGroupSizeEqualsEventually(newCluster, 3);
        
        ((EntityInternal)newCluster).setAttribute(METRIC_SENSOR, 1);
        EntityTestUtils.assertGroupSizeEqualsEventually(newCluster, 1);
    }
}
