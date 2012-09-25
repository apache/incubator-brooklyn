package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

public class AbstractLocationTest {

    private static class ConcreteLocation extends AbstractLocation {
		@SetFromFlag(defaultVal="mydefault")
        String myfield;

        public ConcreteLocation() {
			super();
		}

        public ConcreteLocation(Map properties) {
            super(properties);
        }
    }

    @Test
    public void testEquals() {
        AbstractLocation l1 = new ConcreteLocation(MutableMap.of("id", "1", "name", "bob"));
        AbstractLocation l2 = new ConcreteLocation(MutableMap.of("id", "1", "name", "frank"));
        AbstractLocation l3 = new ConcreteLocation(MutableMap.of("id", "2", "name", "frank"));
        assertEquals(l1, l2);
        assertNotEquals(l2, l3);
    }

    @Test
    public void nullNameAndParentLocationIsAcceptable() {
        AbstractLocation location = new ConcreteLocation(MutableMap.of("name", null, "parentLocation", null));
        assertEquals(location.getName(), null);
        assertEquals(location.getParentLocation(), null);
    }

    @Test
    public void testSettingParentLocation() {
        AbstractLocation location = new ConcreteLocation();
        AbstractLocation locationSub = new ConcreteLocation();
        locationSub.setParentLocation(location);
        
        assertEquals(ImmutableList.copyOf(location.getChildLocations()), ImmutableList.of(locationSub));
        assertEquals(locationSub.getParentLocation(), location);
    }

    @Test
    public void testClearingParentLocation() {
        AbstractLocation location = new ConcreteLocation();
        AbstractLocation locationSub = new ConcreteLocation();
        locationSub.setParentLocation(location);
        
        locationSub.setParentLocation(null);
        assertEquals(ImmutableList.copyOf(location.getChildLocations()), Collections.emptyList());
        assertEquals(locationSub.getParentLocation(), null);
    }
    
    @Test
    public void testContainsLocation() {
        AbstractLocation location = new ConcreteLocation();
        AbstractLocation locationSub = new ConcreteLocation();
        locationSub.setParentLocation(location);
        
        assertTrue(location.containsLocation(location));
        assertTrue(location.containsLocation(locationSub));
        assertFalse(locationSub.containsLocation(location));
    }


    @Test
    public void queryingNameReturnsNameGivenInConstructor() {
        String name = "Outer Mongolia";
        AbstractLocation location = new ConcreteLocation(MutableMap.of("name", "Outer Mongolia"));
        assertEquals(location.getName(), name);;
    }

    @Test
    public void queryingParentLocationReturnsLocationGivenInConstructor() {
        AbstractLocation parent = new ConcreteLocation(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = new ConcreteLocation(MutableMap.of("name", "The Shire", "parentLocation", parent));
        assertEquals(child.getParentLocation(), parent);
    }

    @Test
    public void queryingChildrenOfParentReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = new ConcreteLocation(MutableMap.of("name", "The Shire", "parentLocation", parent));
        assertEquals(ImmutableList.copyOf(parent.getChildLocations()), ImmutableList.of(child));
    }

    @Test
    public void queryParentLocationAfterSetParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = new ConcreteLocation(MutableMap.of("name", "The Shire"));
        child.setParentLocation(parent);
        assertEquals(child.getParentLocation(), parent);
    }

    @Test
    public void queryingChildrenOfParentAfterSetParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(MutableMap.of("name", "Middle Earth"));
        AbstractLocation child = new ConcreteLocation(MutableMap.of("name", "The Shire"));
        child.setParentLocation(parent);
        assertEquals(ImmutableList.copyOf(parent.getChildLocations()), ImmutableList.of(child));
    }
	
    @Test
    public void testFieldSetFromFlag() {
    	ConcreteLocation loc = new ConcreteLocation(MutableMap.of("myfield", "myval"));
        assertEquals(loc.myfield, "myval");
    }
    
    @Test
    public void testFieldSetFromFlagUsesDefault() {
        ConcreteLocation loc = new ConcreteLocation();
        assertEquals(loc.myfield, "mydefault");
    }
	
//	@Test
//	public void testAddChild() {
//		AbstractLocation parent = new ConcreteLocation(id: "1");
//		AbstractLocation child = new ConcreteLocation(id: "2");
//		parent.addChildLocation(child);
//	}
}
