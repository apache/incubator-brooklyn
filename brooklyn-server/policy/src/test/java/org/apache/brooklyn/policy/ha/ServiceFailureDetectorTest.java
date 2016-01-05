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
package org.apache.brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceProblemsLogic;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.policy.ha.HASensors.FailureDescriptor;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;

public class ServiceFailureDetectorTest {
    private static final Logger log = LoggerFactory.getLogger(ServiceFailureDetectorTest.class);

    private static final int TIMEOUT_MS = 10*1000;

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity e1;
    
    private List<SensorEvent<FailureDescriptor>> events;
    private SensorEventListener<FailureDescriptor> eventListener;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        events = new CopyOnWriteArrayList<SensorEvent<FailureDescriptor>>();
        eventListener = new SensorEventListener<FailureDescriptor>() {
            @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                events.add(event);
            }
        };
        
        managementContext = new LocalManagementContextForTests();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        e1.enrichers().add(ServiceStateLogic.newEnricherForServiceStateFromProblemsAndUp());
        
        app.getManagementContext().getSubscriptionManager().subscribe(e1, HASensors.ENTITY_FAILED, eventListener);
        app.getManagementContext().getSubscriptionManager().subscribe(e1, HASensors.ENTITY_RECOVERED, eventListener);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testNotNotifiedOfFailuresForHealthy() throws Exception {
        // Create members before and after the policy is registered, to test both scenarios
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        assertNoEventsContinually();
        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
    }
    
    @Test
    public void testNotifiedOfFailure() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        
        assertEquals(events.size(), 0, "events="+events);
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testNotifiedOfFailureOnProblem() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        
        assertEquals(events.size(), 0, "events="+events);
        
        ServiceProblemsLogic.updateProblemsIndicator(e1, "test", "foo");

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testNotifiedOfFailureOnStateOnFire() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.ON_FIRE);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testNotifiedOfRecovery() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        // Make the entity fail
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);

        // And make the entity recover
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 2, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }
    
    @Test
    public void testNotifiedOfRecoveryFromProblems() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        // Make the entity fail
        ServiceProblemsLogic.updateProblemsIndicator(e1, "test", "foo");

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);

        // And make the entity recover
        ServiceProblemsLogic.clearProblemsIndicator(e1, "test");
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 2, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }
    
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testEmitsEntityFailureOnlyIfPreviouslyUp() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        // Make the entity fail
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);

        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertNoEventsContinually();
    }
    
    @Test
    public void testDisablingPreviouslyUpRequirementForEntityFailed() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP, false));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);

        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testDisablingOnFire() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.SERVICE_ON_FIRE_STABILIZATION_DELAY, Duration.PRACTICALLY_FOREVER));
        
        // Make the entity fail
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testOnFireAfterDelay() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.SERVICE_ON_FIRE_STABILIZATION_DELAY, Duration.ONE_SECOND));
        
        // Make the entity fail
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
        Time.sleep(Duration.millis(100));
        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testOnFailureDelayFromProblemAndRecover() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.SERVICE_ON_FIRE_STABILIZATION_DELAY, Duration.ONE_SECOND)
            .configure(ServiceFailureDetector.ENTITY_RECOVERED_STABILIZATION_DELAY, Duration.ONE_SECOND));
        
        // Set the entity to healthy
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        // Make the entity fail; won't set on-fire for 1s but will publish FAILED immediately.
        ServiceStateLogic.ServiceProblemsLogic.updateProblemsIndicator(e1, "test", "foo");
        EntityTestUtils.assertAttributeEqualsContinually(ImmutableMap.of("timeout", 100), e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
        
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // Now recover: will publish RUNNING immediately, but has 1s stabilisation for RECOVERED
        ServiceStateLogic.ServiceProblemsLogic.clearProblemsIndicator(e1, "test");
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        assertEquals(events.size(), 1, "events="+events);
        
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 2, "events="+events);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testAttendsToServiceState() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        // not counted as failed because not expected to be running
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually();
    }

    @Test(groups="Integration") // Has a 1 second wait
    public void testOnlyReportsFailureIfRunning() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class));
        
        // Make the entity fail
        ServiceStateLogic.setExpectedState(e1, Lifecycle.STARTING);
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually();
    }
    
    @Test
    public void testReportsFailureWhenAlreadyDownOnRegisteringPolicy() throws Exception {
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP, false));

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenAlreadyOnFireOnRegisteringPolicy() throws Exception {
        ServiceStateLogic.setExpectedState(e1, Lifecycle.ON_FIRE);

        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP, false));

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test(groups="Integration") // Has a 1.5 second wait
    public void testRepublishedFailure() throws Exception {
        Duration republishPeriod = Duration.millis(100);

        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
                .configure(ServiceFailureDetector.ENTITY_FAILED_REPUBLISH_TIME, republishPeriod));
            
        // Set the entity to healthy
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        // Make the entity fail;
        ServiceStateLogic.ServiceProblemsLogic.updateProblemsIndicator(e1, "test", "foo");
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);

        //wait for at least 10 republish events (~1 sec)
        assertEventsSizeEventually(10);

        // Now recover
        ServiceStateLogic.ServiceProblemsLogic.clearProblemsIndicator(e1, "test");
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);

        //once recovered check no more failed events emitted periodically
        assertEventsSizeContiniually(events.size());

        SensorEvent<FailureDescriptor> prevEvent = null;
        for (SensorEvent<FailureDescriptor> event : events) {
            if (prevEvent != null) {
                long repeatOffset = event.getTimestamp() - prevEvent.getTimestamp();
                long deviation = Math.abs(repeatOffset - republishPeriod.toMilliseconds());
                if (deviation > republishPeriod.toMilliseconds()/10 &&
                        //warn only if recovered is too far away from the last failure
                        (!event.getSensor().equals(HASensors.ENTITY_RECOVERED) ||
                        repeatOffset > republishPeriod.toMilliseconds())) {
                    log.error("The time between failure republish (" + repeatOffset + "ms) deviates too much from the expected " + republishPeriod + ". prevEvent=" + prevEvent + ", event=" + event);
                }
            }
            prevEvent = event;
        }
        
        //make sure no republish takes place after recovered
        assertEquals(prevEvent.getSensor(), HASensors.ENTITY_RECOVERED);
    }
    
    private void assertEventsSizeContiniually(final int size) {
        Asserts.succeedsContinually(MutableMap.of("timeout", 500), new Runnable() {
            @Override
            public void run() {
                assertTrue(events.size() == size, "assertEventsSizeContiniually expects " + size + " events but found " + events.size() + ": " + events);
            }
        });
    }

    private void assertEventsSizeEventually(final int size) {
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override
            public void run() {
                assertTrue(events.size() >= size, "assertEventsSizeContiniually expects at least " + size + " events but found " + events.size() + ": " + events);
            }
        });
    }

    private void assertHasEvent(Sensor<?> sensor, Predicate<Object> componentPredicate, Predicate<? super CharSequence> descriptionPredicate) {
        for (SensorEvent<FailureDescriptor> event : events) {
            if (event.getSensor().equals(sensor) && 
                    (componentPredicate == null || componentPredicate.apply(event.getValue().getComponent())) &&
                    (descriptionPredicate == null || descriptionPredicate.apply(event.getValue().getDescription()))) {
                return;
            }
        }
        fail("No matching "+sensor+" event found; events="+events);
    }
    
    private void assertHasEventEventually(final Sensor<?> sensor, final Predicate<Object> componentPredicate, final Predicate<? super CharSequence> descriptionPredicate) {
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertHasEvent(sensor, componentPredicate, descriptionPredicate);
            }});
    }
    
    private void assertNoEventsContinually() {
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
}
