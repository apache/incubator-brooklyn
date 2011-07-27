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

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.TimeExtras

public class AbstractGeoDnsServiceTest {
    private static final String MONTEREY_WEST_IP = "208.95.232.123";
    private static final String MONTEREY_EAST_IP = "216.150.144.82";
    private static final double CALIFORNIA_LATITUDE = 37.43472, CALIFORNIA_LONGITUDE = -121.89500;
    private static final double NEW_YORK_LATITUDE = 41.10361, NEW_YORK_LONGITUDE = -73.79583;
    
    private static final Location CALIFORNIA = new GeneralPurposeLocation(
        name: "California", latitude: CALIFORNIA_LATITUDE, longitude: CALIFORNIA_LONGITUDE);
    
    private static final Location NEW_YORK = new GeneralPurposeLocation(
        name: "New York", latitude: NEW_YORK_LATITUDE, longitude: NEW_YORK_LONGITUDE);

    private static final Location CALIFORNIA_MACHINE = new SshMachineLocation(
        name: "California machine", address: MONTEREY_WEST_IP, parentLocation: CALIFORNIA); 
        
    private static final Location NEW_YORK_MACHINE = new SshMachineLocation(
        name: "New York machine", address: MONTEREY_EAST_IP, parentLocation: NEW_YORK); 
    
    
    private AbstractApplication app;
    private DynamicFabric fabric
    private AbstractGeoDnsService geoDns;
    

    @BeforeMethod
    public void setup() {
        def template = { properties -> new TestEntity(properties) }
        app = new AbstractApplication() { };
        fabric = new DynamicFabric(owner:app, newEntity:template);
    }

    @AfterMethod
    public void shutdown() {
        if (fabric != null && fabric.getAttribute(Startable.SERVICE_UP)) {
            EntityStartUtils.stopEntity(fabric)
        }
    }

    
    @Test(enabled=false)
    public void geoInfoOnLocations() {
        DynamicFabric fabric = new DynamicFabric(newEntity:{ properties -> return new TestEntity(properties) }, app)
        DynamicGroup testEntities = new DynamicGroup([:], app, { Entity e -> (e instanceof TestEntity) });
        geoDns = new TestService(app);
        geoDns.setTargetEntityProvider(testEntities);
        
        app.start( [ CALIFORNIA_MACHINE, NEW_YORK_MACHINE ] );
        
        // FIXME: remove this sleep once the location-polling mechanism has been replaced with proper subscriptions
        Thread.sleep(7000);
        
        assertTrue(geoDns.targetHostsByName.containsKey("California machine"));
        assertTrue(geoDns.targetHostsByName.containsKey("New York machine"));
    }
    
    @Test
    public void geoInfoNotFound() {
        // TODO
    }
    
    @Test
    public void emptyGroup() {
        // TODO
    }
    
    
    private class TestService extends AbstractGeoDnsService {
        public Map<String, HostGeoInfo> targetHostsByName = new LinkedHashMap<String, HostGeoInfo>();
        
        public TestService(properties=[:], Entity owner) {
            super(properties, owner);
        }
        
        @Override
        protected void reconfigureService(Set<HostGeoInfo> targetHosts) {
            targetHostsByName.clear();
            for (HostGeoInfo host : targetHosts) {
                if (host != null) targetHostsByName.put(host.displayName, host);
            }
        }
    }
    
}
