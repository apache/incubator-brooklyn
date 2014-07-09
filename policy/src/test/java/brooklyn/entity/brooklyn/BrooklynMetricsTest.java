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
package brooklyn.entity.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEventListener;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class BrooklynMetricsTest {

    private static final long TIMEOUT_MS = 2*1000;
    
    TestApplication app;
    SimulatedLocation loc;
    BrooklynMetrics brooklynMetrics;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        brooklynMetrics = app.createAndManageChild(EntitySpec.create(BrooklynMetrics.class).configure("updatePeriod", 10L));
        Entities.manage(brooklynMetrics);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testInitialBrooklynMetrics() {
        app.start(ImmutableList.of(loc));

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), (Long)1L);
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_TASKS_SUBMITTED) > 0);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_INCOMPLETE_TASKS), (Long)0L);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_ACTIVE_TASKS), (Long)0L);
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_PUBLISHED) > 0);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_DELIVERED), (Long)0L);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_SUBSCRIPTIONS), (Long)0L);
            }});
    }
    
    @Test
    public void testBrooklynMetricsIncremented() {
        TestEntity e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), (Long)2L); // for app and testEntity's start
            }});

        // Note if attribute has not yet been set, the value returned could be null
        final long effsInvoked = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EFFECTORS_INVOKED, 0);
        final long tasksSubmitted = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_TASKS_SUBMITTED, 0);
        final long eventsPublished = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EVENTS_PUBLISHED, 0);
        final long eventsDelivered = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EVENTS_DELIVERED, 0);
        final long subscriptions = getAttribute(brooklynMetrics, BrooklynMetrics.NUM_SUBSCRIPTIONS, 0);

        // Invoking an effector increments effector/task count
        e.myEffector();
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), (Long)(effsInvoked+1));
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_TASKS_SUBMITTED) > tasksSubmitted);
            }});
        
        // Setting attribute causes event to be published and delivered to the subscriber
        // Note that the brooklyn metrics entity itself is also publishing sensors
        app.subscribe(e, TestEntity.SEQUENCE, SensorEventListener.NOOP);
        e.setAttribute(TestEntity.SEQUENCE, 1);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_PUBLISHED) > eventsPublished);
                assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_DELIVERED) > eventsDelivered);
                assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_SUBSCRIPTIONS), (Long)1L);
            }});
    }
    
    private long getAttribute(Entity entity, AttributeSensor<Long> attribute, long defaultVal) {
        Long result = entity.getAttribute(attribute);
        return (result != null) ? result : defaultVal;
    }
}
