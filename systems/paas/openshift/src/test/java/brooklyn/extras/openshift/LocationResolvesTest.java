package brooklyn.extras.openshift;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.basic.LocationRegistry;

public class LocationResolvesTest {

    @Test
    public void testOpenshiftLocationResolves() {
        Assert.assertNotNull(new LocationRegistry().resolve("openshift"));
    }
    
}
