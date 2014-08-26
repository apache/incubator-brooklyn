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
package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ServiceProblemsLogic;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ManagementContext;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class ServiceFailureDetectorTest {

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
        e1.addEnricher(ServiceStateLogic.newEnricherForServiceStateFromProblemsAndUp());
        
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
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        assertNoEventsContinually();
        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
    }
    
    @Test
    public void testNotifiedOfFailure() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        
        assertEquals(events.size(), 0, "events="+events);
        
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testNotifiedOfFailureOnProblem() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        
        assertEquals(events.size(), 0, "events="+events);
        
        ServiceProblemsLogic.updateProblemsIndicator(e1, "test", "foo");

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testNotifiedOfFailureOnStateOnFire() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.ON_FIRE);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 1, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testNotifiedOfRecovery() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);

        // And make the entity recover
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 2, "events="+events);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }
    
    @Test
    public void testNotifiedOfRecoveryFromProblems() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.setAttribute(TestEntity.SERVICE_UP, true);
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
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, false);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);

        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertNoEventsContinually();
    }
    
    @Test
    public void testDisablingPreviouslyUpRequirementForEntityFailed() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP, false));
        
        e1.setAttribute(TestEntity.SERVICE_UP, false);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);

        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testDisablingOnFire() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.SERVICE_ON_FIRE_STABILIZATION_DELAY, Duration.PRACTICALLY_FOREVER));
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testOnFireAfterDelay() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.SERVICE_ON_FIRE_STABILIZATION_DELAY, Duration.ONE_SECOND));
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
        Time.sleep(Duration.millis(100));
        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testOnFailureDelayFromProblemAndRecover() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.SERVICE_ON_FIRE_STABILIZATION_DELAY, Duration.ONE_SECOND)
            .configure(ServiceFailureDetector.ENTITY_RECOVERED_STABILIZATION_DELAY, Duration.ONE_SECOND));
        
        // Make the entity fail
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);

        EntityTestUtils.assertAttributeEqualsEventually(e1, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        ServiceStateLogic.ServiceProblemsLogic.updateProblemsIndicator(e1, "test", "foo");
        
        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
        Time.sleep(Duration.millis(100));
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        assertEquals(e1.getAttribute(TestEntity.SERVICE_STATE_ACTUAL), Lifecycle.RUNNING);
        
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // Now recover
        ServiceStateLogic.ServiceProblemsLogic.clearProblemsIndicator(e1, "test");
        EntityTestUtils.assertAttributeEqualsEventually(e1, TestEntity.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        assertEquals(events.size(), 1, "events="+events);
        
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
        assertEquals(events.size(), 2, "events="+events);
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testAttendsToServiceState() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        // not counted as failed because not expected to be running
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually();
    }

    @Test(groups="Integration") // Has a 1 second wait
    public void testOnlyReportsFailureIfRunning() throws Exception {
        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class));
        
        // Make the entity fail
        ServiceStateLogic.setExpectedState(e1, Lifecycle.STARTING);
        e1.setAttribute(TestEntity.SERVICE_UP, true);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually();
    }
    
    @Test
    public void testReportsFailureWhenAlreadyDownOnRegisteringPolicy() throws Exception {
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        e1.setAttribute(TestEntity.SERVICE_UP, false);

        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP, false));

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test
    public void testReportsFailureWhenAlreadyOnFireOnRegisteringPolicy() throws Exception {
        ServiceStateLogic.setExpectedState(e1, Lifecycle.ON_FIRE);

        e1.addEnricher(EnricherSpec.create(ServiceFailureDetector.class)
            .configure(ServiceFailureDetector.ENTITY_FAILED_ONLY_IF_PREVIOUSLY_UP, false));

        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
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
