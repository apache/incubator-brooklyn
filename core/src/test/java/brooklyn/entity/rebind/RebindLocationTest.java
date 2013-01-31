package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.RebindEntityTest.MyApplication;
import brooklyn.entity.rebind.RebindEntityTest.MyApplicationImpl;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.entity.rebind.RebindEntityTest.MyEntityImpl;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindLocationTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private MyApplication origApp;
    private MyEntity origE;
    private File mementoDir;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = new MyApplicationImpl();
        origE = new MyEntityImpl(origApp);
        Entities.startManagement(origApp, managementContext);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }
    
    @Test
    public void testSetsLocationOnEntities() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));

        MyApplication newApp = (MyApplication) rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));

        assertEquals(newApp.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newApp.getLocations(), 0) instanceof MyLocation);
        
        assertEquals(newE.getLocations().size(), 1, "locs="+newE.getLocations());
        assertTrue(Iterables.get(newE.getLocations(), 0) instanceof MyLocation);
    }
    
    @Test
    public void testRestoresLocationIdAndDisplayName() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));
        
        MyApplication newApp = (MyApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getId(), origLoc.getId());
        assertEquals(newLoc.getName(), origLoc.getName());
    }
    
    @Test
    public void testCanCustomizeLocationRebind() throws Exception {
        MyLocationCustomProps origLoc = new MyLocationCustomProps(MutableMap.of("name", "mylocname", "myfield", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        MyApplication newApp = (MyApplication) rebind();
        MyLocationCustomProps newLoc2 = (MyLocationCustomProps) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc2.getId(), origLoc.getId());
        assertEquals(newLoc2.getName(), origLoc.getName());
        assertEquals(newLoc2.rebound, true);
        assertEquals(newLoc2.myfield, "myval");
    }
    
    @Test
    public void testRestoresFieldsWithSetFromFlag() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myfield", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        MyApplication newApp = (MyApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myfield, "myval");
    }
    
    @Test
    public void testRestoresAtomicLongWithSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("myAtomicLong", "123"));
        origApp.start(ImmutableList.of(origLoc));

        origLoc.myAtomicLong.incrementAndGet();
        assertEquals(origLoc.myAtomicLong.get(), 124L);
        ((EntityInternal)origApp).getManagementSupport().getManagementContext(false).getRebindManager().getChangeListener().onChanged(origLoc);
        
        MyApplication newApp = (MyApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myAtomicLong.get(), 124L);
    }
    
    @Test
    public void testIgnoresTransientFields() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myTransientField", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        MyApplication newApp = (MyApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myTransientField, null);
    }
    
    @Test
    public void testIgnoresStaticFields() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myStaticField", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        RebindTestUtils.waitForPersisted(origApp);
        MyLocation.myStaticField = "mynewval"; // not auto-checkpointed
        MyApplication newApp = (MyApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myStaticField, "mynewval");
    }
    
    @Test
    public void testHandlesFieldReferencingOtherLocations() throws Exception {
    	MyLocation origOtherLoc = new MyLocation();
    	MyLocationReffingOthers origLoc = new MyLocationReffingOthers(MutableMap.of("otherLocs", ImmutableList.of(origOtherLoc), "myfield", "myval"));
    	origOtherLoc.setParentLocation(origLoc);
    	
        origApp.start(ImmutableList.of(origLoc));

        Application newApp = rebind();
        MyLocationReffingOthers newLoc = (MyLocationReffingOthers) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getChildLocations().size(), 1);
        assertTrue(Iterables.get(newLoc.getChildLocations(), 0) instanceof MyLocation, "children="+newLoc.getChildLocations());
        assertEquals(newLoc.otherLocs, ImmutableList.copyOf(newLoc.getChildLocations()));
        
        // Confirm this didn't override other values (e.g. setting other fields back to their defaults, as was once the case!)
        assertEquals(newLoc.myfield, "myval");
    }

    private MyApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (MyApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
    
    public static class MyLocation extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        String myfield;

        @SetFromFlag(defaultVal="1")
        AtomicLong myAtomicLong;

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
    
    public static class MyLocationReffingOthers extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag(defaultVal="a")
        String myfield;

        @SetFromFlag
        List<Location> otherLocs;

        private final Object dummy = new Object(); // so not serializable

        public MyLocationReffingOthers(Map flags) {
            super(flags);
        }
    }
    
    public static class MyLocationCustomProps extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        String myfield;
        boolean rebound;

        private final Object dummy = new Object(); // so not serializable

        public MyLocationCustomProps() {
        }
        
        public MyLocationCustomProps(Map flags) {
            super(flags);
            myfield = (String) flags.get("myfield");
        }
        
        @Override
        public RebindSupport<LocationMemento> getRebindSupport() {
            return new BasicLocationRebindSupport(this) {
                @Override public LocationMemento getMemento() {
                    return getMementoWithProperties(MutableMap.<String,Object>of("myfield", myfield));
                }
                @Override
                protected void doReconsruct(RebindContext rebindContext, LocationMemento memento) {
                    super.doReconsruct(rebindContext, memento);
                    myfield = (String) memento.getCustomField("myfield");
                    rebound = true;
                }
            };
        }
    }
}
