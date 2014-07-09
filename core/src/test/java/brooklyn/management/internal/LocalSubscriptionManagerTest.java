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
package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;
import brooklyn.test.entity.TestEntity;

/**
 * testing the {@link SubscriptionManager} and associated classes.
 */
public class LocalSubscriptionManagerTest extends BrooklynAppUnitTestSupport {
    
    private static final int TIMEOUT_MS = 5000;
    
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    private void manage(Entity ...entities) {
        for (Entity e: entities)
            Entities.manage(e);
    }

    @Test
    public void testSubscribeToEntityAttributeChange() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        app.subscribe(entity, TestEntity.SEQUENCE, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    latch.countDown();
                }});
        entity.setSequenceValue(1234);
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timeout waiting for Event on TestEntity listener");
        }
    }
    
    @Test
    public void testSubscribeToEntityWithAttributeWildcard() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        app.subscribe(entity, null, new SensorEventListener<Object>() {
            @Override public void onEvent(SensorEvent<Object> event) {
                latch.countDown();
            }});
        entity.setSequenceValue(1234);
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timeout waiting for Event on TestEntity listener");
        }
    }
    
    @Test
    public void testSubscribeToAttributeChangeWithEntityWildcard() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        app.subscribe(null, TestEntity.SEQUENCE, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    latch.countDown();
                }});
        entity.setSequenceValue(1234);
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timeout waiting for Event on TestEntity listener");
        }
    }
    
    @Test
    public void testSubscribeToChildAttributeChange() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        app.subscribeToChildren(app, TestEntity.SEQUENCE, new SensorEventListener<Object>() {
            @Override public void onEvent(SensorEvent<Object> event) {
                latch.countDown();
            }});
        entity.setSequenceValue(1234);
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timeout waiting for Event on child TestEntity listener");
        }
    }
    
    @Test
    public void testSubscribeToMemberAttributeChange() throws Exception {
        BasicGroup group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        TestEntity member = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        manage(group, member);
        
        group.addMember(member);

        final List<SensorEvent<Integer>> events = new CopyOnWriteArrayList<SensorEvent<Integer>>();
        final CountDownLatch latch = new CountDownLatch(1);
        app.subscribeToMembers(group, TestEntity.SEQUENCE, new SensorEventListener<Integer>() {
            @Override public void onEvent(SensorEvent<Integer> event) {
                events.add(event);
                latch.countDown();
            }});
        member.setAttribute(TestEntity.SEQUENCE, 123);

        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timeout waiting for Event on parent TestEntity listener");
        }
        assertEquals(events.size(), 1);
        assertEquals(events.get(0).getValue(), (Integer)123);
        assertEquals(events.get(0).getSensor(), TestEntity.SEQUENCE);
        assertEquals(events.get(0).getSource().getId(), member.getId());
    }
    
    // Regression test for ConcurrentModificationException in issue #327
    @Test(groups="Integration")
    public void testConcurrentSubscribingAndPublishing() throws Exception {
        final AtomicReference<Exception> threadException = new AtomicReference<Exception>();
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        // Repeatedly subscribe and unsubscribe, so listener-set constantly changing while publishing to it.
        // First create a stable listener so it is always the same listener-set object.
        Thread thread = new Thread() {
            public void run() {
                try {
                    SensorEventListener<Object> noopListener = new SensorEventListener<Object>() {
                        @Override public void onEvent(SensorEvent<Object> event) {
                        }
                    };
                    app.subscribe(null, TestEntity.SEQUENCE, noopListener);
                    while (!Thread.currentThread().isInterrupted()) {
                        SubscriptionHandle handle = app.subscribe(null, TestEntity.SEQUENCE, noopListener);
                        app.unsubscribe(null, handle);
                    }
                } catch (Exception e) {
                    threadException.set(e);
                }
            }
        };
        
        try {
            thread.start();
            for (int i = 0; i < 10000; i++) {
                entity.setAttribute(TestEntity.SEQUENCE, i);
            }
        } finally {
            thread.interrupt();
        }

        if (threadException.get() != null) throw threadException.get();
    }

}
