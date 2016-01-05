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
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.FailingEntity;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.policy.ha.HASensors.FailureDescriptor;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ServiceRestarterTest {

    private static final int TIMEOUT_MS = 10*1000;

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity e1;
    private ServiceRestarter policy;
    private SensorEventListener<Object> eventListener;
    private List<SensorEvent<?>> events;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        events = Lists.newCopyOnWriteArrayList();
        eventListener = new SensorEventListener<Object>() {
            @Override public void onEvent(SensorEvent<Object> event) {
                events.add(event);
            }
        };
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testRestartsOnFailure() throws Exception {
        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e1.policies().add(policy);
        
        e1.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(e1.getCallHistory(), ImmutableList.of("restart"));
            }});
    }
    
    @Test(groups="Integration") // Has a 1 second wait
    public void testDoesNotRestartsWhenHealthy() throws Exception {
        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e1.policies().add(policy);
        
        e1.sensors().emit(HASensors.ENTITY_RECOVERED, new FailureDescriptor(e1, "not a failure"));
        
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertEquals(e1.getCallHistory(), ImmutableList.of());
            }});
    }
    
    @Test
    public void testEmitsFailureEventWhenRestarterFails() throws Exception {
        final FailingEntity e2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.FAIL_ON_RESTART, true));
        app.subscriptions().subscribe(e2, ServiceRestarter.ENTITY_RESTART_FAILED, eventListener);

        policy = new ServiceRestarter(new ConfigBag().configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e2.policies().add(policy);

        e2.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e2, "simulate failure"));
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(Iterables.getOnlyElement(events).getSensor(), ServiceRestarter.ENTITY_RESTART_FAILED, "events="+events);
                assertEquals(Iterables.getOnlyElement(events).getSource(), e2, "events="+events);
                assertEquals(((FailureDescriptor)Iterables.getOnlyElement(events).getValue()).getComponent(), e2, "events="+events);
            }});
        
        assertEquals(e2.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.ON_FIRE);
    }
    
    @Test
    public void testDoesNotSetOnFireOnFailure() throws Exception {
        final FailingEntity e2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.FAIL_ON_RESTART, true));
        app.subscriptions().subscribe(e2, ServiceRestarter.ENTITY_RESTART_FAILED, eventListener);

        policy = new ServiceRestarter(new ConfigBag()
                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED)
                .configure(ServiceRestarter.SET_ON_FIRE_ON_FAILURE, false));
        e2.policies().add(policy);

        e2.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e2, "simulate failure"));
        
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertNotEquals(e2.getAttribute(Attributes.SERVICE_STATE_ACTUAL), Lifecycle.ON_FIRE);
            }});
    }
    
    // Previously RestarterPolicy called entity.restart inside the event-listener thread.
    // That caused all other events for that entity's subscriptions to be queued until that
    // entity's single event handler thread was free again.
    @Test
    public void testRestartDoesNotBlockOtherSubscriptions() throws Exception {
        final CountDownLatch inRestartLatch = new CountDownLatch(1);
        final CountDownLatch continueRestartLatch = new CountDownLatch(1);
        
        final FailingEntity e2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.FAIL_ON_RESTART, true)
                .configure(FailingEntity.EXEC_ON_FAILURE, new Function<Object, Void>() {
                    @Override public Void apply(Object input) {
                        inRestartLatch.countDown();
                        try {
                            continueRestartLatch.await();
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        }
                        return null;
                    }}));
        
        e2.policies().add(PolicySpec.create(ServiceRestarter.class)
                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        e2.subscriptions().subscribe(e2, TestEntity.SEQUENCE, eventListener);

        // Cause failure, and wait for entity.restart to be blocking
        e2.sensors().emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        assertTrue(inRestartLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        
        // Expect other notifications to continue to get through
        e2.sensors().set(TestEntity.SEQUENCE, 1);
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(Iterables.getOnlyElement(events).getValue(), 1);
            }});

        // Allow restart to finish
        continueRestartLatch.countDown();
    }
}
