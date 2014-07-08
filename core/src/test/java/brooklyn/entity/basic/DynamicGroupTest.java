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
package brooklyn.entity.basic;

import static brooklyn.test.Asserts.assertEqualsIgnoringOrder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.Sensors;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

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
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
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
        Predicate predicate = Predicates.equalTo(e1);
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
        final Entity e3 = new AbstractEntity() {};
        group.setEntityFilter(EntityPredicates.idEqualTo(e3.getId()));
        e3.setParent(app);
        
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of());
        
        Entities.manage(e3);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e3));
            }});
    }

    @Test
    public void testGroupUsesNewFilter() throws Exception {
        Entity e3 = new AbstractEntity(app) {};
        Entities.manage(e3);
        group.setEntityFilter(EntityPredicates.idEqualTo(e3.getId()));
        
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e3));
    }

    @Test
    public void testGroupDetectsChangedEntities() throws Exception {
        final AttributeSensor<String> MY_ATTRIBUTE = Sensors.newStringSensor("test.myAttribute", "My test attribute");
    
        group.setEntityFilter(EntityPredicates.attributeEqualTo(MY_ATTRIBUTE, "yes"));
        group.addSubscription(null, MY_ATTRIBUTE);
        
        assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of());
        
        // When changed (such that subscription spots it), then entity added
        e1.setAttribute(MY_ATTRIBUTE, "yes");
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEqualsIgnoringOrder(group.getMembers(), ImmutableSet.of(e1));
            }});

        // When it stops matching, entity is removed        
        e1.setAttribute(MY_ATTRIBUTE, "no");
        
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
        e1.setAttribute(MY_ATTRIBUTE, "yes");
        e2.setAttribute(MY_ATTRIBUTE, "yes");
        
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
        
        app.subscribe(group, DynamicGroup.MEMBER_ADDED, new SensorEventListener<Entity>() {
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

        app.subscribe(group, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
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
            final TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).id("entity-" + i));
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
        ((EntityLocal)group2).setConfig(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class));
        app.addChild(group2);
        group2.init();
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
    @Test
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
        ((EntityLocal)group2).setConfig(DynamicGroup.ENTITY_FILTER, Predicates.<Object>equalTo(e3));
        app.addChild(group2);
        group2.init();
        
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
}
