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
package org.apache.brooklyn.core.location;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.RecordingSensorEventListener;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.sensor.BasicSensorEvent;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class LocationSubscriptionTest {

    // TODO Duplication between this and PolicySubscriptionTest
    
    private SimulatedLocation loc;
    private TestApplication app;
    private TestEntity observedEntity;
    private BasicGroup observedGroup;
    private TestEntity observedChildEntity;
    private TestEntity observedMemberEntity;
    private TestEntity otherEntity;
    private RecordingSensorEventListener<Object> listener;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc = app.newSimulatedLocation();
        observedEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedChildEntity = observedEntity.createAndManageChild(EntitySpec.create(TestEntity.class));

        observedGroup = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        observedMemberEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedGroup.addMember(observedMemberEntity);
        
        otherEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        listener = new RecordingSensorEventListener<>();
        
        app.start(ImmutableList.of(loc));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testSubscriptionReceivesEvents() {
        loc.subscriptions().subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        loc.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);
        loc.subscriptions().subscribe(observedEntity, TestEntity.MY_NOTIF, listener);
        
        otherEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        observedEntity.sensors().emit(TestEntity.MY_NOTIF, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedEntity, 123),
                        new BasicSensorEvent<String>(TestEntity.NAME, observedEntity, "myname"),
                        new BasicSensorEvent<Integer>(TestEntity.MY_NOTIF, observedEntity, 456)));
            }});
    }
    
    @Test
    public void testSubscriptionToAllReceivesEvents() {
        loc.subscriptions().subscribe(null, TestEntity.SEQUENCE, listener);
        
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        otherEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedEntity, 123),
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 456)));
            }});
    }
    
    @Test
    public void testSubscribeToChildrenReceivesEvents() {
        loc.subscriptions().subscribeToChildren(observedEntity, TestEntity.SEQUENCE, listener);
        
        observedChildEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedChildEntity, 123)));
            }});
    }
    
    @Test
    public void testSubscribeToChildrenReceivesEventsForDynamicallyAddedChildren() {
        loc.subscriptions().subscribeToChildren(observedEntity, TestEntity.SEQUENCE, listener);
        
        final TestEntity observedChildEntity2 = observedEntity.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedChildEntity2.sensors().set(TestEntity.SEQUENCE, 123);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedChildEntity2, 123)));
            }});
    }
    
    @Test
    public void testSubscribeToMembersReceivesEvents() {
        loc.subscriptions().subscribeToMembers(observedGroup, TestEntity.SEQUENCE, listener);
        
        observedMemberEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedGroup.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedMemberEntity, 123)));
            }});
    }
    
    @Test
    public void testSubscribeToMembersReceivesEventsForDynamicallyAddedMembers() {
        loc.subscriptions().subscribeToMembers(observedGroup, TestEntity.SEQUENCE, listener);
        
        final TestEntity observedMemberEntity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        observedGroup.addMember(observedMemberEntity2);
        observedMemberEntity2.sensors().set(TestEntity.SEQUENCE, 123);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedMemberEntity2, 123)));
            }});
    }
    
    @Test(groups="Integration")
    public void testSubscribeToMembersIgnoresEventsForDynamicallyRemovedMembers() {
        loc.subscriptions().subscribeToMembers(observedGroup, TestEntity.SEQUENCE, listener);
        
        observedGroup.removeMember(observedMemberEntity);
        
        observedMemberEntity.sensors().set(TestEntity.SEQUENCE, 123);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of());
            }});
    }
    
    @Test
    public void testUnsubscribeRemovesAllSubscriptionsForThatEntity() {
        loc.subscriptions().subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        loc.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);
        loc.subscriptions().subscribe(observedEntity, TestEntity.MY_NOTIF, listener);
        loc.subscriptions().subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        loc.subscriptions().unsubscribe(observedEntity);
        
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        observedEntity.sensors().emit(TestEntity.MY_NOTIF, 123);
        otherEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 456)));
            }});
    }
    
    @Test
    public void testUnsubscribeUsingHandleStopsEvents() {
        SubscriptionHandle handle1 = loc.subscriptions().subscribe(observedEntity, TestEntity.SEQUENCE, listener);
        SubscriptionHandle handle2 = loc.subscriptions().subscribe(observedEntity, TestEntity.NAME, listener);
        SubscriptionHandle handle3 = loc.subscriptions().subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        
        loc.subscriptions().unsubscribe(observedEntity, handle2);
        
        observedEntity.sensors().set(TestEntity.SEQUENCE, 123);
        observedEntity.sensors().set(TestEntity.NAME, "myname");
        otherEntity.sensors().set(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.getEvents(), ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, observedEntity, 123),
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 456)));
            }});
    }
    
    @Test
    public void testSubscriptionReceivesEventsInOrder() {
        final int NUM_EVENTS = 100;
        loc.subscriptions().subscribe(observedEntity, TestEntity.MY_NOTIF, listener);

        for (int i = 0; i < NUM_EVENTS; i++) {
            observedEntity.sensors().emit(TestEntity.MY_NOTIF, i);
        }
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(Iterables.size(listener.getEvents()), NUM_EVENTS);
                for (int i = 0; i < NUM_EVENTS; i++) {
                    assertEquals(Iterables.get(listener.getEvents(), i).getValue(), i);
                }
            }});
    }

}
