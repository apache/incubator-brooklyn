package brooklyn.entity.rebind;

import static brooklyn.entity.rebind.RebindTestUtils.serializeAndRebind;
import static brooklyn.test.EntityTestUtils.assertAttributeEquals;
import static brooklyn.test.EntityTestUtils.assertConfigEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.rebind.RebindLocationTest.MyLocation;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class RebindEntityTest {

    private MyApplication origApp;

    @BeforeMethod
    public void setUp() throws Exception {
        origApp = new MyApplication();
    }
    
    @Test
    public void testRestoresEntityHierarchy() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        MyEntity origE2 = new MyEntity(origE);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());

        // Assert has expected config/fields
        assertEquals(newApp.getId(), origApp.getId());
        
        assertEquals(newApp.getOwnedChildren().size(), 1);
        MyEntity newE = (MyEntity) Iterables.get(newApp.getOwnedChildren(), 0);
        assertEquals(newE.getId(), origE.getId());

        assertEquals(newE.getOwnedChildren().size(), 1);
        MyEntity newE2 = (MyEntity) Iterables.get(newE.getOwnedChildren(), 0);
        assertEquals(newE2.getId(), origE2.getId());
        
        assertNotSame(origApp, newApp);
        assertNotSame(origApp.getManagementContext(), newApp.getManagementContext());
        assertNotSame(origE, newE);
        assertNotSame(origE2, newE2);
    }
    
    @Test
    public void testRestoresGroupMembers() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        MyEntity origE2 = new MyEntity(origApp);
        BasicGroup origG = new BasicGroup(origApp);
        origG.addMember(origE);
        origG.addMember(origE2);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        
        BasicGroup newG = (BasicGroup) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(BasicGroup.class));
        Iterable<Entity> newEs = Iterables.filter(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(ImmutableSet.copyOf(newG.getMembers()), ImmutableSet.copyOf(newEs));
    }
    
    @Test
    public void testRestoresEntityConfig() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testRestoresEntitySensors() throws Exception {
        AttributeSensor<String> myCustomAttribute = new BasicAttributeSensor<String>(String.class, "my.custom.attribute");
        
        MyEntity origE = new MyEntity(origApp);
        origApp.getManagementContext().manage(origApp);
        origE.setAttribute(myCustomAttribute, "myval");
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getAttribute(myCustomAttribute), "myval");
    }
    
    @Test
    public void testRestoresEntityIdAndDisplayName() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("displayName", "mydisplayname"), origApp);
        String eId = origE.getId();
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getId(), eId);
        assertEquals(newE.getDisplayName(), "mydisplayname");
    }
    
    @Test
    public void testCanCustomizeRebind() throws Exception {
        MyEntity2 origE = new MyEntity2(MutableMap.of("myfield", "myval"), origApp);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntity2 newE = (MyEntity2) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity2.class));
        assertEquals(newE.rebound, true);
        assertEquals(newE.myfield, "myval");
    }
    
    @Test
    public void testRebindsSubscriptions() throws Exception {
        MyEntity2 origE = new MyEntity2(MutableMap.of("subscribe", true), origApp);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntity2 newE = (MyEntity2) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity2.class));
        
        newApp.setAttribute(MyApplication.MY_SENSOR, "mysensorval");
        TestUtils.assertEventually(Suppliers.ofInstance(newE.events), Predicates.equalTo(ImmutableList.of("mysensorval")));
    }
    
    @Test
    public void testHandlesReferencingOtherEntities() throws Exception {
        MyEntity origOtherE = new MyEntity(origApp);
        MyEntityReffingOthers origE = new MyEntityReffingOthers(
                MutableMap.builder()
                        .put("entityRef", origOtherE)
                        .build(),
                origApp);
        origE.setAttribute(MyEntityReffingOthers.ENTITY_REF_SENSOR, origOtherE);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntityReffingOthers newE = (MyEntityReffingOthers) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntityReffingOthers.class));
        MyEntity newOtherE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertAttributeEquals(newE, MyEntityReffingOthers.ENTITY_REF_SENSOR, newOtherE);
        assertConfigEquals(newE, MyEntityReffingOthers.ENTITY_REF_CONFIG, newOtherE);
    }
    
    @Test
    public void testHandlesReferencingOtherLocations() throws Exception {
        MyLocation origLoc = new MyLocation();
        MyEntityReffingOthers origE = new MyEntityReffingOthers(
                MutableMap.builder()
                        .put("locationRef", origLoc)
                        .build(),
                origApp);
        origE.setAttribute(MyEntityReffingOthers.LOCATION_REF_SENSOR, origLoc);
        origApp.getManagementContext().manage(origApp);
        origApp.start(ImmutableList.of(origLoc));
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntityReffingOthers newE = (MyEntityReffingOthers) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntityReffingOthers.class));
        MyLocation newLoc = (MyLocation) Iterables.getOnlyElement(newApp.getLocations());
        
        assertAttributeEquals(newE, MyEntityReffingOthers.LOCATION_REF_SENSOR, newLoc);
        assertConfigEquals(newE, MyEntityReffingOthers.LOCATION_REF_CONFIG, newLoc);
    }

    // FIXME Test is broken, because RebindManager now calls manage
    @Test(enabled=false)
    public void testEntityUnmanagedDuringRebind() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("subscribe", true), origApp);
        origApp.getManagementContext().manage(origApp);

        // Serialize and rebind, but don't yet manage the app
        BrooklynMemento memento = origApp.getManagementContext().getRebindManager().getMemento();
        LocalManagementContext managementContext = new LocalManagementContext();
        MyApplication newApp = (MyApplication) managementContext.getRebindManager().rebind(memento, getClass().getClassLoader()).get(0);
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        // Entities should not be available yet (i.e. not managed)
        assertNull(managementContext.getEntity(origApp.getId()));
        assertNull(managementContext.getEntity(origE.getId()));

        // When we manage the app, then the entities will be available
        managementContext.manage(newApp);
        assertEquals(managementContext.getEntity(origApp.getId()), newApp);
        assertEquals(managementContext.getEntity(origE.getId()), newE);
    }
    
    // FIXME Alex is looking at the "brooklyn management start sequence"
    @Test(enabled=false, groups="WIP")
    public void testSubscriptionAndPublishingNotActiveUntilAppIsManaged() throws Exception {
        MyEntity2 origE = new MyEntity2(MutableMap.of("subscribe", true), origApp);
        origApp.getManagementContext().manage(origApp);
        
        // Serialize and rebind, but don't yet manage the app
        BrooklynMemento memento = origApp.getManagementContext().getRebindManager().getMemento();
        LocalManagementContext managementContext = new LocalManagementContext();
        MyApplication newApp = (MyApplication) managementContext.getRebindManager().rebind(memento, getClass().getClassLoader()).get(0);
        MyEntity2 newE = (MyEntity2) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity2.class));

        // Publishing before managed: should not be received by subscriber
        newApp.setAttribute(MyApplication.MY_SENSOR, "mysensorval");
        TestUtils.assertContinuallyFromJava(Suppliers.ofInstance(newE.events), Predicates.equalTo(Collections.emptyList()));
    }
    
    // FIXME Fails for setting config
    @Test(groups="WIP")
    public void testRestoresComplexConfigKeys() throws Exception {
        TestEntity origE = new TestEntity(origApp);
        origApp.getManagementContext().manage(origApp);
        
        TestApplication newApp = (TestApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getConfig(TestEntity.CONF_LIST_PLAIN), origE.getConfig(TestEntity.CONF_LIST_PLAIN));
        assertEquals(newE.getConfig(TestEntity.CONF_MAP_PLAIN), origE.getConfig(TestEntity.CONF_MAP_PLAIN));
        assertEquals(newE.getConfig(TestEntity.CONF_LIST_THING), origE.getConfig(TestEntity.CONF_LIST_THING));
        assertEquals(newE.getConfig(TestEntity.CONF_MAP_THING), origE.getConfig(TestEntity.CONF_MAP_THING));
    }

    // FIXME Fails because newE has the config explicitly set to null, rather than no entry for the config key
    @Test(groups="WIP")
    public void testRebindPreservesGetConfigWithDefault() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        origApp.getManagementContext().manage(origApp);

        assertNull(origE.getConfig(MyEntity.MY_CONFIG));
        assertEquals(origE.getConfig(MyEntity.MY_CONFIG, "mydefault"), "mydefault");
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertNull(newE.getConfig(MyEntity.MY_CONFIG));
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG, "mydefault"), "mydefault");
    }

    public static class MyApplication extends AbstractApplication implements Rebindable {
        private static final long serialVersionUID = 1L;
        
        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                        String.class, "test.mysensor", "My test sensor");
        
        public MyApplication() {
        }

        public MyApplication(Map flags) {
            super(flags);
        }
    }
    
    public static class MyEntity extends AbstractEntity implements Rebindable {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myconfig", "My test config");

        public MyEntity(Entity owner) {
            super(owner);
        }
        
        public MyEntity(Map flags, Entity owner) {
            super(flags, owner);
        }
    }
    
    public static class MyEntityReffingOthers extends AbstractEntity implements Rebindable {
        private static final long serialVersionUID = 1L;
        
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
        
        public MyEntityReffingOthers(Entity owner) {
            super(owner);
        }
        
        public MyEntityReffingOthers(Map flags, Entity owner) {
            super(flags, owner);
        }
    }
    
    public static class MyEntity2 extends AbstractEntity implements Rebindable {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myconfig", "My test config");

        @SetFromFlag("subscribe")
        public static final ConfigKey<Boolean> SUBSCRIBE = new BasicConfigKey<Boolean>(
                Boolean.class, "test.subscribe", "Whether to do some subscriptions on re-bind", false);
        
        @SetFromFlag
        String myfield;
        
        boolean rebound;

        final List<String> events = new CopyOnWriteArrayList<String>();
        
        public MyEntity2(Map flags, Entity owner) {
            super(flags, owner);
        }
        
        @Override
        public RebindSupport<EntityMemento> getRebindSupport() {
            return new BasicEntityRebindSupport(this) {
                @Override public EntityMemento getMemento() {
                    // Note: using MutableMap so accepts nulls
                    return new BasicEntityMemento(MyEntity2.this, MutableMap.<String,Object>of("myfield", myfield));
                }
                @Override protected void doReconstruct(RebindContext rebindContext, EntityMemento memento) {
                    super.doReconstruct(rebindContext, memento);
                    myfield = (String) memento.getCustomProperty("myfield");
                }
                @Override protected void doRebind(RebindContext rebindContext, EntityMemento memento) {
                    super.doRebind(rebindContext, memento);
                    
                    if (getConfig(SUBSCRIBE)) {
                        subscribe(getApplication(), MyApplication.MY_SENSOR, new SensorEventListener<String>() {
                            @Override public void onEvent(SensorEvent<String> event) {
                                events.add(event.getValue());
                            }
                        });
                    }
                    
                    rebound = true;
                }
            };
        }
    }
}
