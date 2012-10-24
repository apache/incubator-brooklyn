package brooklyn.extras.openshift;
import brooklyn.config.BrooklynProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.basic.LocationRegistry;

public class LocationResolvesTest {

    @Test
    public void testOpenshiftLocationResolves() {
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        Assert.assertNotNull(new LocationRegistry(properties).resolve("openshift"));
    }
    
}
