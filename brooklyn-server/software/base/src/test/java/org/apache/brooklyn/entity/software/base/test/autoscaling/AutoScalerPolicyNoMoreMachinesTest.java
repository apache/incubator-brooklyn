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
package org.apache.brooklyn.entity.software.base.test.autoscaling;

import static org.testng.Assert.assertEquals;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.mgmt.internal.CollectionChangeListener;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestCluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

public class AutoScalerPolicyNoMoreMachinesTest extends BrooklynAppUnitTestSupport {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AutoScalerPolicyNoMoreMachinesTest.class);
    
    private static long SHORT_WAIT_MS = 250;
    
    DynamicCluster cluster;
    Location loc;
    AutoScalerPolicy policy;
    Set<Entity> entitiesAdded;
    Set<Entity> entitiesRemoved;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(TestCluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)
                        .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true)));
        loc = mgmt.getLocationRegistry().resolve("byon(hosts='1.1.1.1,1.1.1.2')");
        app.start(ImmutableList.of(loc));
        
        entitiesAdded = Sets.newLinkedHashSet();
        entitiesRemoved = Sets.newLinkedHashSet();
        mgmt.addEntitySetListener(new CollectionChangeListener<Entity>() {
            @Override public void onItemAdded(Entity item) { addToSetAndNotify(entitiesAdded, item); }
            @Override public void onItemRemoved(Entity item) { addToSetAndNotify(entitiesRemoved, item); }});
    }
    private static <T> void addToSetAndNotify(Set<T> items, T item) {
        synchronized (items) {
            items.add(item);
            items.notifyAll();
        }
    }

    @Test
    public void testResizeDirectly() throws Exception {
        assertSize(0);

        cluster.resize(2);
        assertSize(2);
        
        // Won't get a location to successfully resize (byon location only has 2 machines); 
        // so still left with 2 members (failed node not quarantined, because exception well understood)
        try {
            cluster.resize(3);
            Asserts.shouldHaveFailedPreviously();
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, Resizable.InsufficientCapacityException.class);
        }
        assertSize(2, 0, 1);

        // Resize down; will delete one of our nodes
        cluster.resize(1);
        assertSize(1, 0, 2);
        
        // Resize back up to 2 should be allowed
        cluster.resize(2);
        assertSize(2, 0, 2);
    }
    
    @Test
    public void testPoolHotSensorResizingBeyondMaxMachines() throws Exception {
        cluster.resize(1);
        policy = cluster.policies().add(PolicySpec.create(AutoScalerPolicy.class)
                .configure(AutoScalerPolicy.MIN_PERIOD_BETWEEN_EXECS, Duration.millis(10)));

        // Single node trying to handle a load of 21; too high, so will add one more node
        cluster.sensors().emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(21L, 10L, 20L));
        assertSizeEventually(2);

        // Two nodes handing an aggregated load of 41; too high for 2 nodes so tries to scale to 3.
        // But byon location only has 2 nodes so will fail.
        cluster.sensors().emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(21L, 10L, 20L));
        assertSizeEventually(2, 0, 1);

        // Should not repeatedly retry
        assertSizeContinually(2, 0, 1);
        
        // If there is another indication of too much load, should not retry yet again.
        cluster.sensors().emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(42L, 10L, 20L));
        assertSizeContinually(2, 0, 1);
    }
    
    @Test
    public void testMetricResizingBeyondMaxMachines() throws Exception {
        AttributeSensor<Integer> metric = Sensors.newIntegerSensor("test.aggregatedLoad");
        
        cluster.resize(1);
        policy = cluster.policies().add(PolicySpec.create(AutoScalerPolicy.class)
                .configure(AutoScalerPolicy.METRIC, metric)
                .configure(AutoScalerPolicy.METRIC_LOWER_BOUND, 10)
                .configure(AutoScalerPolicy.METRIC_UPPER_BOUND, 20)
                .configure(AutoScalerPolicy.MIN_PERIOD_BETWEEN_EXECS, Duration.millis(10)));

        // Single node trying to handle a load of 21; too high, so will add one more node.
        // That takes the load back to within acceptable limits
        cluster.sensors().set(metric, 21);
        assertSizeEventually(2);
        cluster.sensors().set(metric, 19);

        // With two nodes, load is now too high, so will try (and fail) to add one more node.
        // Trigger another attempt to resize.
        // Any nodes that fail with NoMachinesAvailableException will be immediately deleted.
        cluster.sensors().set(metric, 22);
        assertSizeEventually(2, 0, 1);
        assertSizeContinually(2, 0, 1);
        
        // Metric is re-published; should not keep retrying
        cluster.sensors().set(metric, 21);
        assertSizeContinually(2, 0, 1);
    }

    protected Map<String, Object> message(double currentWorkrate, double lowThreshold, double highThreshold) {
        return message(cluster.getCurrentSize(), currentWorkrate, lowThreshold, highThreshold);
    }
    
    protected Map<String, Object> message(int currentSize, double currentWorkrate, double lowThreshold, double highThreshold) {
        return ImmutableMap.<String,Object>of(
            AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, currentSize,
            AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, currentWorkrate,
            AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, lowThreshold,
            AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, highThreshold);
    }
    
    protected void assertSize(Integer targetSize) {
        assertSize(targetSize, 0);
    }

    protected void assertSize(int targetSize, int quarantineSize, final int deletedSize) {
        assertSize(targetSize, quarantineSize);
        Asserts.eventuallyOnNotify(entitiesRemoved, CollectionFunctionals.sizeEquals(deletedSize));
    }
    
    protected void assertSize(int targetSize, int quarantineSize) {
        assertEquals(cluster.getCurrentSize(), (Integer) targetSize, "cluster.currentSize");
        assertEquals(cluster.getMembers().size(), targetSize, "cluster.members.size");
        assertEquals(cluster.sensors().get(DynamicCluster.QUARANTINE_GROUP).getMembers().size(), quarantineSize, "cluster.quarantine.size");
        assertEquals(mgmt.getEntityManager().findEntities(Predicates.instanceOf(EmptySoftwareProcess.class)).size(), targetSize + quarantineSize, "instanceCount(EmptySoftwareProcess)");
    }
    
    protected void assertSizeEventually(int targetSize) {
        assertSizeEventually(targetSize, 0, 0);
    }
    
    protected void assertSizeEventually(final int targetSize, final int quarantineSize, final int deletedSize) {
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertSize(targetSize, quarantineSize);
                assertEquals(entitiesRemoved.size(), deletedSize, "removed="+entitiesRemoved);
            }});
    }
    
    protected void assertSizeContinually(final int targetSize, final int quarantineSize, final int deletedSize) {
        Asserts.succeedsContinually(ImmutableMap.of("timeout", SHORT_WAIT_MS), new Runnable() {
            public void run() {
                assertSize(targetSize, quarantineSize);
                assertEquals(entitiesRemoved.size(), deletedSize, "removed="+entitiesRemoved);
            }});
    }
}
