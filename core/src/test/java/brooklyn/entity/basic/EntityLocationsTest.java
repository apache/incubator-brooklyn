package brooklyn.entity.basic;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;

public class EntityLocationsTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testDuplicateLocationOnlyAddedOnce() {
        Location l = new SimulatedLocation();
        app.addLocations(Arrays.asList(l, l));
        app.addLocations(Arrays.asList(l, l));
        Assert.assertEquals(app.getLocations().size(), 1);
    }
    
}
