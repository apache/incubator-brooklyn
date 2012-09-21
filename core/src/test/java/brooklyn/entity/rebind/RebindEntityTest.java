package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.RebindContext;
import brooklyn.test.TestUtils;
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
        MyEntity origE = new MyEntity(MutableMap.of("myfield", "myval"), origApp);
        MyEntity origE2 = new MyEntity(MutableMap.of("myfield", "myval2"), origE);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);

        // Assert has expected config/fields
        assertTrue(newApp.rebound);
        
        assertEquals(newApp.getOwnedChildren().size(), 1);
        MyEntity newE = (MyEntity) Iterables.get(newApp.getOwnedChildren(), 0);
        assertTrue(newE.rebound);
        assertEquals(newE.myfield, "myval");

        assertEquals(newE.getOwnedChildren().size(), 1);
        MyEntity newE2 = (MyEntity) Iterables.get(newE.getOwnedChildren(), 0);
        assertTrue(newE2.rebound);
        assertEquals(newE2.myfield, "myval2");
        
        assertNotSame(origApp, newApp);
        assertNotSame(origApp.getManagementContext(), newApp.getManagementContext());
        assertNotSame(origE, newE);
        assertNotSame(origE2, newE2);
    }
    
    @Test
    public void testRestoresGroupMembers() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("myfield", "myval"), origApp);
        MyEntity origE2 = new MyEntity(MutableMap.of("myfield", "myval2"), origApp);
        BasicGroup origG = new BasicGroup(origApp);
        origG.addMember(origE);
        origG.addMember(origE2);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);
        
        BasicGroup newG = (BasicGroup) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(BasicGroup.class));
        Iterable<Entity> newEs = Iterables.filter(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(ImmutableSet.copyOf(newG.getMembers()), ImmutableSet.copyOf(newEs));
    }
    
    @Test
    public void testRestoresEntityConfig() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("myconfig", "myval"), origApp);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testRestoresEntityIdAndDisplayName() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("displayName", "mydisplayname"), origApp);
        String eId = origE.getId();
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        assertEquals(newE.getId(), eId);
        assertEquals(newE.getDisplayName(), "mydisplayname");
    }
    
    @Test
    public void testRebindsSubscriptions() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("subscribe", true), origApp);
        origApp.getManagementContext().manage(origApp);
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        newApp.setAttribute(MyApplication.MY_SENSOR, "mysensorval");
        TestUtils.assertEventually(Suppliers.ofInstance(newE.events), Predicates.equalTo(ImmutableList.of("mysensorval")));
    }
    
    @Test
    public void testEntityUnmanagedDuringRebind() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("subscribe", true), origApp);
        origApp.getManagementContext().manage(origApp);

        // Serialize and rebind, but don't yet manage the app
        BrooklynMemento memento = origApp.getManagementContext().getMemento();
        LocalManagementContext managementContext = new LocalManagementContext();
        MyApplication newApp = (MyApplication) managementContext.rebind(memento, getClass().getClassLoader()).get(0);
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));
        
        // Entities should not be available yet (i.e. not managed)
        assertNull(managementContext.getEntity(origApp.getId()));
        assertNull(managementContext.getEntity(origE.getId()));

        // When we manage the app, then the entities will be available
        managementContext.manage(newApp);
        assertEquals(managementContext.getEntity(origApp.getId()), newApp);
        assertEquals(managementContext.getEntity(origE.getId()), newE);
    }
    
    // Serialize, and de-serialize with a different management context
    private Application serializeAndRebind(AbstractApplication app) {
        BrooklynMemento memento = app.getManagementContext().getMemento();
        
        LocalManagementContext managementContext = new LocalManagementContext();
        List<Application> apps = managementContext.rebind(memento, getClass().getClassLoader());
        assertEquals(apps.size(), 1, "apps="+apps);
        
        managementContext.manage(apps.get(0));

        return apps.get(0);
    }
    
    public static class MyApplication extends AbstractApplication {
        private static final long serialVersionUID = 1L;
        
        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                        String.class, "test.mysensor", "My test sensor");
        
        boolean rebound;

        public MyApplication() {
        }

        public MyApplication(Map flags) {
            super(flags);
        }
        
        @Override
        protected void doRebind(RebindContext rebindContext, EntityMemento memento) {
            rebound = true;
        }
    }
    
    public static class MyEntity extends AbstractEntity {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag("myconfig")
        public static final ConfigKey<String> MY_CONFIG = new BasicConfigKey<String>(
                        String.class, "test.myconfig", "My test config");

        @SetFromFlag
        String myfield;
        
        @SetFromFlag
        boolean subscribe;
        
        boolean rebound;

        final List<String> events = new CopyOnWriteArrayList<String>();
        
        public MyEntity(Map flags, Entity owner) {
            super(flags, owner);
        }
        
        @Override
        public MyEntityMemento getMemento() {
            // Uses a custom memento; could equally have done super.getMementoWithProperties(props)
            return new MyEntityMemento(this);
        }

        @Override
        public void reconstruct(EntityMemento memento) {
            super.reconstruct(memento);
            myfield = ((MyEntityMemento)memento).myfield;
            subscribe = ((MyEntityMemento)memento).subscribe;
        }
        
        @Override
        public void doRebind(RebindContext rebindContext, EntityMemento memento) {
            if (subscribe) {
                subscribe(getApplication(), MyApplication.MY_SENSOR, new SensorEventListener<String>() {
                    @Override public void onEvent(SensorEvent<String> event) {
                        events.add(event.getValue());
                    }
                });
            }
            
            rebound = true;
        }
    }
    
    static class MyEntityMemento extends BasicEntityMemento {
        private static final long serialVersionUID = 1L;
        
        String myfield;
        boolean subscribe;
        
        MyEntityMemento(MyEntity entity) {
            super(entity);
            myfield = entity.myfield;
            subscribe = entity.subscribe;
        }
    }
}
