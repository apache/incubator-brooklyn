package brooklyn.extras.cloudfoundry;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.AddressableLocation;
import brooklyn.location.basic.LocationRegistry;

public class LocationResolvesTest {
    
    public static final Logger log = LoggerFactory.getLogger(LocationResolvesTest.class);

    @Test
    public void testCloudFoundryLocationResolves() {
        //NB: if this test fails, make sure src/main/resources is on the classpath
        //and META-INF/services/brooklyn.location.LocationResolver isn't being clobbered in a poorly shaded jar
        Assert.assertNotNull(new LocationRegistry().resolve("cloudfoundry"));
    }
    
    @Test(groups="Integration")
    public void testLocationAddress() {
        log.info("lookup CF address");
        InetAddress address = ((AddressableLocation)new LocationRegistry().resolve("cloudfoundry")).getAddress();
        log.info("done lookup of CF address, got "+address);
        Assert.assertEquals("api.cloudfoundry.com", address.getHostName());
    }

}
