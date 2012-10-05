package brooklyn.entity.rebind;

import static brooklyn.test.EntityTestUtils.assertAttributeEquals;
import static brooklyn.test.EntityTestUtils.assertConfigEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.rebind.RebindLocationTest.MyLocation;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.EntityMemento;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindEntityTest {

    // FIXME Add test about dependent configuration serialization?!
    
    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private MyApplication origApp;
    private File mementoDir;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);
        origApp = new MyApplication();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @Test
    public void testRestoresSimpleApp() throws Exception {
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        assertNotSame(newApp, origApp);
        assertEquals(newApp.getId(), origApp.getId());
    }
    
    @Test
    public void testRestoresEntityHierarchy() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        MyEntity origE2 = new MyEntity(origE);
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();

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
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        
        BasicGroup newG = (BasicGroup) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(BasicGroup.class));
        Iterable<Entity> newEs = Iterables.filter(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(ImmutableSet.copyOf(newG.getMembers()), ImmutableSet.copyOf(newEs));
    }
    
    @Test
    public void testRestoresEntityConfig() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testRestoresEntitySensors() throws Exception {
        AttributeSensor<String> myCustomAttribute = new BasicAttributeSensor<String>(String.class, "my.custom.attribute");
        
        MyEntity origE = new MyEntity(origApp);
        managementContext.manage(origApp);
        origE.setAttribute(myCustomAttribute, "myval");
        
        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getAttribute(myCustomAttribute), "myval");
    }
    
    @Test
    public void testRestoresEntityIdAndDisplayName() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("displayName", "mydisplayname"), origApp);
        String eId = origE.getId();
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getId(), eId);
        assertEquals(newE.getDisplayName(), "mydisplayname");
    }
    
    @Test
    public void testCanCustomizeRebind() throws Exception {
        MyEntity2 origE = new MyEntity2(MutableMap.of("myfield", "myval"), origApp);
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        
        MyEntity2 newE = (MyEntity2) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity2.class));
        assertEquals(newE.myfield, "myval");
    }
    
    @Test
    public void testRebindsSubscriptions() throws Exception {
        MyEntity2 origE = new MyEntity2(MutableMap.of("subscribe", true), origApp);
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
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
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
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
        managementContext.manage(origApp);
        origApp.start(ImmutableList.of(origLoc));
        
        MyApplication newApp = rebind();
        MyEntityReffingOthers newE = (MyEntityReffingOthers) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntityReffingOthers.class));
        MyLocation newLoc = (MyLocation) Iterables.getOnlyElement(newApp.getLocations());
        
        assertAttributeEquals(newE, MyEntityReffingOthers.LOCATION_REF_SENSOR, newLoc);
        assertConfigEquals(newE, MyEntityReffingOthers.LOCATION_REF_CONFIG, newLoc);
    }

    // FIXME Test is broken, because RebindManager now calls manage
//    @Test(enabled=false)
//    public void testEntityUnmanagedDuringRebind() throws Exception {
//        MyEntity origE = new MyEntity(MutableMap.of("subscribe", true), origApp);
//        managementContext.manage(origApp);
//
//        // Serialize and rebind, but don't yet manage the app
//        BrooklynMemento memento = origApp.getManagementContext().getRebindManager().getMemento();
//        LocalManagementContext managementContext = new LocalManagementContext();
//        MyApplication newApp = (MyApplication) managementContext.getRebindManager().rebind(memento, getClass().getClassLoader()).get(0);
//        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
//        
//        // Entities should not be available yet (i.e. not managed)
//        assertNull(managementContext.getEntity(origApp.getId()));
//        assertNull(managementContext.getEntity(origE.getId()));
//
//        // When we manage the app, then the entities will be available
//        managementContext.manage(newApp);
//        assertEquals(managementContext.getEntity(origApp.getId()), newApp);
//        assertEquals(managementContext.getEntity(origE.getId()), newE);
//    }
//    
//    // FIXME Alex is looking at the "brooklyn management start sequence"
//    @Test(enabled=false, groups="WIP")
//    public void testSubscriptionAndPublishingNotActiveUntilAppIsManaged() throws Exception {
//        MyEntity2 origE = new MyEntity2(MutableMap.of("subscribe", true), origApp);
//        managementContext.manage(origApp);
//        
//        // Serialize and rebind, but don't yet manage the app
//        BrooklynMemento memento = origApp.getManagementContext().getRebindManager().getMemento();
//        LocalManagementContext managementContext = new LocalManagementContext();
//        MyApplication newApp = (MyApplication) managementContext.getRebindManager().rebind(memento, getClass().getClassLoader()).get(0);
//        MyEntity2 newE = (MyEntity2) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity2.class));
//
//        // Publishing before managed: should not be received by subscriber
//        newApp.setAttribute(MyApplication.MY_SENSOR, "mysensorval");
//        TestUtils.assertContinuallyFromJava(Suppliers.ofInstance(newE.events), Predicates.equalTo(Collections.emptyList()));
//    }
    
    @Test
    public void testRestoresConfigKeys() throws Exception {
        TestEntity origE = new TestEntity(origApp);
        origE.setConfig(TestEntity.CONF_LIST_PLAIN, ImmutableList.of("val1", "val2"));
        origE.setConfig(TestEntity.CONF_MAP_PLAIN, ImmutableMap.of("akey", "aval"));
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getConfig(TestEntity.CONF_LIST_PLAIN), ImmutableList.of("val1", "val2"));
        assertEquals(newE.getConfig(TestEntity.CONF_MAP_PLAIN), ImmutableMap.of("akey", "aval"));
    }

    
    @Test
    public void testRestoresListConfigKey() throws Exception {
        TestEntity origE = new TestEntity(origApp);
        origE.setConfig(TestEntity.CONF_LIST_THING.subKey(), "val1");
        origE.setConfig(TestEntity.CONF_LIST_THING.subKey(), "val2");
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getConfig(TestEntity.CONF_LIST_THING), ImmutableList.of("val1", "val2"));
    }

    @Test
    public void testRestoresMapConfigKey() throws Exception {
        TestEntity origE = new TestEntity(origApp);
        origE.setConfig(TestEntity.CONF_MAP_THING.subKey("akey"), "aval");
        origE.setConfig(TestEntity.CONF_MAP_THING.subKey("bkey"), "bval");
        managementContext.manage(origApp);
        
        MyApplication newApp = rebind();
        final TestEntity newE = (TestEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(TestEntity.class));

        assertEquals(newE.getConfig(TestEntity.CONF_MAP_THING), ImmutableMap.of("akey", "aval", "bkey", "bval"));
    }

    @Test
    public void testRebindPreservesInheritedConfig() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        origApp.setConfig(MyEntity.MY_CONFIG, "myValInSuper");
        managementContext.manage(origApp);

        // rebind: inherited config is preserved
        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myValInSuper");
        assertEquals(newApp.getConfig(MyEntity.MY_CONFIG), "myValInSuper");
        
        // This config should be inherited by dynamically-added children of app
        MyEntity newE2 = new MyEntity(origApp);
        newApp.getManagementContext().manage(newE2);
        
        assertEquals(newE2.getConfig(MyEntity.MY_CONFIG), "myValInSuper");
        
    }

    @Test
    public void testRebindPreservesGetConfigWithDefault() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        managementContext.manage(origApp);

        assertNull(origE.getConfig(MyEntity.MY_CONFIG));
        assertEquals(origE.getConfig(MyEntity.MY_CONFIG, "mydefault"), "mydefault");
        
        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertNull(newE.getConfig(MyEntity.MY_CONFIG));
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG, "mydefault"), "mydefault");
    }

    @Test
    public void testRebindPersistsNullAttribute() throws Exception {
        MyEntity origE = new MyEntity(origApp);
        origE.setAttribute(MyEntity.MY_SENSOR, null);
        managementContext.manage(origApp);

        assertNull(origE.getAttribute(MyEntity.MY_SENSOR));

        MyApplication newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertNull(newE.getAttribute(MyEntity.MY_SENSOR));
    }

    private MyApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        RebindTestUtils.checkCurrentMementoSerializable(origApp);
        return (MyApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
    
    public static class MyApplication extends AbstractApplication {
        private static final long serialVersionUID = 1L;
        
        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                        String.class, "test.mysensor", "My test sensor");
        
        public MyApplication() {
        }

        public MyApplication(Map flags) {
            super(flags);
        }
    }
    
    public static class MyEntity extends AbstractEntity implements Startable {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myconfig", "My test config");

        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                String.class, "test.mysensor", "My test sensor");
        
        private final Object dummy = new Object(); // so not serializable

        public MyEntity(Entity owner) {
            super(owner);
        }
        
        public MyEntity(Map flags, Entity owner) {
            super(flags, owner);
        }

        @Override
        public void start(Collection<? extends Location> locations) {
            getLocations().addAll(locations);
        }

        @Override
        public void stop() {
        }

        @Override
        public void restart() {
        }
    }
    
    public static class MyEntityReffingOthers extends AbstractEntity {
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
        
        private final Object dummy = new Object(); // so not serializable

        public MyEntityReffingOthers(Entity owner) {
            super(owner);
        }
        
        public MyEntityReffingOthers(Map flags, Entity owner) {
            super(flags, owner);
        }
    }
    
    public static class MyEntity2 extends AbstractEntity {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myconfig", "My test config");

        @SetFromFlag("subscribe")
        public static final ConfigKey<Boolean> SUBSCRIBE = new BasicConfigKey<Boolean>(
                Boolean.class, "test.subscribe", "Whether to do some subscriptions on re-bind", false);
        
        @SetFromFlag
        String myfield;
        
        final List<String> events = new CopyOnWriteArrayList<String>();

        private final Object dummy = new Object(); // so not serializable

        public MyEntity2(Map flags, Entity owner) {
            super(flags, owner);
        }
        
        @Override
        public void onManagementStarting() {
            if (getConfig(SUBSCRIBE)) {
                subscribe(getApplication(), MyApplication.MY_SENSOR, new SensorEventListener<String>() {
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
                    myfield = (String) memento.getCustomProperty("myfield");
                }
            };
        }
    }
}
