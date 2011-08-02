package brooklyn.location.basic

import static org.testng.AssertJUnit.*

import org.testng.annotations.Test

public class AbstractLocationTest {

    private static class ConcreteLocation extends AbstractLocation {
        ConcreteLocation(Map properties = [:]) {
            super(properties)
        }
    }

    @Test
    public void testEquals() {
        AbstractLocation l1 = new ConcreteLocation(id: "3", name: "bob");
        AbstractLocation l2 = new ConcreteLocation(id: "3", name: "frank");
        AbstractLocation l3 = new ConcreteLocation(id: "10", name: "frank");
        assertEquals(l1, l2);
        assertNotEquals(l2, l3);
    }

    @Test
    public void nullNameAndParentLocationIsAcceptable() {
        AbstractLocation location = new ConcreteLocation(name: null, parentLocation: null)
    }

    @Test
    public void testSettingParentLocation() {
        AbstractLocation location = new ConcreteLocation()
        AbstractLocation locationSub = new ConcreteLocation()
        locationSub.setParentLocation(location)
        
        assertEquals(location.getChildLocations() as List, [locationSub])
        assertEquals(locationSub.getParentLocation(), location)
    }

    @Test
    public void testClearingParentLocation() {
        AbstractLocation location = new ConcreteLocation()
        AbstractLocation locationSub = new ConcreteLocation()
        locationSub.setParentLocation(location)
        
        locationSub.setParentLocation(null)
        assertEquals(location.getChildLocations() as List, [])
        assertEquals(locationSub.getParentLocation(), null)
    }
    
    @Test
    public void testContainsLocation() {
        AbstractLocation location = new ConcreteLocation()
        AbstractLocation locationSub = new ConcreteLocation()
        locationSub.setParentLocation(location)
        
        assertTrue(location.containsLocation(location))
        assertTrue(location.containsLocation(locationSub))
        assertFalse(locationSub.containsLocation(location))
    }


    @Test
    public void queryingNameReturnsNameGivenInConstructor() {
        String name = "Outer Mongolia"
        AbstractLocation location = new ConcreteLocation(name: "Outer Mongolia")
        assertEquals name, location.name
    }

    @Test
    public void queryingParentLocationReturnsLocationGivenInConstructor() {
        AbstractLocation parent = new ConcreteLocation(name: "Middle Earth")
        AbstractLocation child = new ConcreteLocation(name: "The Shire", parentLocation: parent)
        assertEquals parent, child.parentLocation
    }

    @Test
    public void queryingChildrenOfParentReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(name: "Middle Earth")
        AbstractLocation child = new ConcreteLocation(name: "The Shire", parentLocation: parent)
        assertEquals 1, parent.childLocations.size()
        assertEquals child, parent.childLocations.find { true }
    }

    @Test
    public void queryParentLocationAfterSetParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(name: "Middle Earth")
        AbstractLocation child = new ConcreteLocation(name: "The Shire")
        child.parentLocation = parent
        assertEquals parent, child.parentLocation
    }

    @Test
    public void queryingChildrenOfParentAfterSetParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation(name: "Middle Earth")
        AbstractLocation child = new ConcreteLocation(name: "The Shire")
        child.parentLocation = parent
        assertEquals 1, parent.childLocations.size()
        assertEquals child, parent.childLocations.find { true }
    }
}
