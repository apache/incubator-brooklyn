package brooklyn.location.geo;

import java.net.InetAddress;

import org.testng.Assert;
import org.testng.annotations.Test;

public class GeoBytesHostGeoLookupIntegrationTest {

    @Test(groups = "Integration")
    public void testLookupGeobytesDotCom() throws Exception {
        HostGeoInfo geo = new GeoBytesHostGeoLookup().getHostGeoInfo(InetAddress.getByName("geobytes.com"));
        Assert.assertEquals(geo.displayName, "Baltimore (US)");
        Assert.assertEquals(geo.latitude, 39.2894, 0.1);
        Assert.assertEquals(geo.longitude, -76.6384, 0.1);
    }
    
}
