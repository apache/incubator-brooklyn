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
package org.apache.brooklyn.entity.group;

import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.collect.Iterables.find;
import static org.apache.brooklyn.core.entity.EntityPredicates.displayNameEqualTo;
import static org.apache.brooklyn.entity.group.DynamicGroup.ENTITY_FILTER;
import static org.apache.brooklyn.entity.group.DynamicMultiGroup.BUCKET_FUNCTION;
import static org.apache.brooklyn.entity.group.DynamicMultiGroup.RESCAN_INTERVAL;
import static org.apache.brooklyn.entity.group.DynamicMultiGroupImpl.bucketFromAttribute;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.group.DynamicMultiGroup;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class DynamicMultiGroupTest {

    private static final AttributeSensor<String> SENSOR = Sensors.newSensor(String.class, "multigroup.test");

    private TestApplication app;


    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class, new LocalManagementContextForTests());
        app.start(ImmutableList.of(new SimulatedLocation()));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (app != null)
            Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testBucketDistributionFromSubscription() {
        Group group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        final DynamicMultiGroup dmg = app.createAndManageChild(
                EntitySpec.create(DynamicMultiGroup.class)
                        .configure(ENTITY_FILTER, instanceOf(TestEntity.class))
                        .configure(BUCKET_FUNCTION, bucketFromAttribute(SENSOR))
        );
        app.subscriptions().subscribeToChildren(group, SENSOR, new SensorEventListener<String>() {
            public void onEvent(SensorEvent<String> event) { dmg.rescanEntities(); }
        });

        EntitySpec<TestEntity> childSpec = EntitySpec.create(TestEntity.class);
        TestEntity child1 = group.addChild(EntitySpec.create(childSpec).displayName("child1"));
        TestEntity child2 = group.addChild(EntitySpec.create(childSpec).displayName("child2"));

        checkDistribution(group, dmg, childSpec, child1, child2);
    }

    @Test(groups="Integration") // because takes 4s or so
    public void testBucketDistributionWithRescan() {
        Group group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        final DynamicMultiGroup dmg = app.createAndManageChild(
                EntitySpec.create(DynamicMultiGroup.class)
                        .configure(ENTITY_FILTER, instanceOf(TestEntity.class))
                        .configure(BUCKET_FUNCTION, bucketFromAttribute(SENSOR))
                        .configure(RESCAN_INTERVAL, 1L)
        );

        EntitySpec<TestEntity> childSpec = EntitySpec.create(TestEntity.class);
        TestEntity child1 = group.addChild(EntitySpec.create(childSpec).displayName("child1"));
        TestEntity child2 = group.addChild(EntitySpec.create(childSpec).displayName("child2"));
        
        checkDistribution(group, dmg, childSpec, child1, child2);
    }

    @Test
    public void testRemovesEmptyBuckets() {
        Group group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        final DynamicMultiGroup dmg = app.createAndManageChild(
                EntitySpec.create(DynamicMultiGroup.class)
                        .configure(ENTITY_FILTER, instanceOf(TestEntity.class))
                        .configure(BUCKET_FUNCTION, bucketFromAttribute(SENSOR))
        );
        app.subscriptions().subscribeToChildren(group, SENSOR, new SensorEventListener<String>() {
            public void onEvent(SensorEvent<String> event) { dmg.rescanEntities(); }
        });

        EntitySpec<TestEntity> childSpec = EntitySpec.create(TestEntity.class);
        TestEntity child1 = app.createAndManageChild(EntitySpec.create(childSpec).displayName("child1"));
        TestEntity child2 = app.createAndManageChild(EntitySpec.create(childSpec).displayName("child2"));

        // Expect two buckets: bucketA and bucketB 
        child1.sensors().set(SENSOR, "bucketA");
        child2.sensors().set(SENSOR, "bucketB");
        dmg.rescanEntities();
        Group bucketA = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketA"), null);
        Group bucketB = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketB"), null);
        assertNotNull(bucketA);
        assertNotNull(bucketB);
        
        // Expect second bucket to be removed when empty 
        child1.sensors().set(SENSOR, "bucketA");
        child2.sensors().set(SENSOR, "bucketA");
        dmg.rescanEntities();
        bucketA = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketA"), null);
        bucketB = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketB"), null);
        assertNotNull(bucketA);
        assertNull(bucketB);
    }

    private void checkDistribution(final Group group, final DynamicMultiGroup dmg, final EntitySpec<TestEntity> childSpec, final TestEntity child1, final TestEntity child2) {
        // Start with both children in bucket A; there is no bucket B
        child1.sensors().set(SENSOR, "bucketA");
        child2.sensors().set(SENSOR, "bucketA");
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Group bucketA = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketA"), null);
                Group bucketB = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketB"), null);
                assertNotNull(bucketA);
                assertNull(bucketB);
                assertEquals(ImmutableSet.copyOf(bucketA.getMembers()), ImmutableSet.of(child1, child2));
            }
        });

        // Move child 1 into bucket B
        child1.sensors().set(SENSOR, "bucketB");
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Group bucketA = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketA"), null);
                Group bucketB = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketB"), null);
                assertNotNull(bucketA);
                assertNotNull(bucketB);
                assertEquals(ImmutableSet.copyOf(bucketB.getMembers()), ImmutableSet.of(child1));
                assertEquals(ImmutableSet.copyOf(bucketA.getMembers()), ImmutableSet.of(child2));
            }
        });

        // Move child 2 into bucket B; there is now no bucket A
        child2.sensors().set(SENSOR, "bucketB");
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Group bucketA = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketA"), null);
                Group bucketB = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketB"), null);
                assertNull(bucketA);
                assertNotNull(bucketB);
                assertEquals(ImmutableSet.copyOf(bucketB.getMembers()), ImmutableSet.of(child1, child2));
            }
        });

        // Add new child 3, associated with new bucket C
        final TestEntity child3 = group.addChild(EntitySpec.create(childSpec).displayName("child3"));
        child3.sensors().set(SENSOR, "bucketC");
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Group bucketC = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketC"), null);
                assertNotNull(bucketC);
                assertEquals(ImmutableSet.copyOf(bucketC.getMembers()), ImmutableSet.of(child3));
            }
        });

        // Un-set the sensor on child 3 -- gets removed from bucket C, which then
        // disappears as it is empty.
        child3.sensors().set(SENSOR, null);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Group bucketB = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketB"), null);
                Group bucketC = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketC"), null);
                assertNotNull(bucketB);
                assertNull(bucketC);
                assertEquals(ImmutableSet.copyOf(bucketB.getMembers()), ImmutableSet.of(child1, child2));
            }
        });

        // Add child 3 back to bucket C -- this should result in a new group entity
        child3.sensors().set(SENSOR, "bucketC");
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                Group bucketC = (Group) find(dmg.getChildren(), displayNameEqualTo("bucketC"), null);
                assertNotNull(bucketC);
                assertEquals(ImmutableSet.copyOf(bucketC.getMembers()), ImmutableSet.of(child3));
            }
        });
    }

}
