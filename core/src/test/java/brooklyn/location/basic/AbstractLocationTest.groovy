package brooklyn.location.basic

import static org.testng.Assert.*
import org.testng.annotations.Test
import brooklyn.location.Location

public class AbstractLocationTest {

    private static class ConcreteLocation extends AbstractLocation {
        ConcreteLocation(String name, Location parentLocation) {
            super(name, parentLocation)
        }
    }

    @Test
    public void nullNameAndParentLocationIsAcceptable() {
        AbstractLocation location = new ConcreteLocation(null, null)
    }

    @Test
    public void queryingNameReturnsNameGivenInConstructor() {
        String name = "Outer Mongolia"
        AbstractLocation location = new ConcreteLocation("Outer Mongolia", null)
        assertEquals name, location.name
    }

    @Test
    public void queryingParentLocationReturnsLocationGivenInConstructor() {
        AbstractLocation parent = new ConcreteLocation("Middle Earth", null)
        AbstractLocation child = new ConcreteLocation("The Shire", parent)
        assertEquals parent, child.parentLocation
    }

    @Test
    public void queryingChildrenOfParentReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation("Middle Earth", null)
        AbstractLocation child = new ConcreteLocation("The Shire", parent)
        assertEquals 1, parent.childLocations.size()
        assertEquals child, parent.childLocations.iterator().next()
    }

    @Test
    public void queryParentLocationAfterSetParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation("Middle Earth", null)
        AbstractLocation child = new ConcreteLocation("The Shire", null)
        child.parentLocation = parent
        assertEquals parent, child.parentLocation
    }

    @Test
    public void queryingChildrenOfParentAfterSetParentLocationReturnsExpectedLocation() {
        AbstractLocation parent = new ConcreteLocation("Middle Earth", null)
        AbstractLocation child = new ConcreteLocation("The Shire", null)
        child.parentLocation = parent
        assertEquals 1, parent.childLocations.size()
        assertEquals child, parent.childLocations.iterator().next()
    }
}
