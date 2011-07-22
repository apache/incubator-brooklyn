package brooklyn.entity.dns;

import static org.testng.AssertJUnit.*
import groovy.lang.MetaClass
import groovy.transform.InheritConstructors

import java.util.Collection
import java.util.Map
import java.util.Set
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.trait.Startable
import brooklyn.location.CoordinatesProvider
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.TimeExtras

public class AbstractGeoDnsServiceTest {
    private static final Logger log = LoggerFactory.getLogger(AbstractGeoDnsServiceTest.class)
    static { TimeExtras.init() }

    private static final String MONTEREY_WEST_IP = "208.95.232.123";
    private static final String MONTEREY_EAST_IP = "216.150.144.82";
    private static final Map CALIFORNIA_COORDS = [ 'latitude' : 37.43472, 'longitude' : -121.89500 ];
    private static final Map NEW_YORK_COORDS = [ 'latitude' : 41.10361, 'longitude' : -73.79583 ];
    
    private static final Location CALIFORNIA = new GeoLocation(
        name: "California",
        latitude: CALIFORNIA_COORDS.latitude,
        longitude: CALIFORNIA_COORDS.longitude);
    
    private static final Location NEW_YORK = new GeoLocation(
        name: "New York",
        latitude: NEW_YORK_COORDS.latitude,
        longitude: NEW_YORK_COORDS.longitude);

    private static final Location CALIFORNIA_MACHINE = new SshMachineLocation(
        name: "California machine",
        address: MONTEREY_WEST_IP,
        parentLocation: CALIFORNIA); 
        
    private static final Location NEW_YORK_MACHINE = new SshMachineLocation(
        name: "New York machine",
        address: MONTEREY_EAST_IP,
        parentLocation: NEW_YORK); 
    
    private Application app;
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

    @Test
    public void geoInfoOnLocations() {
        DynamicFabric fabric = new DynamicFabric(newEntity:{ properties -> return new TestEntity(properties) }, app)
        fabric.start( [ CALIFORNIA_MACHINE, NEW_YORK_MACHINE ] );
        
        DynamicGroup testEntities = new DynamicGroup([:], app, { Entity e -> (e instanceof TestEntity) });
        testEntities.rescanEntities();
        geoDns = new TestService(app);
        geoDns.setGroup(testEntities);
        
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
    };


    @InheritConstructors
    private static class TestEntity extends AbstractEntity implements Startable {
        AtomicInteger counter = new AtomicInteger(0)
        
        void start(Collection<? extends Location> locs) {
            log.trace "Start $this"; 
            counter.incrementAndGet(); 
            locations.addAll(locs)
        }
        void stop() { 
            log.trace "Stop"; 
            counter.decrementAndGet()
        }
        void restart() {
        }
        @Override String toString() {
            return "TestEntity["+id[-8..-1]+"]"
        }
    }
    
    
    private static class GeoLocation extends GeneralPurposeLocation implements CoordinatesProvider {
        private final double latitude;
        private final double longitude;
        
        public GeoLocation(Map properties = [:]) {
            super(properties);
            latitude = properties['latitude'];
            longitude = properties['longitude'];
        }
        
        double getLatitude() { return latitude; }
        double getLongitude() { return longitude; }
    }
    
}
