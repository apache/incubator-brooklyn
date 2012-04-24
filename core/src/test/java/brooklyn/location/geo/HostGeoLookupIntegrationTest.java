package brooklyn.location.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;

public class HostGeoLookupIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(HostGeoLookupIntegrationTest.class);
    
    @Test(groups = "Integration")
    public void testLocalhostGetsLocation() {
        SshMachineLocation l = new LocalhostMachineProvisioningLocation().obtain();
        HostGeoInfo geo = HostGeoInfo.fromLocation(l);
        log.info("localhost is in "+geo);
        Assert.assertNotNull(geo, "couldn't load data; must be online and with credit with the HostGeoLookup impl (e.g. GeoBytes)");
        Assert.assertTrue(-90 <= geo.latitude && geo.latitude <= 90); 
    }
    
}
