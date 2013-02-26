package brooklyn.entity.basic;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestEntityImpl;

public class EntityLocationsTest {

    @Test
    public void testDuplicateLocation() {
        TestEntityImpl e = new TestEntityImpl();
        Location l = new SimulatedLocation();
        e.addLocations(Arrays.asList(l, l));
        e.addLocations(Arrays.asList(l, l));
        Assert.assertEquals(e.getLocations().size(), 1);
    }
    
}
