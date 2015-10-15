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
package org.apache.brooklyn.core.entity;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.EntityAutomanagedTest.RecordingCollectionChangeListener.ChangeEvent;
import org.apache.brooklyn.core.entity.EntityAutomanagedTest.RecordingCollectionChangeListener.ChangeType;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class EntityAutomanagedTest extends BrooklynAppUnitTestSupport {

    // TODO Want to use a RecordingCollectionChangeListener to ensure on auto-manage
    // we are notified of the entity being added etc, but compilation fails on command line,
    // running `mvn clean install` (using 1.7.0_65, build 24.65-b04)!
    //  - when written "properly", it complains about the @Override annotation on onItemAdded(Entity item)
    //  - when that is removed, it complains about the call to mgmt.addEntitySetListener not being compatible
    //  - when try stripping out generics, it complains that "cannot find symbol" for the declaration of this class
    //
    // In your IDE (at least in Eclipse), you can uncomment the bits beside the TODO and run it 
    // with those insertions!
    
    protected RecordingCollectionChangeListener listener;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        listener = new RecordingCollectionChangeListener();
        
        // TODO Compiler problems - see comment at top of class
        //mgmt.addEntitySetListener((CollectionChangeListener)listener);
    }

    
    //////////////////////////////////////
    // Variants of addChild(EntitySpec) //
    //////////////////////////////////////
    
    @Test
    public void testAddedChildSpec() throws Exception {
        TestEntity e = app.addChild(EntitySpec.create(TestEntity.class));
        assertTrue(Entities.isManaged(e));
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, e)));
    }

    @Test
    public void testAddedChildHierarchySpec() throws Exception {
        TestEntity e = app.addChild(EntitySpec.create(TestEntity.class)
                .child(EntitySpec.create(TestEntity.class)
                        .child(EntitySpec.create(TestEntity.class))));
        TestEntity e2 = (TestEntity) Iterables.getOnlyElement(e.getChildren());
        TestEntity e3 = (TestEntity) Iterables.getOnlyElement(e2.getChildren());
        
        assertTrue(Entities.isManaged(e));
        assertTrue(Entities.isManaged(e2));
        assertTrue(Entities.isManaged(e3));
        assertEquals(e.getParent(), app);
        assertEquals(e2.getParent(), e);
        assertEquals(e3.getParent(), e2);
        listener.assertEventsEqualsEventually(ImmutableList.of(
                new ChangeEvent(ChangeType.ADDED, e),
                new ChangeEvent(ChangeType.ADDED, e2),
                new ChangeEvent(ChangeType.ADDED, e3)));
    }

    @Test
    public void testNewEntityWithParent() throws Exception {
        TestEntity e = app.addChild(EntitySpec.create(TestEntity.class)
                .parent(app));
        assertTrue(Entities.isManaged(e));
        assertEquals(e.getParent(), app);
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, e)));
    }

    @Test
    public void testNewEntityHierarchyWithParent() throws Exception {
        TestEntity e = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                .child(EntitySpec.create(TestEntity.class)
                        .child(EntitySpec.create(TestEntity.class)))
                .parent(app));
        TestEntity e2 = (TestEntity) Iterables.getOnlyElement(e.getChildren());
        TestEntity e3 = (TestEntity) Iterables.getOnlyElement(e2.getChildren());
        
        assertTrue(Entities.isManaged(e));
        assertTrue(Entities.isManaged(e2));
        assertTrue(Entities.isManaged(e3));
        assertEquals(e.getParent(), app);
        assertEquals(e2.getParent(), e);
        assertEquals(e3.getParent(), e2);
        listener.assertEventsEqualsEventually(ImmutableList.of(
                new ChangeEvent(ChangeType.ADDED, e),
                new ChangeEvent(ChangeType.ADDED, e2),
                new ChangeEvent(ChangeType.ADDED, e3)));
    }
    
    @Test
    public void testAddingSameChildAgainIsNoop() throws Exception {
        TestEntity e = app.addChild(EntitySpec.create(TestEntity.class)
                .parent(app));
        
        app.addChild(e);
        assertTrue(Entities.isManaged(e));
        assertEquals(e.getParent(), app);
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, e)));
    }
    
    
    //////////////////////////////////////
    // Variants of createEntity for app //
    //////////////////////////////////////

    @Test
    public void testNewApp() throws Exception {
        TestApplication app2 = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        assertTrue(Entities.isManaged(app2));
        
        assertTrue(mgmt.getApplications().contains(app2), "app="+app2+"; apps="+mgmt.getApplications());
        app.addChild(app2);
        assertTrue(Entities.isManaged(app2));
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, app2)));
    }
    
    
    ////////////////////////////////////////////////////////////////
    // Variants of Entities.startManagement and Entities.manage() //
    ////////////////////////////////////////////////////////////////

    @Test
    public void testManageIsNoop() throws Exception {
        TestEntity child = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                .parent(app));
        
        Entities.manage(child);
        assertTrue(Entities.isManaged(child));
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, child)));
    }
    
    @Test
    public void testStartManagementIsNoop() throws Exception {
        TestApplication app2 = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        assertTrue(Entities.isManaged(app2));
        
        Entities.startManagement(app2, mgmt);
        assertTrue(Entities.isManaged(app2));
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, app2)));
    }
    
    @Test
    public void testStartManagementOfEntityIsNoop() throws Exception {
        Entity app2 = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        assertTrue(Entities.isManaged(app2));
        
        Entities.startManagement(app2);
        assertTrue(Entities.isManaged(app2));
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, app2)));
    }
    
    @Test
    public void testStartManagementFailsIfAppDeleted() throws Exception {
        TestApplication app2 = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        Entities.unmanage(app2);
        
        try {
            Entities.startManagement(app2, mgmt);
            fail("Managed deleted app "+app2+" in "+mgmt);
        } catch (IllegalStateException e) {
            if (!(e.toString().contains("No concrete entity known"))) throw e;
        }
    }

    @Test
    public void testManageFailsIfEntityDeleted() throws Exception {
        TestEntity child = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                .parent(app));
        Entities.unmanage(child);
        
        try {
            Entities.manage(child);
            fail("Managed deleted entity "+child+" in "+mgmt);
        } catch (IllegalStateException e) {
            if (!(e.toString().contains("No concrete entity known"))) throw e;
        }
    }
    

    ///////////////////////////////////////////
    // Variants of createEntity for non-apps //
    ///////////////////////////////////////////

    // TODO Controversial? Should it be based on reachability from parent? Can entities be (temporarily) top-level?
    // But management model is simpler if it becomes managed immediately.
    @Test
    public void testNewOrphanedEntityIsManaged() throws Exception {
        TestEntity e = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class));
        assertTrue(Entities.isManaged(e));
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, e)));
        
        // Check that orphaned entity doesn't interfere with getApplications
        Asserts.assertEqualsIgnoringOrder(mgmt.getApplications(), ImmutableList.of(app)); 
    }

    @Test
    public void testOrphanedEntityHierarchyIsManaged() throws Exception {
        TestEntity e = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                .child(EntitySpec.create(TestEntity.class)
                        .child(EntitySpec.create(TestEntity.class))));
        TestEntity e2 = (TestEntity) Iterables.getOnlyElement(e.getChildren());
        TestEntity e3 = (TestEntity) Iterables.getOnlyElement(e2.getChildren());
        
        assertTrue(Entities.isManaged(e));
        assertTrue(Entities.isManaged(e2));
        assertTrue(Entities.isManaged(e3));
        assertEquals(e.getParent(), null);
        assertEquals(e2.getParent(), e);
        assertEquals(e3.getParent(), e2);
        listener.assertEventsEqualsEventually(ImmutableList.of(
                new ChangeEvent(ChangeType.ADDED, e),
                new ChangeEvent(ChangeType.ADDED, e2),
                new ChangeEvent(ChangeType.ADDED, e3)));
    }
    
    @Test
    public void testNewOrphanedEntityCanBeAddedToChild() throws Exception {
        TestEntity e = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class));
        
        app.addChild(e);
        assertTrue(Entities.isManaged(e));
        listener.assertEventsEqualsEventually(ImmutableList.of(new ChangeEvent(ChangeType.ADDED, e)));
    }

    // TODO Compiler problems - see comment at top of class
    public static class RecordingCollectionChangeListener { // FIXME implements CollectionChangeListener<Entity> {
        public enum ChangeType {
            ADDED, REMOVED;
        }
        public static class ChangeEvent {
            public final ChangeType type;
            public final Entity entity;
            
            ChangeEvent(ChangeType type, Entity entity) {
                this.type = checkNotNull(type, "type");
                this.entity = checkNotNull(entity, "entity");
            }
            
            @Override
            public int hashCode() {
                return Objects.hashCode(type, entity);
            }
            
            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof ChangeEvent)) return false;
                ChangeEvent o = (ChangeEvent) obj;
                return type.equals(o.type) && entity.equals(o.entity);
            }
            
            @Override
            public String toString() {
                return Objects.toStringHelper(this).add("type", type).add("entity", entity).toString();
            }
        }
        
        private final List<ChangeEvent> events = Lists.newCopyOnWriteArrayList();
        private final Set<Entity> items = Sets.newConcurrentHashSet();

        public void assertEventsEqualsEventually(final Iterable<? extends ChangeEvent> expected) {
            // TODO Compiler problems - see comment at top of class
//            Asserts.succeedsEventually(new Runnable() {
//                public void run() {
//                    assertEquals(events, ImmutableList.copyOf(expected));
//                }});
        }

        public void assertItemsEqualsEventually(final Iterable<? extends Entity> expected) {
            // TODO Compiler problems - see comment at top of class
//            Asserts.succeedsEventually(new Runnable() {
//                public void run() {
//                    assertEquals(items, ImmutableSet.copyOf(expected));
//                }});
        }

        // TODO Want to include @Override; compiler problems - see comment at top of class
        public void onItemAdded(Entity item) {
            items.add(item);
            events.add(new ChangeEvent(ChangeType.ADDED, item));
        }

        // TODO Want to include @Override; compiler problems - see comment at top of class
        public void onItemRemoved(Entity item) {
            items.remove(item);
            events.add(new ChangeEvent(ChangeType.REMOVED, item));
        }
    }
}
