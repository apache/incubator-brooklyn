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

import static org.apache.brooklyn.test.Asserts.assertEqualsIgnoringOrder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class DynamicGroupTest {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicGroupTest.class);
    
    private static final int TIMEOUT_MS = 50*1000;
    private static final int VERY_SHORT_WAIT_MS = 100;
    
    private TestApplication app;
    private DynamicGroup group;
    private TestEntity e1;
    private TestEntity e2;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        group = app.createAndManageChild(EntitySpec.create(DynamicGroup.class));
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        e2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testGroupWithNoFilterReturnsNoMembers() throws Exception {
        assertTrue(group.getMembers().isEmpty());
    }
    
    @Test
    public void testGroupWithNonMatchingFilterReturnsNoMembers() throws Exception {
        group.setEntityFilter(Predicates.alwaysFalse());
        assertTrue(group.getMembers().isEmpty());
    }
    
    @Test
    public void testGroupWithMatchingFilterReturnsOnlyMatchingMembers() throws Exception {
        group.setEntityFilter(EntityPredicates.idEqualTo(e1.getId()));
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableList.of(e1));
    }
    
    @Test
    public void testCanUsePredicateAsFilter() throws Exception {
        Predicate<Entity> predicate = Predicates.<Entity>equalTo(e1);
        group.setEntityFilter(predicate);
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e1));
    }
    
    @Test
    public void testGroupWithMatchingFilterReturnsEverythingThatMatches() throws Exception {
        group.setEntityFilter(Predicates.alwaysTrue());
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e1, e2, app, group));
    }
    
    @Test
    public void testGroupDetectsNewlyManagedMatchingMember() throws Exception {
        group.setEntityFilter(EntityPredicates.displayNameEqualTo("myname"));
        final Entity e3 = app.addChild(EntitySpec.create(TestEntity.class).displayName("myname"));
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e3));
            }});
    }

    @Test
    public void testGroupUsesNewFilter() throws Exception {
        final Entity e3 = app.addChild(EntitySpec.create(TestEntity.class).displayName("myname"));

        group.setEntityFilter(EntityPredicates.displayNameEqualTo("myname"));
        
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e3));
    }

    @Test
    public void testGroupDetectsChangedEntities() throws Exception {
        final AttributeSensor<String> MY_ATTRIBUTE = Sensors.newStringSensor("test.myAttribute", "My test attribute");
        
        group.setEntityFilter(EntityPredicates.attributeEqualTo(MY_ATTRIBUTE, "yes"));
        group.addSubscription(null, MY_ATTRIBUTE);
        
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of());
        
        // When changed (such that subscription spots it), then entity added
        e1.sensors().set(MY_ATTRIBUTE, "yes");
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e1));
            }});

        // When it stops matching, entity is removed        
        e1.sensors().set(MY_ATTRIBUTE, "no");
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of());
            }});
    }
    
    @Test
    public void testGroupDetectsChangedEntitiesMatchingFilter() throws Exception {
        final AttributeSensor<String> MY_ATTRIBUTE = Sensors.newStringSensor("test.myAttribute", "My test attribute");
        group.setEntityFilter(new Predicate<Entity>() {
            @Override public boolean apply(Entity input) {
                if (!(input.getAttribute(MY_ATTRIBUTE) == "yes")) 
                    return false;
                if (input.equals(e1)) {
                    LOG.info("testGroupDetectsChangedEntitiesMatchingFilter scanned e1 when MY_ATTRIBUTE is yes; not a bug, but indicates things may be running slowly");
                    return false;
                }
                return true;
            }});
        group.addSubscription(null, MY_ATTRIBUTE, new Predicate<SensorEvent<?>>() {
            @Override public boolean apply(SensorEvent<?> input) {
                return !e1.equals(input.getSource());
            }});
        
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of());
        
        // Does not subscribe to things which do not match predicate filter, 
        // so event from e1 should normally be ignored 
        // but pending rescans may cause it to pick up e1, so we ignore e1 in the entity filter also
        e1.sensors().set(MY_ATTRIBUTE, "yes");
        e2.sensors().set(MY_ATTRIBUTE, "yes");
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e2));
            }});
    }
    
    @Test
    public void testGroupRemovesUnmanagedEntity() throws Exception {
        group.setEntityFilter(EntityPredicates.idEqualTo(e1.getId()));
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e1));
        
        Entities.unmanage(e1);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of());
            }});
    }
    
    // TODO Previously did Entities.unmanage(e1), but that now causes the group to be told
    //      (to preserve referential integrity). Now doing Entities.unmanage(e3) instead.
    //      Note that group.stop is now deprecated, so can delete this test when the method 
    //      is deleted!
    @Test
    public void testStoppedGroupIgnoresComingAndGoingsOfEntities() throws Exception {
        Entity e3 = new AbstractEntity() {};
        group.setEntityFilter(Predicates.instanceOf(TestEntity.class));
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e1, e2));
        group.stop();
        
        e3.setParent(app);
        Entities.manage(e3);
        Asserts.succeedsContinually(MutableMap.of("timeout", VERY_SHORT_WAIT_MS), new Runnable() {
            public void run() {
                assertEquals(ImmutableSet.copyOf(group.getMembers()), ImmutableSet.of(e1, e2));
            }});
                
        Entities.unmanage(e3);
        Asserts.succeedsContinually(MutableMap.of("timeout", VERY_SHORT_WAIT_MS), new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(ImmutableSet.copyOf(group.getMembers()), ImmutableSet.of(e1, e2));
            }});
    }
    
    @Test
    public void testUnmanagedGroupIgnoresComingAndGoingsOfEntities() {
        Entity e3 = new AbstractEntity() {};
        group.setEntityFilter(Predicates.instanceOf(TestEntity.class));
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e1, e2));
        Entities.unmanage(group);
        
        e3.setParent(app);
        Entities.manage(e3);
        Asserts.succeedsContinually(MutableMap.of("timeout", VERY_SHORT_WAIT_MS), new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(ImmutableSet.copyOf(group.getMembers()), ImmutableSet.of(e1, e2));
            }});
    }

    // Motivated by strange behavior observed testing load-balancing policy, but this passed...
    //
    // Note that addMember/removeMember is now async for when member-entity is managed/unmanaged,
    // so to avoid race where entity is already unmanaged by the time addMember does its stuff,
    // we wait for it to really be added.
    @Test
    public void testGroupAddsAndRemovesManagedAndUnmanagedEntitiesExactlyOnce() throws Exception {
        final int NUM_CYCLES = 100;
        group.setEntityFilter(Predicates.instanceOf(TestEntity.class));

        final Set<TestEntity> entitiesNotified = Sets.newConcurrentHashSet();
        final AtomicInteger addedNotifications = new AtomicInteger(0);
        final AtomicInteger removedNotifications = new AtomicInteger(0);
        final List<Exception> exceptions = new CopyOnWriteArrayList<Exception>();
        
        app.subscriptions().subscribe(group, DynamicGroup.MEMBER_ADDED, new SensorEventListener<Entity>() {
            public void onEvent(SensorEvent<Entity> event) {
                try {
                    TestEntity val = (TestEntity) event.getValue();
                    LOG.debug("Notified of member added: member={}, thread={}", val.getId(), Thread.currentThread().getName());
                    assertEquals(group, event.getSource());
                    assertTrue(entitiesNotified.add(val));
                    addedNotifications.incrementAndGet();
                } catch (Throwable t) {
                    LOG.error("Error on event $event", t);
                    exceptions.add(new Exception("Error on event $event", t));
                }
            }});

        app.subscriptions().subscribe(group, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            public void onEvent(SensorEvent<Entity> event) {
                try {
                    TestEntity val = (TestEntity) event.getValue();
                    LOG.debug("Notified of member removed: member={}, thread={}", val.getId(), Thread.currentThread().getName());
                    assertEquals(group, event.getSource());
                    assertTrue(entitiesNotified.remove(val));
                    removedNotifications.incrementAndGet();
                } catch (Throwable t) {
                    LOG.error("Error on event $event", t);
                    exceptions.add(new Exception("Error on event $event", t));
                }
            }
        });

        for (int i = 0; i < NUM_CYCLES; i++) {
            final TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
            LOG.debug("Created: entity {}", i);
            Asserts.succeedsEventually(new Runnable() {
                public void run() {
                    assertTrue(entitiesNotified.contains(entity));
                }
            });
            LOG.debug("Contained in entitiesNotified: entity {}", i);
            Entities.unmanage(entity);
            LOG.debug("Unmanaged: entity {}", i);
        }

        Asserts.succeedsEventually(ImmutableMap.of("timeout", Duration.of(10, TimeUnit.SECONDS)), new Runnable() {
            public void run() {
                int added = addedNotifications.get(),
                    removed = removedNotifications.get(),
                    notifications = added + removed;
                assertTrue(notifications == (NUM_CYCLES * 2) || exceptions.size() > 0,
                        "addedNotifications=" + added +
                        ", removedNotifications=" + removed +
                        ", cycles=" + NUM_CYCLES * 2 +
                        ", exceptions.size=" + exceptions.size());
            }
        });

        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
        
        assertEquals(removedNotifications.get() + addedNotifications.get(), NUM_CYCLES*2);
    }
    
    // The entityAdded/entityRemoved is now async for when member-entity is managed/unmanaged,
    // but it should always be called sequentially (i.e. semantics of a single-threaded executor).
    // Test is deliberately slow in processing entityAdded/removed calls, to try to cause
    // concurrent calls if they are going to happen at all.
    @Test(groups="Integration")
    public void testEntityAddedAndRemovedCalledSequentially() throws Exception {
        final int NUM_CYCLES = 10;
        final Set<Entity> knownMembers = Sets.newLinkedHashSet();
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger concurrentCallsCount = new AtomicInteger(0);
        final List<Exception> exceptions = new CopyOnWriteArrayList<Exception>();
        
        DynamicGroupImpl group2 = new DynamicGroupImpl() {
            @Override protected void onEntityAdded(Entity item) {
                try {
                    onCall("Member added: member="+item);
                    assertTrue(knownMembers.add(item));
                } catch (Throwable t) {
                    exceptions.add(new Exception("Error detected adding "+item, t));
                    throw Exceptions.propagate(t);
                }
            }
            @Override protected void onEntityRemoved(Entity item) {
                try {
                    onCall("Member removed: member="+item);
                    assertTrue(knownMembers.remove(item));
                } catch (Throwable t) {
                    exceptions.add(new Exception("Error detected adding "+item, t));
                    throw Exceptions.propagate(t);
                }
            }
            private void onCall(String msg) {
                LOG.debug(msg+", thread="+Thread.currentThread().getName());
                try {
                    assertEquals(concurrentCallsCount.incrementAndGet(), 1);
                    Time.sleep(100);
                } finally {
                    concurrentCallsCount.decrementAndGet();
                }
                notificationCount.incrementAndGet();
            }
        };
        group2.config().set(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class));
        app.addChild(group2);
        Entities.manage(group2);
        
        for (int i = 0; i < NUM_CYCLES; i++) {
            TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
            Entities.unmanage(entity);
        }

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(notificationCount.get() == (NUM_CYCLES*2) || exceptions.size() > 0);
            }});

        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
        
        assertEquals(notificationCount.get(), NUM_CYCLES*2);
    }
    
    // See Deadlock in https://github.com/brooklyncentral/brooklyn/issues/378
    // TODO Now that entities are auto-managed, this test is no longer appropriate.
    // Should it be re-written or deleted?
    @Test(groups="WIP")
    public void testDoesNotDeadlockOnManagedAndMemberAddedConcurrently() throws Exception {
        final CountDownLatch rescanReachedLatch = new CountDownLatch(1);
        final CountDownLatch entityAddedReachedLatch = new CountDownLatch(1);
        final CountDownLatch rescanLatch = new CountDownLatch(1);
        final CountDownLatch entityAddedLatch = new CountDownLatch(1);
        
        final TestEntity e3 = app.addChild(EntitySpec.create(TestEntity.class));
        
        final DynamicGroupImpl group2 = new DynamicGroupImpl() {
            @Override public void rescanEntities() {
                rescanReachedLatch.countDown();
                try {
                    rescanLatch.await();
                } catch (InterruptedException e) {
                    Exceptions.propagate(e);
                }
                super.rescanEntities();
            }
            @Override protected void onEntityAdded(Entity item) {
                entityAddedReachedLatch.countDown();
                try {
                    entityAddedLatch.await();
                } catch (InterruptedException e) {
                    Exceptions.propagate(e);
                }
                super.onEntityAdded(item);
            }
        };
        group2.config().set(DynamicGroup.ENTITY_FILTER, Predicates.<Object>equalTo(e3));
        app.addChild(group2);
        
        Thread t1 = new Thread(new Runnable() {
            @Override public void run() {
                Entities.manage(group2);
            }});
        
        Thread t2 = new Thread(new Runnable() {
            @Override public void run() {
                Entities.manage(e3);
            }});

        t1.start();
        try {
            assertTrue(rescanReachedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            
            t2.start();
            assertTrue(entityAddedReachedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            
            entityAddedLatch.countDown();
            rescanLatch.countDown();
            
            t2.join(TIMEOUT_MS);
            t1.join(TIMEOUT_MS);
            assertFalse(t1.isAlive());
            assertFalse(t2.isAlive());
            
        } finally {
            t1.interrupt();
            t2.interrupt();
        }
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group2.getMembers(), ImmutableSet.of(e3));
            }});
    }
    
    // See deadlock in https://issues.apache.org/jira/browse/BROOKLYN-66
    @Test
    public void testDoesNotDeadlockOnUnmanageWhileOtherMemberBeingAdded() throws Exception {
        final CountDownLatch removingMemberReachedLatch = new CountDownLatch(1);
        final CountDownLatch addingMemberReachedLatch = new CountDownLatch(1);
        final CountDownLatch addingMemberContinueLatch = new CountDownLatch(1);
        final AtomicBoolean addingMemberDoLatching = new AtomicBoolean(false);
        final List<Entity> membersAdded = new CopyOnWriteArrayList<Entity>();
        
        final DynamicGroupImpl group2 = new DynamicGroupImpl() {
            private final BasicSensorSupport interceptedSensors = new BasicSensorSupport() {
                @Override
                public <T> void emit(Sensor<T> sensor, T val) {
                    // intercept inside AbstractGroup.addMember, while it still holds lock on members
                    if (sensor == AbstractGroup.MEMBER_ADDED && addingMemberDoLatching.get()) {
                        addingMemberReachedLatch.countDown();
                        try {
                            addingMemberContinueLatch.await();
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        }
                    }
                    super.emit(sensor, val);
                }
            };
            @Override
            public BasicSensorSupport sensors() {
                return interceptedSensors;
            }
            @Override
            public boolean removeMember(final Entity member) {
                removingMemberReachedLatch.countDown();
                return super.removeMember(member);
            }
        };
        group2.config().set(DynamicGroup.MEMBER_DELEGATE_CHILDREN, true);
        app.addChild(group2);
        Entities.manage(group2);
        
        app.subscriptions().subscribe(group2, AbstractGroup.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                membersAdded.add(event.getValue());
            }});
        
        final TestEntity e2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        final TestEntity e3 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        group2.addMember(e2);
        assertContainsEventually(membersAdded, e2);
        addingMemberDoLatching.set(true);
        
        Thread t1 = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    addingMemberReachedLatch.await();
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
                Entities.unmanage(e2);
            }});
        
        Thread t2 = new Thread(new Runnable() {
            @Override public void run() {
                group2.addMember(e3);
            }});

        t1.start();
        t2.start();
        
        try {
            removingMemberReachedLatch.await();
            addingMemberContinueLatch.countDown();
            t1.join(TIMEOUT_MS);
            t2.join(TIMEOUT_MS);
            assertFalse(t1.isAlive());
            assertFalse(t2.isAlive());
        } finally {
            t1.interrupt();
            t2.interrupt();
        }
    }

    private <T> void assertContainsEventually(final Collection<? extends T> vals, final T val) {
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(vals.contains(val));
            }});
    }
}
