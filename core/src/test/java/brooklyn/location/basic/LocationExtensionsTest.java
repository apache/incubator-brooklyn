package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;

public class LocationExtensionsTest {

    public static class ConcreteLocation extends AbstractLocation {
        private static final long serialVersionUID = 2407231019435442876L;

        public ConcreteLocation() {
			super();
		}
    }

    public interface MyExtension {
    }
    
    public static class MyExtensionImpl implements MyExtension {
    }
    
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = Entities.newManagementContext();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (mgmt!=null) Entities.destroyAll(mgmt);
    }

    private ConcreteLocation createConcrete() {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(ConcreteLocation.class));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ConcreteLocation createConcrete(Class<?> extensionType, Object extension) {
        return mgmt.getLocationManager().createLocation(LocationSpec.create(ConcreteLocation.class).extension((Class)extensionType, extension));
    }
    
    @Test
    public void testHasExtensionWhenMissing() {
        Location loc = createConcrete();
        assertFalse(loc.hasExtension(MyExtension.class));
    }

    @Test
    public void testWhenExtensionPresent() {
        MyExtension extension = new MyExtensionImpl();
        ConcreteLocation loc = createConcrete();
        loc.addExtension(MyExtension.class, extension);
        
        assertTrue(loc.hasExtension(MyExtension.class));
        assertEquals(loc.getExtension(MyExtension.class), extension);
    }

    @Test
    public void testAddExtensionThroughLocationSpec() {
        MyExtension extension = new MyExtensionImpl();
        Location loc = createConcrete(MyExtension.class, extension);
        
        assertTrue(loc.hasExtension(MyExtension.class));
        assertEquals(loc.getExtension(MyExtension.class), extension);
    }

    @Test
    public void testGetExtensionWhenMissing() {
        Location loc = createConcrete();

        try {
            loc.getExtension(MyExtension.class);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
        
        try {
            loc.getExtension(null);
            fail();
        } catch (NullPointerException e) {
            // success
        }
    }

    @Test
    public void testWhenExtensionDifferent() {
        MyExtension extension = new MyExtensionImpl();
        ConcreteLocation loc = createConcrete();
        loc.addExtension(MyExtension.class, extension);
        
        assertFalse(loc.hasExtension(Object.class));
        
        try {
            loc.getExtension(Object.class);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testAddExtensionIllegally() {
        MyExtension extension = new MyExtensionImpl();
        ConcreteLocation loc = createConcrete();
        
        try {
            loc.addExtension((Class)MyExtension.class, "not an extension");
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
        
        try {
            loc.addExtension(MyExtension.class, null);
            fail();
        } catch (NullPointerException e) {
            // success
        }
        
        try {
            loc.addExtension(null, extension);
            fail();
        } catch (NullPointerException e) {
            // success
        }
    }

    @Test
    public void testAddExtensionThroughLocationSpecIllegally() {
        MyExtension extension = new MyExtensionImpl();
        
        try {
            Location loc = createConcrete(MyExtension.class, "not an extension");
            fail("loc="+loc);
        } catch (IllegalArgumentException e) {
            // success
        }
        
        try {
            Location loc = createConcrete(MyExtension.class, null);
            fail("loc="+loc);
        } catch (NullPointerException e) {
            // success
        }
        
        try {
            Location loc = createConcrete(null, extension);
            fail("loc="+loc);
        } catch (NullPointerException e) {
            // success
        }
    }
}
