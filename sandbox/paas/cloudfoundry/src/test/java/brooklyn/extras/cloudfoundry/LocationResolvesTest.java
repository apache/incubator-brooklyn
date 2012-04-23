package brooklyn.extras.cloudfoundry;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.basic.LocationRegistry;

public class LocationResolvesTest {

    @Test
    public void testCloudFoundryLocationResolves() {
        Assert.assertNotNull(new LocationRegistry().resolve("cloudfoundry"));
    }
    
}
