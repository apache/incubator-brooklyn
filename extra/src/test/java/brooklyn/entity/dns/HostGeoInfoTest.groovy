package brooklyn.entity.dns;

import static org.testng.AssertJUnit.*
import groovy.lang.MetaClass

import java.util.Map
import java.util.Set

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.trait.Startable
import brooklyn.location.CoordinatesProvider
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.TimeExtras

public class HostGeoInfoTest {
    private static final String IP = "192.168.0.1";
    
    private static final Location DOUBLE_LOCATION = new GeneralPurposeLocation(name: "doubles", latitude: 50.0d, longitude: 0.0d);
    private static final Location BIGDECIMAL_LOCATION = new GeneralPurposeLocation( name: "bigdecimals", latitude: 50.0, longitude: 0.0);
    private static final Location MIXED_LOCATION = new GeneralPurposeLocation(name: "mixed", latitude: 50.0d, longitude: 0.0);
    
    private static final Location DOUBLE_CHILD = new SshMachineLocation(name: "double-child", address: IP, parentLocation: DOUBLE_LOCATION);
    private static final Location BIGDECIMAL_CHILD = new SshMachineLocation(name: "bigdecimal-child", address: IP, parentLocation: BIGDECIMAL_LOCATION);
    private static final Location MIXED_CHILD = new SshMachineLocation(name: "mixed-child", address: IP, parentLocation: MIXED_LOCATION);

        
    @Test
    public void testDoubleCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(DOUBLE_CHILD);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
    @Test
    public void testBigdecimalCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(BIGDECIMAL_CHILD);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
    @Test
    public void testMixedCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(MIXED_CHILD);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
    @Test
    public void testMissingCoordinates() {
    }
    
}
