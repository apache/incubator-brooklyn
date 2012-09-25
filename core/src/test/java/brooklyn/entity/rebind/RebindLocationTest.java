package brooklyn.entity.rebind;

import static brooklyn.entity.rebind.RebindTestUtils.serializeRebindAndManage;
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
    
    @BeforeMethod
    public void setUp() throws Exception {
        origApp = new MyApplication();
    }
    
    @Test
    public void testSetsLocationOnEntities() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        MyEntity origE = new MyEntity(MutableMap.of("myfield", "myval"), origApp);
        MyEntity origE2 = new MyEntity(MutableMap.of("myfield", "myval2"), origE);
        origApp.invoke(MyApplication.START, ImmutableMap.of("locations", ImmutableList.of(origLoc)))
        		.blockUntilEnded();

        MyApplication newApp = (MyApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));

        assertEquals(newApp.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newApp.getLocations(), 0) instanceof MyLocation);
        
        assertEquals(newE.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newE.getLocations(), 0) instanceof MyLocation);
    }
    
    @Test
    public void testRestoresLocationIdAndDisplayName() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        MyEntity origE = new MyEntity(MutableMap.of("displayName", "mydisplayname"), origApp);
        origApp.invoke(MyApplication.START, ImmutableMap.of("locations", ImmutableList.of(origLoc)))
        		.blockUntilEnded();
        
        MyApplication newApp = (MyApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getId(), origLoc.getId());
        assertEquals(newLoc.getName(), origLoc.getName());
    }
    
    @Test
    public void testCanCustomizeLocationRebind() throws Exception {
        MyLocation2 origLoc = new MyLocation2(MutableMap.of("name", "mylocname", "myfield", "myval"));
        origApp.invoke(MyApplication.START, ImmutableMap.of("locations", ImmutableList.of(origLoc)))
        		.blockUntilEnded();

        MyApplication newApp = (MyApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        MyLocation2 newLoc2 = (MyLocation2) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc2.getId(), origLoc.getId());
        assertEquals(newLoc2.getName(), origLoc.getName());
        assertEquals(newLoc2.rebound, true);
        assertEquals(newLoc2.myfield, "myval");
    }
    
    @Test
    public void testRestoresFieldsWithSetFromFlag() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myfield", "myval"));
        origApp.start(ImmutableList.of(origLoc));
        origApp.getManagementContext().manage(origApp);

        MyApplication newApp = (MyApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myfield, "myval");
    }
    
    @Test
    public void testIgnoresTransientFields() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myTransientField", "myval"));
        origApp.start(ImmutableList.of(origLoc));
        origApp.getManagementContext().manage(origApp);

        MyApplication newApp = (MyApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myTransientField, null);
    }
    
    @Test
    public void testIgnoresStaticFields() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myStaticField", "myval"));
        origApp.start(ImmutableList.of(origLoc));
        origApp.getManagementContext().manage(origApp);

        BrooklynMemento brooklynMemento = RebindTestUtils.serialize(origApp);
        MyLocation.myStaticField = "mynewval";
        MyApplication newApp = (MyApplication) RebindTestUtils.rebindAndManage(brooklynMemento, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myStaticField, "mynewval");
    }
    
    @Test
    public void testHandlesFieldReferencingOtherLocations() throws Exception {
    	MyLocation origOtherLoc = new MyLocation();
    	MyLocationReffingOthers origLoc = new MyLocationReffingOthers(MutableMap.of("otherLocs", ImmutableList.of(origOtherLoc)));
    	origOtherLoc.setParentLocation(origLoc);
    	
        origApp.start(ImmutableList.of(origLoc));
        origApp.getManagementContext().manage(origApp);

        Application newApp = serializeRebindAndManage(origApp, getClass().getClassLoader());
        MyLocationReffingOthers newLoc = (MyLocationReffingOthers) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getChildLocations().size(), 1);
        assertTrue(Iterables.get(newLoc.getChildLocations(), 0) instanceof MyLocation, "children="+newLoc.getChildLocations());
        assertEquals(newLoc.otherLocs, ImmutableList.copyOf(newLoc.getChildLocations()));
    }
    
    @Test
    public void testFoo() throws Exception {
    	MyLocation origOtherLoc = new MyLocation();
    	MyLocationReffingOthers loc = new MyLocationReffingOthers(MutableMap.of("otherLocs", ImmutableList.of(origOtherLoc)));
    	MyLocationReffingOthers loc2 = RebindTestUtils.serializeAndDeserialize(loc);
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
        
        @SetFromFlag
        String myfield;

        @SetFromFlag
        transient String myTransientField;
        
        @SetFromFlag
        static String myStaticField;
        
        public MyLocation() {
        }
        
        public MyLocation(Map flags) {
            super(flags);
        }
    }
    
    public static class MyLocationReffingOthers extends AbstractLocation implements RebindableLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        List<Location> otherLocs;

        public MyLocationReffingOthers(Map flags) {
            super(flags);
        }
    }
    
    public static class MyLocation2 extends AbstractLocation implements RebindableLocation {
        private static final long serialVersionUID = 1L;
        
        String myfield;
        boolean rebound;

        public MyLocation2() {
        }
        
        public MyLocation2(Map flags) {
            super(flags);
            myfield = (String) flags.get("myfield");
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
                    myfield = (String) memento.getCustomProperty("myfield");
                    rebound = true;
                }
            };
        }
    }
}
