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
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogicTest;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.policy.ha.HASensors.FailureDescriptor;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;

/** also see more primitive tests in {@link ServiceStateLogicTest} */
public class ServiceFailureDetectorStabilizationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceFailureDetectorStabilizationTest.class);

    private static final int TIMEOUT_MS = 10*1000;
    private static final int OVERHEAD = 250;

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity e1;
    
    private List<SensorEvent<FailureDescriptor>> events;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        events = new CopyOnWriteArrayList<SensorEvent<FailureDescriptor>>();
        
        managementContext = new LocalManagementContextForTests();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        ServiceStateLogic.setExpectedState(e1, Lifecycle.RUNNING);
        
        app.getManagementContext().getSubscriptionManager().subscribe(
                e1, 
                HASensors.ENTITY_FAILED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
        app.getManagementContext().getSubscriptionManager().subscribe(
                e1, 
                HASensors.ENTITY_RECOVERED, 
                new SensorEventListener<FailureDescriptor>() {
                    @Override public void onEvent(SensorEvent<FailureDescriptor> event) {
                        events.add(event);
                    }
                });
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test(groups="Integration") // Because slow
    public void testNotNotifiedOfTemporaryFailuresDuringStabilisationDelay() throws Exception {
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
                .configure(ServiceFailureDetector.ENTITY_FAILED_STABILIZATION_DELAY, Duration.ONE_MINUTE));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        Thread.sleep(100);
        e1.sensors().set(TestEntity.SERVICE_UP, true);

        assertNoEventsContinually();
    }
    
    @Test(groups="Integration") // Because slow
    public void testNotifiedOfFailureAfterStabilisationDelay() throws Exception {
        final int stabilisationDelay = 1000;
        
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
                .configure(ServiceFailureDetector.ENTITY_FAILED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test(groups="Integration") // Because slow
    public void testFailuresThenUpDownResetsStabilisationCount() throws Exception {
        LOG.debug("Running testFailuresThenUpDownResetsStabilisationCount");
        final long stabilisationDelay = 1000;
        
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
                .configure(ServiceFailureDetector.ENTITY_FAILED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));

        e1.sensors().set(TestEntity.SERVICE_UP, true);
        Thread.sleep(OVERHEAD);
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test(groups="Integration") // Because slow
    public void testNotNotifiedOfTemporaryRecoveryDuringStabilisationDelay() throws Exception {
        final long stabilisationDelay = 1000;
        
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
                .configure(ServiceFailureDetector.ENTITY_RECOVERED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        events.clear();
        
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        Thread.sleep(100);
        e1.sensors().set(TestEntity.SERVICE_UP, false);

        assertNoEventsContinually(Duration.of(stabilisationDelay + OVERHEAD));
    }
    
    @Test(groups="Integration") // Because slow
    public void testNotifiedOfRecoveryAfterStabilisationDelay() throws Exception {
        final int stabilisationDelay = 1000;
        
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
                .configure(ServiceFailureDetector.ENTITY_RECOVERED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        events.clear();

        e1.sensors().set(TestEntity.SERVICE_UP, true);
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
    }
    
    @Test(groups="Integration") // Because slow
    public void testRecoversThenDownUpResetsStabilisationCount() throws Exception {
        final long stabilisationDelay = 1000;
        
        e1.enrichers().add(EnricherSpec.create(ServiceFailureDetector.class)
                .configure(ServiceFailureDetector.ENTITY_RECOVERED_STABILIZATION_DELAY, Duration.of(stabilisationDelay)));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        assertHasEventEventually(HASensors.ENTITY_FAILED, Predicates.<Object>equalTo(e1), null);
        events.clear();
        
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));
        
        e1.sensors().set(TestEntity.SERVICE_UP, false);
        Thread.sleep(OVERHEAD);
        e1.sensors().set(TestEntity.SERVICE_UP, true);
        assertNoEventsContinually(Duration.of(stabilisationDelay - OVERHEAD));

        assertHasEventEventually(HASensors.ENTITY_RECOVERED, Predicates.<Object>equalTo(e1), null);
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

    private void assertNoEventsContinually(Duration duration) {
        Asserts.succeedsContinually(ImmutableMap.of("timeout", duration), new Runnable() {
            @Override public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
    
    private void assertNoEventsContinually() {
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertTrue(events.isEmpty(), "events="+events);
            }});
    }
}
