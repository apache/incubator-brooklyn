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
package org.apache.brooklyn.core.mgmt.rebind;

import static org.apache.brooklyn.test.EntityTestUtils.assertAttributeEquals;
import static org.apache.brooklyn.test.EntityTestUtils.assertConfigEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.api.mgmt.rebind.RebindContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoManifest;
import org.apache.brooklyn.api.mgmt.rebind.mementos.EntityMemento;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.AttributeSensor.SensorPersistenceMode;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.LocationConfigTest.MyLocation;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.sensor.BasicSensorEvent;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.entity.group.AbstractGroupImpl;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.RuntimeInterruptedException;
import org.apache.brooklyn.util.time.Durations;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class RebindEntityTest extends RebindTestFixtureWithApp {

    // FIXME Add test about dependent configuration serialization?!
    
    // TODO Convert, so not calling entity constructors
    
    @Test
    public void testRestoresSimpleApp() throws Exception {
        newApp = rebind();
        assertNotSame(newApp, origApp);
        assertEquals(newApp.getId(), origApp.getId());
    }
    
    @Test
    public void testRestoresEntityHierarchy() throws Exception {
        TestEntity origE = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity origE2 = origE.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        newApp = rebind();

        // Assert has expected config/fields
        assertEquals(newApp.getId(), origApp.getId());
        
        assertEquals(newApp.getChildren().size(), 1);
        TestEntity newE = (TestEntity) Iterables.get(newApp.getChildren(), 0);
        assertEquals(newE.getId(), origE.getId());

        assertEquals(newE.getChildren().size(), 1);
        TestEntity newE2 = (TestEntity) Iterables.get(newE.getChildren(), 0);
        assertEquals(newE2.getId(), origE2.getId());
        
        assertNotSame(origApp, newApp);
        assertNotSame(origApp.getManagementContext(), newApp.getManagementContext());
        assertNotSame(origE, newE);
        assertNotSame(origE2, newE2);
    }
    
    @Test
    public void testRestoresGroupMembers() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        MyEntity origE2 = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        BasicGroup origG = origApp.createAndManageChild(EntitySpec.create(BasicGroup.class));
        origG.addMember(origE);
        origG.addMember(origE2);
        
        newApp = rebind();
        
        BasicGroup newG = (BasicGroup) Iterables.find(newApp.getChildren(), Predicates.instanceOf(BasicGroup.class));
        Iterable<Entity> newEs = Iterables.filter(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(ImmutableSet.copyOf(newG.getMembers()), ImmutableSet.copyOf(newEs));
    }
    
    @Test
    public void testRestoresEntityConfig() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(MyEntity.class).configure("myconfig", "myval"));
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testRestoresEntityDependentConfigCompleted() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .configure("myconfig", DependentConfiguration.attributeWhenReady(origApp, TestApplication.MY_ATTRIBUTE)));
        origApp.sensors().set(TestApplication.MY_ATTRIBUTE, "myval");
        origE.getConfig(MyEntity.MY_CONFIG); // wait for it to be done
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test(enabled=false) // not yet supported
    public void testRestoresEntityDependentConfigUncompleted() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .configure("myconfig", DependentConfiguration.attributeWhenReady(origApp, TestApplication.MY_ATTRIBUTE)));
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        newApp.sensors().set(TestApplication.MY_ATTRIBUTE, "myval");
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testRestoresEntitySensors() throws Exception {
        AttributeSensor<String> myCustomAttribute = Sensors.newStringSensor("my.custom.attribute");
        
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        origE.sensors().set(myCustomAttribute, "myval");
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getAttribute(myCustomAttribute), "myval");
    }

    @Test
    public void testRestoresEntityLocationAndCleansUp() throws Exception {
        MyLocation loc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(MyLocation.class));
        origApp.createAndManageChild(EntitySpec.create(MyEntity.class).location(loc));
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        Assert.assertEquals(newE.getLocations().size(), 1); 
        Location loc2 = Iterables.getOnlyElement(newE.getLocations());
        Assert.assertEquals(loc, loc2);
        Assert.assertFalse(loc==loc2);
        
        newApp.stop();
        // TODO how to trigger automatic unmanagement? see notes in RebindLocalhostLocationTest
        newManagementContext.getLocationManager().unmanage(loc2);
        switchOriginalToNewManagementContext();
        RebindTestUtils.waitForPersisted(origManagementContext);
        
        BrooklynMementoManifest mf = loadMementoManifest();
        Assert.assertTrue(mf.getLocationIdToType().isEmpty(), "Expected no locations; had "+mf.getLocationIdToType());
    }

    @Test
    public void testRestoresEntityIdAndDisplayName() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class)
                .displayName("mydisplayname")
                .configure("iconUrl", "file:///tmp/myicon.png"));
        String eId = origE.getId();
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getId(), eId);
        assertEquals(newE.getDisplayName(), "mydisplayname");
    }
    
    // Saw this fail during development (fixed now); but want at least one of these tests to be run 
    // many times for stress testing purposes
    @Test(invocationCount=100, groups="Integration")
    public void testRestoresEntityIdAndDisplayNameManyTimes() throws Exception {
        testRestoresEntityIdAndDisplayName();
    }
    
    @Test
    public void testCanCustomizeRebind() throws Exception {
        MyEntity2 origE = origApp.createAndManageChild(EntitySpec.create(MyEntity2.class).configure("myfield", "myval"));
        
        newApp = rebind();
        
        MyEntity2 newE = (MyEntity2) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity2.class));
        assertEquals(newE.getMyfield(), "myval");
        Assert.assertEquals(newE, origE);
    }
    
    @Test
    public void testRebindsSubscriptions() throws Exception {
        MyEntity2 origE = origApp.createAndManageChild(EntitySpec.create(MyEntity2.class).configure("subscribe", true));
        
        newApp = rebind();
        MyEntity2 newE = (MyEntity2) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity2.class));
        
        newApp.sensors().set(TestApplication.MY_ATTRIBUTE, "mysensorval");
        Asserts.eventually(Suppliers.ofInstance(newE.getEvents()), Predicates.<List<String>>equalTo(ImmutableList.of("mysensorval")));
        Assert.assertEquals(newE, origE);
    }
    
    @Test
    public void testHandlesReferencingOtherEntities() throws Exception {
        MyEntity origOtherE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        MyEntityReffingOthers origE = origApp.createAndManageChild(EntitySpec.create(MyEntityReffingOthers.class)
                .configure("entityRef", origOtherE));
        origE.sensors().set(MyEntityReffingOthers.ENTITY_REF_SENSOR, origOtherE);
        
        newApp = rebind();
        MyEntityReffingOthers newE = (MyEntityReffingOthers) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntityReffingOthers.class));
        MyEntity newOtherE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertAttributeEquals(newE, MyEntityReffingOthers.ENTITY_REF_SENSOR, newOtherE);
        assertConfigEquals(newE, MyEntityReffingOthers.ENTITY_REF_CONFIG, newOtherE);
    }
    
    @Test
    public void testHandlesReferencingOtherEntitiesInPojoField() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        ReffingEntity reffer = new ReffingEntity();
        reffer.obj = origE;
        reffer.entity = origE;
        reffer.myEntity = origE;
        origApp.config().set(TestEntity.CONF_OBJECT, reffer);

        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        ReffingEntity reffer2 = (ReffingEntity)newApp.getConfig(TestEntity.CONF_OBJECT);
        
        assertEquals(reffer2.myEntity, newE);
        assertEquals(reffer2.entity, newE);
        assertEquals(reffer2.obj, newE);
    }
    
    // Where the same object is referenced from two different fields, using types that do not share a 
    // super type... then the object will just be deserialized once - at that point it must have *both*
    // interfaces.
    @Test(groups="WIP")
    public void testHandlesReferencingOtherEntityInPojoFieldsOfOtherTypes() throws Exception {
        MyEntityWithMultipleInterfaces origE = origApp.createAndManageChild(EntitySpec.create(MyEntityWithMultipleInterfaces.class));
        ReffingEntity reffer = new ReffingEntity();
        reffer.group = origE;
        reffer.resizable = origE;
        origApp.config().set(TestEntity.CONF_OBJECT, reffer);

        newApp = rebind();
        MyEntityWithMultipleInterfaces newE = (MyEntityWithMultipleInterfaces) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntityWithMultipleInterfaces.class));
        ReffingEntity newReffer = (ReffingEntity)newApp.getConfig(TestEntity.CONF_OBJECT);
        
        assertEquals(newReffer.group, newE);
        assertEquals(newReffer.resizable, newE);
    }
    
    @Test
    public void testEntityTags() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        origE.tags().addTag("foo");
        origE.tags().addTag(origApp);

        newApp = rebind();
        MyEntity newE = Iterables.getOnlyElement( Entities.descendants(newApp, MyEntity.class) );

        assertTrue(newE.tags().containsTag("foo"), "tags are "+newE.tags().getTags());
        assertFalse(newE.tags().containsTag("bar"));
        assertTrue(newE.tags().containsTag(newE.getParent()));
        assertTrue(newE.tags().containsTag(origApp));
        assertEquals(newE.tags().getTags(), MutableSet.of("foo", newE.getParent()));
    }

    public static class ReffingEntity {
        public Group group;
        public Resizable resizable;
        public MyEntity myEntity;
        public Entity entity;
        public Object obj;
        @Override
        public boolean equals(Object o) {
            return (o instanceof ReffingEntity) && Objects.equal(entity, ((ReffingEntity)o).entity) 
                    && Objects.equal(obj, ((ReffingEntity)o).obj) && Objects.equal(group, ((ReffingEntity)o).group)
                    && Objects.equal(resizable, ((ReffingEntity)o).resizable);
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(entity, obj);
        }
    }

    @Test
    public void testHandlesReferencingOtherLocations() throws Exception {
        MyLocation origLoc = new MyLocation();
        MyEntityReffingOthers origE = origApp.createAndManageChild(EntitySpec.create(MyEntityReffingOthers.class)
                .configure("locationRef", origLoc));
        origE.sensors().set(MyEntityReffingOthers.LOCATION_REF_SENSOR, origLoc);
        origApp.start(ImmutableList.of(origLoc));
        
        newApp = rebind();
        MyEntityReffingOthers newE = (MyEntityReffingOthers) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntityReffingOthers.class));
        MyLocation newLoc = (MyLocation) Iterables.getOnlyElement(newApp.getLocations());
        
        assertAttributeEquals(newE, MyEntityReffingOthers.LOCATION_REF_SENSOR, newLoc);
        assertConfigEquals(newE, MyEntityReffingOthers.LOCATION_REF_CONFIG, newLoc);
    }

    @Test
    public void testEntityManagementLifecycleAndVisibilityDuringRebind() throws Exception {
        MyLatchingEntityImpl.latching = false;
        MyLatchingEntity origE = origApp.createAndManageChild(EntitySpec.create(MyLatchingEntity.class));
        MyLatchingEntityImpl.reset(); // after origE has been managed
        MyLatchingEntityImpl.latching = true;
        
        // Serialize and rebind, but don't yet manage the app
        RebindTestUtils.waitForPersisted(origApp);
        RebindTestUtils.checkCurrentMementoSerializable(origApp);
        newManagementContext = RebindTestUtils.newPersistingManagementContextUnstarted(mementoDir, classLoader);
        Thread thread = new Thread() {
            public void run() {
                try {
                    newManagementContext.getRebindManager().rebind(classLoader, null, ManagementNodeState.MASTER);
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        try {
            thread.start();
            
            assertTrue(Durations.await(MyLatchingEntityImpl.reconstructStartedLatch, TIMEOUT_MS));
            assertNull(newManagementContext.getEntityManager().getEntity(origApp.getId()));
            assertNull(newManagementContext.getEntityManager().getEntity(origE.getId()));
            assertTrue(MyLatchingEntityImpl.managingStartedLatch.getCount() > 0);
            
            MyLatchingEntityImpl.reconstructContinuesLatch.countDown();
            assertTrue(Durations.await(MyLatchingEntityImpl.managingStartedLatch, TIMEOUT_MS));
            assertNotNull(newManagementContext.getEntityManager().getEntity(origApp.getId()));
            assertNull(newManagementContext.getEntityManager().getEntity(origE.getId()));
            assertTrue(MyLatchingEntityImpl.managedStartedLatch.getCount() > 0);
            
            MyLatchingEntityImpl.managingContinuesLatch.countDown();
            assertTrue(Durations.await(MyLatchingEntityImpl.managedStartedLatch, TIMEOUT_MS));
            assertNotNull(newManagementContext.getEntityManager().getEntity(origApp.getId()));
            assertNotNull(newManagementContext.getEntityManager().getEntity(origE.getId()));
            MyLatchingEntityImpl.managedContinuesLatch.countDown();

            Durations.join(thread, TIMEOUT_MS);
            assertFalse(thread.isAlive());
            
        } finally {
            thread.interrupt();
            MyLatchingEntityImpl.reset();
        }
    }
    
    @Test(groups="Integration") // takes more than 4 seconds, due to assertContinually calls
    public void testSubscriptionAndPublishingOnlyActiveWhenEntityIsManaged() throws Exception {
        MyLatchingEntityImpl.latching = false;
        origApp.createAndManageChild(EntitySpec.create(MyLatchingEntity.class)
                .configure("subscribe", TestApplication.MY_ATTRIBUTE)
                .configure("publish", "myvaltopublish"));
        MyLatchingEntityImpl.reset(); // after origE has been managed
        MyLatchingEntityImpl.latching = true;

        // Serialize and rebind, but don't yet manage the app
        RebindTestUtils.waitForPersisted(origApp);
        RebindTestUtils.checkCurrentMementoSerializable(origApp);
        newManagementContext = new LocalManagementContext();
        Thread thread = new Thread() {
            public void run() {
                try {
                    RebindTestUtils.rebind(RebindOptions.create()
                            .newManagementContext(newManagementContext)
                            .mementoDir(mementoDir)
                            .classLoader(RebindEntityTest.class.getClassLoader()));
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        };
        try {
            thread.start();
            final List<Object> events = new CopyOnWriteArrayList<Object>();
            
            newManagementContext.getSubscriptionManager().subscribe(null, MyLatchingEntityImpl.MY_SENSOR, new SensorEventListener<Object>() {
                @Override public void onEvent(SensorEvent<Object> event) {
                    events.add(event.getValue());
                }});

            // In entity's reconstruct, publishes events are queued, and subscriptions don't yet take effect
            assertTrue(Durations.await(MyLatchingEntityImpl.reconstructStartedLatch, TIMEOUT_MS));
            newManagementContext.getSubscriptionManager().publish(new BasicSensorEvent<String>(TestApplication.MY_ATTRIBUTE, null, "myvaltooearly"));
            
            Asserts.continually(Suppliers.ofInstance(MyLatchingEntityImpl.events), Predicates.equalTo(Collections.emptyList()));
            Asserts.continually(Suppliers.ofInstance(events), Predicates.equalTo(Collections.emptyList()));
            
            // When the entity is notified of "managing", then subscriptions take effect (but missed events not delivered); 
            // published events remain queued
            MyLatchingEntityImpl.reconstructContinuesLatch.countDown();
            assertTrue(MyLatchingEntityImpl.managingStartedLatch.getCount() > 0);

            Asserts.continually(Suppliers.ofInstance(events), Predicates.equalTo(Collections.emptyList()));
            Asserts.continually(Suppliers.ofInstance(MyLatchingEntityImpl.events), Predicates.equalTo(Collections.emptyList()));

            newManagementContext.getSubscriptionManager().publish(new BasicSensorEvent<String>(TestApplication.MY_ATTRIBUTE, null, "myvaltoreceive"));
            Asserts.eventually(Suppliers.ofInstance(MyLatchingEntityImpl.events), Predicates.<List<Object>>equalTo(ImmutableList.of((Object)"myvaltoreceive")));

            // When the entity is notified of "managed", its events are only then delivered
            MyLatchingEntityImpl.managingContinuesLatch.countDown();
            assertTrue(Durations.await(MyLatchingEntityImpl.managedStartedLatch, TIMEOUT_MS));

            Asserts.eventually(Suppliers.ofInstance(MyLatchingEntityImpl.events), Predicates.<List<Object>>equalTo(ImmutableList.of((Object)"myvaltoreceive")));
            
            MyLatchingEntityImpl.managedContinuesLatch.countDown();
            
            Durations.join(thread, TIMEOUT_MS);
            assertFalse(thread.isAlive());
            
        } finally {
            thread.interrupt();
            MyLatchingEntityImpl.reset();
        }

    }
    
    @Test
    public void testRestoresConfigKeys() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "nameval")
                .configure(TestEntity.CONF_LIST_PLAIN, ImmutableList.of("val1", "val2"))
                .configure(TestEntity.CONF_MAP_PLAIN, ImmutableMap.of("akey", "aval")));
        
        newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getConfig(TestEntity.CONF_NAME), "nameval");
        assertEquals(newE.getConfig(TestEntity.CONF_LIST_PLAIN), ImmutableList.of("val1", "val2"));
        assertEquals(newE.getConfig(TestEntity.CONF_MAP_PLAIN), ImmutableMap.of("akey", "aval"));
    }

    @Test // ListConfigKey deprecated, as order no longer guaranteed
    public void testRestoresListConfigKey() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_LIST_THING.subKey(), "val1")
                .configure(TestEntity.CONF_LIST_THING.subKey(), "val2"));
        
        newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        //assertEquals(newE.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("val1", "val2"));
        assertEquals(ImmutableSet.copyOf(newE.getConfig(TestEntity.CONF_LIST_THING)), ImmutableSet.of("val1", "val2"));
    }

    @Test
    public void testRestoresSetConfigKey() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_SET_THING.subKey(), "val1")
                .configure(TestEntity.CONF_SET_THING.subKey(), "val2"));
        
        newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getConfig(TestEntity.CONF_SET_THING), ImmutableSet.of("val1", "val2"));
    }

    @Test
    public void testRestoresMapConfigKey() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_MAP_THING.subKey("akey"), "aval")
                .configure(TestEntity.CONF_MAP_THING.subKey("bkey"), "bval"));
        
        newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getConfig(TestEntity.CONF_MAP_THING), ImmutableMap.of("akey", "aval", "bkey", "bval"));
    }

    @Test
    public void testRebindPreservesInheritedConfig() throws Exception {
        origApp.config().set(MyEntity.MY_CONFIG, "myValInSuper");
        origApp.createAndManageChild(EntitySpec.create(MyEntity.class));

        // rebind: inherited config is preserved
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myValInSuper");
        assertEquals(newApp.getConfig(MyEntity.MY_CONFIG), "myValInSuper");
        
        // This config should be inherited by dynamically-added children of app
        MyEntity newE2 = newApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        
        assertEquals(newE2.getConfig(MyEntity.MY_CONFIG), "myValInSuper");
        
    }

    @Test
    public void testRebindPreservesGetConfigWithDefault() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));

        assertNull(origE.getConfig(MyEntity.MY_CONFIG));
        assertEquals(origE.getConfigRaw(MyEntity.MY_CONFIG, true).or("mydefault"), "mydefault");
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertNull(newE.getConfig(MyEntity.MY_CONFIG));
        assertEquals(newE.getConfigRaw(MyEntity.MY_CONFIG, true).or("mydefault"), "mydefault");
    }

    @Test
    public void testRestoresUnmatchedConfig() throws Exception {
        TestEntity origE = origApp.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure("myunmatchedkey", "myunmatchedval"));
        
        origE.createAndManageChild(EntitySpec.create(TestEntity.class));

        newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        final TestEntity newChildE = (TestEntity) Iterables.find(newE.getChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.config().getBag().getStringKey("myunmatchedkey"), "myunmatchedval");
        assertEquals(newE.config().getLocalBag().getStringKey("myunmatchedkey"), "myunmatchedval");
        
        assertEquals(newChildE.config().getBag().getStringKey("myunmatchedkey"), "myunmatchedval");
        assertFalse(newChildE.config().getLocalBag().containsKey("myunmatchedkey"));
    }

    @Test
    public void testRebindPersistsNullAttribute() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        origE.sensors().set(MyEntity.MY_SENSOR, null);

        assertNull(origE.getAttribute(MyEntity.MY_SENSOR));

        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertNull(newE.getAttribute(MyEntity.MY_SENSOR));
    }

    @Test
    public void testRebindPersistsDynamicAttribute() throws Exception {
        final String sensorName = "test.mydynamicsensor";
        final String sensorDescription = "My description";
        final AttributeSensor<String> MY_DYNAMIC_SENSOR = new BasicAttributeSensor<String>(
                String.class, sensorName, sensorDescription);

        origApp.sensors().set(MY_DYNAMIC_SENSOR, "myval");
        assertEquals(origApp.getEntityType().getSensor(sensorName).getDescription(), sensorDescription);

        newApp = rebind();
        
        assertEquals(newApp.getAttribute(MY_DYNAMIC_SENSOR), "myval");
        assertEquals(newApp.getEntityType().getSensor(sensorName).getDescription(), sensorDescription);
    }

    @Test
    public void testRebindDoesNotPersistTransientAttribute() throws Exception {
        final String sensorName = "test.mydynamicsensor";
        final AttributeSensor<Object> MY_DYNAMIC_SENSOR = Sensors.builder(Object.class, sensorName)
                .persistence(SensorPersistenceMode.NONE)
                .build();
        
        // Anonymous inner class: we will not be able to rebind that.
        @SuppressWarnings("serial")
        Semaphore unrebindableObject = new Semaphore(1) {
        };
        
        origApp.sensors().set(MY_DYNAMIC_SENSOR, unrebindableObject);
        assertEquals(origApp.getAttribute(MY_DYNAMIC_SENSOR), unrebindableObject);

        newApp = rebind();
        assertNull(newApp.getAttribute(MY_DYNAMIC_SENSOR));
    }

    @Test
    public void testRebindWhenPreviousAppDestroyedHasNoApp() throws Exception {
        origApp.stop();

        RebindTestUtils.waitForPersisted(origManagementContext);
        newManagementContext = RebindTestUtils.newPersistingManagementContextUnstarted(mementoDir, classLoader);
        List<Application> newApps = newManagementContext.getRebindManager().rebind(classLoader, null, ManagementNodeState.MASTER);
        newManagementContext.getRebindManager().startPersistence();
        
        assertEquals(newApps.size(), 0, "apps="+newApps);
        assertEquals(newManagementContext.getApplications().size(), 0, "apps="+newManagementContext.getApplications());
    }

    @Test(invocationCount=100, groups="Integration")
    public void testRebindWhenPreviousAppDestroyedHasNoAppRepeatedly() throws Exception {
        testRebindWhenPreviousAppDestroyedHasNoApp();
    }

    /**
     * @deprecated since 0.7; support for rebinding old-style entities is deprecated
     */
    @Test
    public void testHandlesOldStyleEntity() throws Exception {
        MyOldStyleEntity origE = new MyOldStyleEntity(MutableMap.of("confName", "myval"), origApp);
        Entities.manage(origE);
        
        newApp = rebind();

        MyOldStyleEntity newE = (MyOldStyleEntity) Iterables.find(newApp.getChildren(), EntityPredicates.idEqualTo(origE.getId()));
        
        assertEquals(newE.getConfig(MyOldStyleEntity.CONF_NAME), "myval");
    }

    @Test
    public void testIsRebinding() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(EntityChecksIsRebinding.class));

        newApp = rebind();
        final EntityChecksIsRebinding newE = (EntityChecksIsRebinding) Iterables.find(newApp.getChildren(), Predicates.instanceOf(EntityChecksIsRebinding.class));

        assertTrue(newE.isRebindingValWhenRebinding());
        assertFalse(newE.isRebinding());
    }
    
    @ImplementedBy(EntityChecksIsRebindingImpl.class)
    public static interface EntityChecksIsRebinding extends TestEntity {
        boolean isRebindingValWhenRebinding();
        boolean isRebinding();
    }
    
    public static class EntityChecksIsRebindingImpl extends TestEntityImpl implements EntityChecksIsRebinding {
        boolean isRebindingValWhenRebinding;
        
        @Override public boolean isRebindingValWhenRebinding() {
            return isRebindingValWhenRebinding;
        }
        @Override public boolean isRebinding() {
            return super.isRebinding();
        }
        @Override public void rebind() {
            super.rebind();
            isRebindingValWhenRebinding = isRebinding();
        }
    }
    
    public static class MyOldStyleEntity extends AbstractEntity {
        @SetFromFlag("confName")
        public static final ConfigKey<String> CONF_NAME = TestEntity.CONF_NAME;

        @SuppressWarnings("deprecation")
        public MyOldStyleEntity(Map<?,?> flags, Entity parent) {
            super(flags, parent);
        }
    }
    
    // TODO Don't want to extend EntityLocal, but tests want to call app.setAttribute
    @ImplementedBy(MyEntityImpl.class)
    public interface MyEntity extends Entity, Startable, EntityLocal {
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myentity.myconfig", "My test config");

        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                String.class, "test.myentity.mysensor", "My test sensor");
    }
    
    public static class MyEntityImpl extends AbstractEntity implements MyEntity {
        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable

        public MyEntityImpl() {
        }

        @Override
        public void start(Collection<? extends Location> locations) {
            addLocations(locations);
        }

        @Override
        public void stop() {
        }

        @Override
        public void restart() {
        }
    }

    @ImplementedBy(MyEntityWithMultipleInterfacesImpl.class)
    public interface MyEntityWithMultipleInterfaces extends Group, Resizable, EntityLocal {
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myentity.myconfig", "My test config");

        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                String.class, "test.myentity.mysensor", "My test sensor");
    }
    
    public static class MyEntityWithMultipleInterfacesImpl extends AbstractGroupImpl implements MyEntityWithMultipleInterfaces {
        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable

        public MyEntityWithMultipleInterfacesImpl() {
        }

        @Override
        public Integer resize(Integer desiredSize) {
            return 0;
        }
    }
    
    // TODO Don't want to extend EntityLocal, but tests want to call app.setAttribute
    @ImplementedBy(MyEntityReffingOthersImpl.class)
    public interface MyEntityReffingOthers extends Entity, EntityLocal {
        @SetFromFlag("entityRef")
        public static final ConfigKey<Entity> ENTITY_REF_CONFIG = new BasicConfigKey<Entity>(
                        Entity.class, "test.config.entityref", "Ref to other entity");

        @SetFromFlag("locationRef")
        public static final ConfigKey<Location> LOCATION_REF_CONFIG = new BasicConfigKey<Location>(
                Location.class, "test.config.locationref", "Ref to other location");
        
        public static final AttributeSensor<Entity> ENTITY_REF_SENSOR = new BasicAttributeSensor<Entity>(
                Entity.class, "test.attribute.entityref", "Ref to other entity");
        
        public static final AttributeSensor<Location> LOCATION_REF_SENSOR = new BasicAttributeSensor<Location>(
                Location.class, "test.attribute.locationref", "Ref to other location");
    }
    
    public static class MyEntityReffingOthersImpl extends AbstractEntity implements MyEntityReffingOthers {
        @SetFromFlag("entityRef")
        public static final ConfigKey<Entity> ENTITY_REF_CONFIG = new BasicConfigKey<Entity>(
                        Entity.class, "test.config.entityref", "Ref to other entity");

        @SetFromFlag("locationRef")
        public static final ConfigKey<Location> LOCATION_REF_CONFIG = new BasicConfigKey<Location>(
                Location.class, "test.config.locationref", "Ref to other location");
        
        public static final AttributeSensor<Entity> ENTITY_REF_SENSOR = new BasicAttributeSensor<Entity>(
                Entity.class, "test.attribute.entityref", "Ref to other entity");
        
        public static final AttributeSensor<Location> LOCATION_REF_SENSOR = new BasicAttributeSensor<Location>(
                Location.class, "test.attribute.locationref", "Ref to other location");
        
        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable

        public MyEntityReffingOthersImpl() {
        }
    }

    @ImplementedBy(MyEntity2Impl.class)
    public interface MyEntity2 extends Entity {
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myconfig", "My test config");

        @SetFromFlag("subscribe")
        public static final ConfigKey<Boolean> SUBSCRIBE = new BasicConfigKey<Boolean>(
                Boolean.class, "test.subscribe", "Whether to do some subscriptions on re-bind", false);
        
        public List<String> getEvents();
        
        public String getMyfield();
    }
    
    public static class MyEntity2Impl extends AbstractEntity implements MyEntity2 {
        @SetFromFlag
        String myfield;
        
        final List<String> events = new CopyOnWriteArrayList<String>();

        @SuppressWarnings("unused")
        private final Object dummy = new Object(); // so not serializable

        public MyEntity2Impl() {
        }

        public List<String> getEvents() {
            return events;
        }

        public String getMyfield() {
            return myfield;
        }
        
        @Override
        public void onManagementStarting() {
            if (getConfig(SUBSCRIBE)) {
                subscriptions().subscribe(getApplication(), TestApplication.MY_ATTRIBUTE, new SensorEventListener<String>() {
                    @Override public void onEvent(SensorEvent<String> event) {
                        events.add(event.getValue());
                    }
                });
            }
        }
        
        @Override
        public RebindSupport<EntityMemento> getRebindSupport() {
            return new BasicEntityRebindSupport(this) {
                @Override public EntityMemento getMemento() {
                    // Note: using MutableMap so accepts nulls
                    return getMementoWithProperties(MutableMap.<String,Object>of("myfield", myfield));
                }
                @Override protected void doReconstruct(RebindContext rebindContext, EntityMemento memento) {
                    super.doReconstruct(rebindContext, memento);
                    myfield = (String) memento.getCustomField("myfield");
                }
            };
        }
    }

    @ImplementedBy(MyLatchingEntityImpl.class)
    public interface MyLatchingEntity extends Entity {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @SetFromFlag("subscribe")
        public static final ConfigKey<AttributeSensor<?>> SUBSCRIBE = new BasicConfigKey(
                AttributeSensor.class, "test.mylatchingentity.subscribe", "Sensor to subscribe to (or null means don't)", null);
        
        @SetFromFlag("publish")
        public static final ConfigKey<String> PUBLISH = new BasicConfigKey<String>(
                String.class, "test.mylatchingentity.publish", "Value to publish (or null means don't)", null);

        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                String.class, "test.mylatchingentity.mysensor", "My test sensor");
    }
    
    public static class MyLatchingEntityImpl extends AbstractEntity implements MyLatchingEntity {
        static volatile CountDownLatch reconstructStartedLatch;
        static volatile CountDownLatch reconstructContinuesLatch;
        static volatile CountDownLatch managingStartedLatch;
        static volatile CountDownLatch managingContinuesLatch;
        static volatile CountDownLatch managedStartedLatch;
        static volatile CountDownLatch managedContinuesLatch;

        static volatile boolean latching = false;
        static volatile List<Object> events;

        static void reset() {
            latching = false;
            events = new CopyOnWriteArrayList<Object>();

            reconstructStartedLatch = new CountDownLatch(1);
            reconstructContinuesLatch = new CountDownLatch(1);
            managingStartedLatch = new CountDownLatch(1);
            managingContinuesLatch = new CountDownLatch(1);
            managedStartedLatch = new CountDownLatch(1);
            managedContinuesLatch = new CountDownLatch(1);
        }

        public MyLatchingEntityImpl() {
        }

        private void onReconstruct() {
            if (getConfig(SUBSCRIBE) != null) {
                getManagementSupport().getSubscriptionContext().subscribe(null, getConfig(SUBSCRIBE), new SensorEventListener<Object>() {
                        @Override public void onEvent(SensorEvent<Object> event) {
                            events.add(event.getValue());
                        }});
            }

            if (getConfig(PUBLISH) != null) {
                sensors().set(MY_SENSOR, getConfig(PUBLISH));
            }

            if (latching) {
                reconstructStartedLatch.countDown();
                try {
                    reconstructContinuesLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeInterruptedException(e);
                }
            }
        }
        
        @Override
        public void onManagementStarting() {
            if (latching) {
                managingStartedLatch.countDown();
                try {
                    managingContinuesLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeInterruptedException(e);
                }
            }
        }
        
        @Override
        public void onManagementStarted() {
            if (latching) {
                managedStartedLatch.countDown();
                try {
                    managedContinuesLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeInterruptedException(e);
                }
            }
        }
        
        @Override
        public RebindSupport<EntityMemento> getRebindSupport() {
            return new BasicEntityRebindSupport(this) {
                @Override protected void doReconstruct(RebindContext rebindContext, EntityMemento memento) {
                    MyLatchingEntityImpl.this.onReconstruct();
                }
            };
        }
    }
}
