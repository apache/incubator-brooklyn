package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

public class AbstractLocationTest {

    public static class ConcreteLocation extends AbstractLocation {
        private static final long serialVersionUID = 3954199300889119970L;
        @SetFromFlag(defaultVal="mydefault")
        String myfield;

        public ConcreteLocation() {
			super();
		}

        public ConcreteLocation(Map<?,?> properties) {
            super(properties);
        }
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
        return createConcrete(MutableMap.<String,Object>of());
    }
    private ConcreteLocation createConcrete(Map<String,?> flags) {
        return mgmt.getLocationManager().createLocation( LocationSpec.create(ConcreteLocation.class).configure(flags) );
    }
    
    @Test
    public void testEquals() {
        AbstractLocation l1 = createConcrete(MutableMap.of("id", "1", "name", "bob"));
        AbstractLocation l2 = createConcrete(MutableMap.of("id", "1", "name", "frank"));
        AbstractLocation l3 = createConcrete(MutableMap.of("id", "2", "name", "frank"));
        assertEquals(l1, l2);
        assertNotEquals(l2, l3);
    }

    @Test
    public void nullNameAndParentLocationIsAcceptable() {
        AbstractLocation location = createConcrete(MutableMap.of("name", null, "parentLocation", null));
        assertEquals(location.getDisplayName(), null);
        assertEquals(location.getParent(), null);
    }

    @Test
    public void testSettingParentLocation() {
        AbstractLocation location = createConcrete();
        AbstractLocation locationSub = createConcrete();
        locationSub.setParent(location);
        
        assertEquals(ImmutableList.copyOf(location.getChildren()), ImmutableList.of(locationSub));
        assertEquals(locationSub.getParent(), location);
    }

    @Test
    public void testClearingParentLocation() {
        AbstractLocation location = createConcrete();
        AbstractLocation locationSub = createConcrete();
        locationSub.setParent(location);
        
        locationSub.setParent(null);
        assertEquals(ImmutableList.copyOf(location.getChildren()), Collections.emptyList());
        assertEquals(locationSub.getParent(), null);
    }
    
    @Test
    public void testContainsLocation() {
        AbstractLocation location = createConcrete();
        AbstractLocation locationSub = createConcrete();
        locationSub.setParent(location);
        
        assertTrue(location.containsLocation(location));
        assertTrue(location.containsLocation(locationSub));
        assertFalse(locationSub.containsLocation(location));
    }


    @Test
    public void queryingNameReturnsNameGivenInConstructor() {
        String name = "Outer Mongolia";
        AbstractLocation location = createConcrete(MutableMap.of("name", "Outer Mongolia"));
        assertEquals(location.getDisplayName(), name);;
    }

    @Test
    public void constructorParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = createConcrete(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = createConcrete(MutableMap.of("name", "The Shire", "parentLocation", parent));
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }

    @Test
    public void setParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = createConcrete(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = createConcrete(MutableMap.of("name", "The Shire"));
        child.setParent(parent);
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }
    
    @Test
    public void testAddChildToParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = createConcrete(MutableMap.of("id", "1"));
        AbstractLocation child = createConcrete(MutableMap.of("id", "2"));
        parent.addChild(child);
        assertEquals(child.getParent(), parent);
        assertEquals(ImmutableList.copyOf(parent.getChildren()), ImmutableList.of(child));
    }

    @Test
    public void testFieldSetFromFlag() {
    	ConcreteLocation loc = createConcrete(MutableMap.of("myfield", "myval"));
        assertEquals(loc.myfield, "myval");
    }
    
    @Test
    public void testFieldSetFromFlagUsesDefault() {
        ConcreteLocation loc = createConcrete();
        assertEquals(loc.myfield, "mydefault");
    }
    
}
