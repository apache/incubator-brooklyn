package brooklyn.location.basic

import static org.testng.Assert.*
import org.testng.annotations.Test

public class AbstractLocationTest {

    private static class ConcreteLocation extends AbstractLocation {
        ConcreteLocation(Map properties = [:]) {
            super(properties)
        }
    }

    @Test
    public void nullNameAndParentLocationIsAcceptable() {
        AbstractLocation location = new ConcreteLocation(name: null, parentLocation: null)
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
        assertEquals child, parent.childLocations.iterator().next()
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
        assertEquals child, parent.childLocations.iterator().next()
    }
}
