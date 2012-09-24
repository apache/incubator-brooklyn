package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class RebindLocationTest {

    private MyApplication origApp;
    private MyLocation origLoc;
    
    @BeforeMethod
    public void setUp() throws Exception {
        origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp = new MyApplication();
    }
    
    @Test
    public void testSetsLocationOnEntities() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("myfield", "myval"), origApp);
        MyEntity origE2 = new MyEntity(MutableMap.of("myfield", "myval2"), origE);
        origApp.invoke(MyApplication.START, ImmutableMap.of("locations", ImmutableList.of(origLoc)))
        		.blockUntilEnded();

        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));

        assertEquals(newApp.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newApp.getLocations(), 0) instanceof MyLocation);
        
        assertEquals(newE.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newE.getLocations(), 0) instanceof MyLocation);
    }
    
    @Test
    public void testRestoresLocationIdAndDisplayName() throws Exception {
        MyEntity origE = new MyEntity(MutableMap.of("displayName", "mydisplayname"), origApp);
        origApp.invoke(MyApplication.START, ImmutableMap.of("locations", ImmutableList.of(origLoc)))
        		.blockUntilEnded();
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getId(), origLoc.getId());
        assertEquals(newLoc.getName(), origLoc.getName());
    }
    
    @Test
    public void testCanCustomizeLocationRebind() throws Exception {
        MyLocation2 origLoc2 = new MyLocation2(MutableMap.of("name", "mylocname", "myfield", "myval"));
        origApp.invoke(MyApplication.START, ImmutableMap.of("locations", ImmutableList.of(origLoc2)))
        		.blockUntilEnded();

        MyApplication newApp = (MyApplication) serializeAndRebind(origApp);
        MyLocation2 newLoc2 = (MyLocation2) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc2.getId(), origLoc2.getId());
        assertEquals(newLoc2.getName(), origLoc2.getName());
        assertEquals(newLoc2.rebound, true);
        assertEquals(newLoc2.myfield, "myval");
    }
    
    // Serialize, and de-serialize with a different management context
    private Application serializeAndRebind(AbstractApplication app) {
        BrooklynMemento memento = app.getManagementContext().getRebindManager().getMemento();
        
        LocalManagementContext managementContext = new LocalManagementContext();
        List<Application> apps = managementContext.getRebindManager().rebind(memento, getClass().getClassLoader());
        assertEquals(apps.size(), 1, "apps="+apps);
        
        managementContext.manage(apps.get(0));

        return apps.get(0);
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
    
    public static class MyEntity extends AbstractEntity implements Startable, Rebindable {
        private static final long serialVersionUID = 1L;
        
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
            // no-op
        }

        @Override
        public void restart() {
            // no-op
        }
    }
    
    public static class MyLocation extends AbstractLocation implements RebindableLocation {
        private static final long serialVersionUID = 1L;
        
        public MyLocation() {
        }
        
        public MyLocation(Map flags) {
            super(flags);
        }
        
        // FIXME Move into AbstractLocation
        @Override
        public RebindSupport<LocationMemento> getRebindSupport() {
            return new BasicLocationRebindSupport(this);
        }
    }
    
    public static class MyLocation2 extends AbstractLocation implements RebindableLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        String myfield;
        
        boolean rebound;

        public MyLocation2() {
        }
        
        public MyLocation2(Map flags) {
            super(flags);
        }
        
        // FIXME Move into AbstractLocation
        @Override
        public RebindSupport<LocationMemento> getRebindSupport() {
            return new BasicLocationRebindSupport(this) {
                @Override public LocationMemento getMemento() {
                    // Note: using MutableMap so accepts nulls
                    return new BasicLocationMemento(MyLocation2.this, MutableMap.<String,Object>of("myfield", myfield));
                }
                @Override protected void doRebind(RebindContext rebindContext, LocationMemento memento) {
                	super.doRebind(rebindContext, memento);
                    myfield = (String) memento.getProperty("myfield");
                    rebound = true;
                }
            };
        }
    }
}
