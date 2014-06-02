package brooklyn.entity.rebind;

import static brooklyn.test.EntityTestUtils.assertAttributeEquals;
import static brooklyn.test.EntityTestUtils.assertConfigEquals;
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

import junit.framework.Assert;

import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationConfigTest.MyLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.EntityMemento;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Durations;

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
        origApp.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");
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
        newApp.setAttribute(TestApplication.MY_ATTRIBUTE, "myval");
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testRestoresEntitySensors() throws Exception {
        AttributeSensor<String> myCustomAttribute = Sensors.newStringSensor("my.custom.attribute");
        
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        origE.setAttribute(myCustomAttribute, "myval");
        
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getAttribute(myCustomAttribute), "myval");
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
        
        newApp.setAttribute(TestApplication.MY_ATTRIBUTE, "mysensorval");
        Asserts.eventually(Suppliers.ofInstance(newE.getEvents()), Predicates.<List<String>>equalTo(ImmutableList.of("mysensorval")));
        Assert.assertEquals(newE, origE);
    }
    
    @Test
    public void testHandlesReferencingOtherEntities() throws Exception {
        MyEntity origOtherE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        MyEntityReffingOthers origE = origApp.createAndManageChild(EntitySpec.create(MyEntityReffingOthers.class)
                .configure("entityRef", origOtherE));
        origE.setAttribute(MyEntityReffingOthers.ENTITY_REF_SENSOR, origOtherE);
        
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
        origApp.setConfig(TestEntity.CONF_OBJECT, reffer);

        newApp = rebind(false);
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
        origApp.setConfig(TestEntity.CONF_OBJECT, reffer);

        newApp = rebind(false);
        MyEntityWithMultipleInterfaces newE = (MyEntityWithMultipleInterfaces) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntityWithMultipleInterfaces.class));
        ReffingEntity newReffer = (ReffingEntity)newApp.getConfig(TestEntity.CONF_OBJECT);
        
        assertEquals(newReffer.group, newE);
        assertEquals(newReffer.resizable, newE);
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
        origE.setAttribute(MyEntityReffingOthers.LOCATION_REF_SENSOR, origLoc);
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
        newManagementContext = new LocalManagementContext();
        Thread thread = new Thread() {
            public void run() {
                try {
                    RebindTestUtils.rebind(newManagementContext, mementoDir, getClass().getClassLoader());
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
                    RebindTestUtils.rebind(newManagementContext, mementoDir, getClass().getClassLoader());
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
        origApp.setConfig(MyEntity.MY_CONFIG, "myValInSuper");
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

        //Thread.sleep(1000);
        newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        final TestEntity newChildE = (TestEntity) Iterables.find(newE.getChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getAllConfigBag().getStringKey("myunmatchedkey"), "myunmatchedval");
        assertEquals(newE.getLocalConfigBag().getStringKey("myunmatchedkey"), "myunmatchedval");
        
        try {
            assertEquals(newChildE.getAllConfigBag().getStringKey("myunmatchedkey"), "myunmatchedval");
            assertFalse(newChildE.getLocalConfigBag().containsKey("myunmatchedkey"));
        } catch (Throwable t) {
            t.printStackTrace();
            throw Exceptions.propagate(t);
        }
    }

    @Test
    public void testRebindPersistsNullAttribute() throws Exception {
        MyEntity origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        origE.setAttribute(MyEntity.MY_SENSOR, null);

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

        origApp.setAttribute(MY_DYNAMIC_SENSOR, "myval");
        assertEquals(origApp.getEntityType().getSensor(sensorName).getDescription(), sensorDescription);

        newApp = rebind();
        
        assertEquals(newApp.getAttribute(MY_DYNAMIC_SENSOR), "myval");
        assertEquals(newApp.getEntityType().getSensor(sensorName).getDescription(), sensorDescription);
    }

    @Test
    public void testRebindWhenPreviousAppDestroyedHasNoApp() throws Exception {
        origApp.stop();
        
        RebindTestUtils.waitForPersisted(origManagementContext);
        LocalManagementContext newManagementContext = RebindTestUtils.newPersistingManagementContextUnstarted(mementoDir, classLoader);
        List<Application> newApps = newManagementContext.getRebindManager().rebind(classLoader);
        newManagementContext.getRebindManager().start();
        
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
                subscribe(getApplication(), TestApplication.MY_ATTRIBUTE, new SensorEventListener<String>() {
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
                setAttribute(MY_SENSOR, getConfig(PUBLISH));
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
