package brooklyn.rest.util;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.management.ManagementContext;
import brooklyn.rest.testing.mocks.RestMockApp;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;

public class EntityLocationUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(EntityLocationUtilsTest.class);
    
    @Test
    public void testCount() {
        RestMockApp app = new RestMockApp();
        @SuppressWarnings("unused")
        RestMockSimpleEntity r1 = new RestMockSimpleEntity(app);
        RestMockSimpleEntity r2 = new RestMockSimpleEntity(app);
        ManagementContext mgmt = Entities.startManagement(app);
        try {
        
            AbstractLocation l0 = new LocalhostMachineProvisioningLocation();
            l0.setHostGeoInfo(new HostGeoInfo("localhost", "localhost", 50, 0));

            Entities.start(app, Arrays.<Location>asList(l0));

            Entities.dumpInfo(app);

            log.info("r2loc: "+r2.getLocations());
            log.info("props: "+r2.getLocations().iterator().next().getAllConfig(false));

            Map<Location, Integer> counts = new EntityLocationUtils(mgmt).countLeafEntitiesByLocatedLocations();
            log.info("count: "+counts);
            Assert.assertEquals(counts.size(), 1);
            Assert.assertEquals((int)counts.values().iterator().next(), 2);

        } finally { Entities.destroyAll(mgmt); }
    }
    
}
