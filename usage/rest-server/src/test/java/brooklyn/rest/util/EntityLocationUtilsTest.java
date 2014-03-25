package brooklyn.rest.util;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;

import com.google.common.collect.ImmutableList;

public class EntityLocationUtilsTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(EntityLocationUtilsTest.class);
    
    private Location loc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationRegistry().resolve("localhost");
        ((AbstractLocation)loc).setHostGeoInfo(new HostGeoInfo("localhost", "localhost", 50, 0));
    }
    
    @Test
    public void testCount() {
        @SuppressWarnings("unused")
        SoftwareProcess r1 = app.createAndManageChild(EntitySpec.create(SoftwareProcess.class, RestMockSimpleEntity.class));
        SoftwareProcess r2 = app.createAndManageChild(EntitySpec.create(SoftwareProcess.class, RestMockSimpleEntity.class));
        Entities.start(app, Arrays.<Location>asList(loc));

        Entities.dumpInfo(app);

        log.info("r2loc: "+r2.getLocations());
        log.info("props: "+r2.getLocations().iterator().next().getAllConfig(false));

        Map<Location, Integer> counts = new EntityLocationUtils(mgmt).countLeafEntitiesByLocatedLocations();
        log.info("count: "+counts);
        assertEquals(ImmutableList.copyOf(counts.values()), ImmutableList.of(2), "counts="+counts);
    }
}
