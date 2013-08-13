package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import brooklyn.management.internal.LocalManagementContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.LocationMemento;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class RebindLocationTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext managementContext;
    private TestApplication origApp;
    private MyEntity origE;
    private File mementoDir;
    
    @BeforeMethod
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), managementContext);
        origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
        LocalManagementContext.terminateAll();
    }
    
    @Test
    public void testSetsLocationOnEntities() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("name", "mylocname"));
        origApp.start(ImmutableList.of(origLoc));

        TestApplication newApp = (TestApplication) rebind();
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
        
        TestApplication newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getId(), origLoc.getId());
        assertEquals(newLoc.getDisplayName(), origLoc.getDisplayName());
    }
    
    @Test
    public void testCanCustomizeLocationRebind() throws Exception {
        MyLocationCustomProps origLoc = new MyLocationCustomProps(MutableMap.of("name", "mylocname", "myfield", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        TestApplication newApp = (TestApplication) rebind();
        MyLocationCustomProps newLoc2 = (MyLocationCustomProps) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc2.getId(), origLoc.getId());
        assertEquals(newLoc2.getDisplayName(), origLoc.getDisplayName());
        assertEquals(newLoc2.rebound, true);
        assertEquals(newLoc2.myfield, "myval");
    }
    
    @Test
    public void testRestoresFieldsWithSetFromFlag() throws Exception {
    	MyLocation origLoc = new MyLocation(MutableMap.of("myfield", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        TestApplication newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myfield, "myval");
    }
    
    @Test
    public void testRestoresAtomicLongWithSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("myAtomicLong", "123"));
        origApp.start(ImmutableList.of(origLoc));

        origLoc.myAtomicLong.incrementAndGet();
        assertEquals(origLoc.myAtomicLong.get(), 124L);
        ((EntityInternal)origApp).getManagementContext().getRebindManager().getChangeListener().onChanged(origLoc);
        
        TestApplication newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        // should get _modified_ value, not the one in the config map
        assertEquals(newLoc.myAtomicLong.get(), 124L);
    }
    
    @Test
    public void testIgnoresTransientFieldsNotSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of());
        origLoc.myTransientFieldNotSetFromFlag = "myval";
        origApp.start(ImmutableList.of(origLoc));

        TestApplication newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);

        // transient fields normally not persisted
        assertEquals(newLoc.myTransientFieldNotSetFromFlag, null);
    }
    
    @Test
    public void testIgnoresTransientFieldsSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("myTransientFieldSetFromFlag", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        TestApplication newApp = (TestApplication) rebind();
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myTransientFieldSetFromFlag, null);
    }
    
    @Test
    public void testIgnoresStaticFieldsNotSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of());
        origLoc.myStaticFieldNotSetFromFlag = "myval";
        origApp.start(ImmutableList.of(origLoc));

        RebindTestUtils.waitForPersisted(origApp);
        MyLocation.myStaticFieldNotSetFromFlag = "mynewval";
        TestApplication newApp = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        // static fields normally not persisted (we see new value)
        assertEquals(newLoc.myStaticFieldNotSetFromFlag, "mynewval");
    }
    
    @Test
    public void testIgnoresStaticFieldsSetFromFlag() throws Exception {
        MyLocation origLoc = new MyLocation(MutableMap.of("myStaticFieldSetFromFlag", "myval"));
        origApp.start(ImmutableList.of(origLoc));

        RebindTestUtils.waitForPersisted(origApp);
        MyLocation.myStaticFieldSetFromFlag = "mynewval"; // not auto-checkpointed
        TestApplication newApp = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        MyLocation newLoc = (MyLocation) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.myStaticFieldSetFromFlag, "mynewval");
    }
    
    @Test
    public void testHandlesFieldReferencingOtherLocations() throws Exception {
    	MyLocation origOtherLoc = new MyLocation();
    	MyLocationReffingOthers origLoc = new MyLocationReffingOthers(MutableMap.of("otherLocs", ImmutableList.of(origOtherLoc), "myfield", "myval"));
    	origOtherLoc.setParent(origLoc);
    	
        origApp.start(ImmutableList.of(origLoc));

        Application newApp = rebind();
        MyLocationReffingOthers newLoc = (MyLocationReffingOthers) Iterables.get(newApp.getLocations(), 0);
        
        assertEquals(newLoc.getChildren().size(), 1);
        assertTrue(Iterables.get(newLoc.getChildren(), 0) instanceof MyLocation, "children="+newLoc.getChildren());
        assertEquals(newLoc.otherLocs, ImmutableList.copyOf(newLoc.getChildren()));
        
        // Confirm this didn't override other values (e.g. setting other fields back to their defaults, as was once the case!)
        assertEquals(newLoc.myfield, "myval");
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
    
    public static class MyLocation extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        
        @SetFromFlag
        String myfield;

        @SetFromFlag(defaultVal="1")
        AtomicLong myAtomicLong;

        private final Object dummy = new Object(); // so not serializable
        
        @SetFromFlag
        transient String myTransientFieldSetFromFlag;
        
        transient String myTransientFieldNotSetFromFlag;
        
        @SetFromFlag
        static String myStaticFieldSetFromFlag;
        
        static String myStaticFieldNotSetFromFlag;
        
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
