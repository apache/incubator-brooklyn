package brooklyn.entity.rebind;

import static brooklyn.entity.rebind.RebindTestUtils.serializeAndRebind;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
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
    private MyEntity origE;
    
    @BeforeMethod
    public void setUp() throws Exception {
        origApp = new MyApplication();
        origE = new MyEntity(origApp);
        new LocalManagementContext().manage(origApp);
    }
    
    @Test
    public void testSetsLocationOnEntities() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));

        MyApplication newApp = (MyApplication) RebindTestUtils.serializeAndRebind(origApp, getClass().getClassLoader());
        MyEntity newE = (MyEntity) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(MyEntity.class));

        assertEquals(newApp.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newApp.getLocations(), 0) instanceof MyLocation);
        
        assertEquals(newE.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newE.getLocations(), 0) instanceof MyLocation);
    }
    
    @Test
    public void testRestoresLocationIdAndDisplayName() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));
        
        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getId(), origLoc.getId());
        assertEquals(newLoc.getName(), origLoc.getName());
    }
    
    @Test
    public void testCanCustomizeLocationRebind() throws Exception {
        MyLocation2 origLoc = new MyLocation2(MutableMap.of("name", "mylocname", "myfield", "myval"));
        origApp.invoke(MyApplication.START, ImmutableMap.of("locations", ImmutableList.of(origLoc)))
        		.blockUntilEnded();

        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
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

        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myfield, "myval");
    }
    
    @Test
    public void testIgnoresTransientFields() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myTransientField", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        MyApplication newApp = (MyApplication) serializeAndRebind(origApp, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myTransientField, null);
    }
    
    @Test
    public void testIgnoresStaticFields() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myStaticField", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        BrooklynMemento brooklynMemento = RebindTestUtils.serialize(origApp);
        MyLocation.myStaticField = "mynewval";
        MyApplication newApp = (MyApplication) RebindTestUtils.rebind(brooklynMemento, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myStaticField, "mynewval");
    }
    
    @Test
    public void testHandlesFieldReferencingOtherLocations() throws Exception {
    	MyLocation origOtherLoc = new MyLocation();
    	MyLocationReffingOthers origLoc = new MyLocationReffingOthers(MutableMap.of("otherLocs", ImmutableList.of(origOtherLoc)));
    	origOtherLoc.setParentLocation(origLoc);
    	
        origApp.start(ImmutableList.of(origLoc));

        Application newApp = serializeAndRebind(origApp, getClass().getClassLoader());
        MyLocationReffingOthers newLoc = (MyLocationReffingOthers) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getChildLocations().size(), 1);
        assertTrue(Iterables.get(newLoc.getChildLocations(), 0) instanceof MyLocation, "children="+newLoc.getChildLocations());
        assertEquals(newLoc.otherLocs, ImmutableList.copyOf(newLoc.getChildLocations()));
    }
    
    public static class MyApplication extends AbstractApplication implements Rebindable {
        private static final long serialVersionUID = 1L;
        
        public static final AttributeSensor<String> MY_SENSOR = new BasicAttributeSensor<String>(
                        String.class, "test.mysensor", "My test sensor");
        
        private final Object dummy = new Object(); // so not serializable

        public MyApplication() {
        }

        public MyApplication(Map flags) {
            super(flags);
        }
    }
    
    public static class MyLocation extends AbstractLocation implements RebindableLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        String myfield;

        private final Object dummy = new Object(); // so not serializable
        
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

        private final Object dummy = new Object(); // so not serializable

        public MyLocationReffingOthers(Map flags) {
            super(flags);
        }
    }
    
    public static class MyLocation2 extends AbstractLocation implements RebindableLocation {
        private static final long serialVersionUID = 1L;
        
        String myfield;
        boolean rebound;

        private final Object dummy = new Object(); // so not serializable

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
                    return getMementoWithProperties(MutableMap.<String,Object>of("myfield", myfield));
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
