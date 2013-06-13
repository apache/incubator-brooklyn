package brooklyn.entity.dns;

import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.Repeater;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class AbstractGeoDnsServiceTest {
    public static final Logger log = LoggerFactory.getLogger(AbstractGeoDnsServiceTest.class);

    private static final String WEST_IP = "208.95.232.123";
    private static final String EAST_IP = "216.150.144.82";
    private static final double WEST_LATITUDE = 37.43472, WEST_LONGITUDE = -121.89500;
    private static final double EAST_LATITUDE = 41.10361, EAST_LONGITUDE = -73.79583;
    
    private static SimulatedLocation newSimulatedLocation(String name, double lat, double lon) {
        return new SimulatedLocation(MutableMap.of("name", name, "latitude", lat, "longitude", lon));
    }
    
    private static Location newSshMachineLocation(String name, String address, Location parent) {
        return new SshMachineLocation(MutableMap.of("name", name, "address", address, "parentLocation", parent)); 
    }
    
    private static Location newSshMachineLocation(String name, String address, Location parent, double lat, double lon) {
        return new SshMachineLocation(MutableMap.of("name", name, "address", address, "parentLocation", parent, "latitude", lat, "longitude", lon)); 
    }
    
    private static final Location WEST_PARENT = newSimulatedLocation("West parent", WEST_LATITUDE, WEST_LONGITUDE);
    
    private static final Location WEST_CHILD = newSshMachineLocation("West child", WEST_IP, WEST_PARENT);
    private static final Location WEST_CHILD_WITH_LOCATION = newSshMachineLocation("West child with location", WEST_IP, WEST_PARENT, WEST_LATITUDE, WEST_LONGITUDE); 
    
    private static final Location EAST_PARENT = newSimulatedLocation("East parent", EAST_LATITUDE, EAST_LONGITUDE);
    private static final Location EAST_CHILD = newSshMachineLocation("East child", EAST_IP, EAST_PARENT); 
    private static final Location EAST_CHILD_WITH_LOCATION = newSshMachineLocation("East child with location", EAST_IP, EAST_PARENT, EAST_LATITUDE, EAST_LONGITUDE); 
    
    private TestApplication app;
    private DynamicFabric fabric;
    private DynamicGroup testEntities;
    private GeoDnsTestService geoDns;
    

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        fabric = app.createAndManageChild(EntitySpecs.spec(DynamicFabric.class)
            .configure(DynamicFabric.MEMBER_SPEC, EntitySpecs.spec(TestEntity.class)));
        
        testEntities = app.createAndManageChild(EntitySpecs.spec(DynamicGroup.class)
            .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class)));
        geoDns = app.createAndManageChild(EntitySpecs.spec(GeoDnsTestService.class)
                .configure(GeoDnsTestService.POLL_PERIOD, 10L));
        geoDns.setTargetEntityProvider(testEntities);
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroy(app);
    }

    
    @Test
    public void testGeoInfoOnLocation() {
        app.start( ImmutableList.of(WEST_CHILD_WITH_LOCATION, EAST_CHILD_WITH_LOCATION) );
        
        waitForTargetHosts(geoDns);
        assertTrue(geoDns.getTargetHostsByName().containsKey("West child with location"), "targets="+geoDns.getTargetHostsByName());
        assertTrue(geoDns.getTargetHostsByName().containsKey("East child with location"), "targets="+geoDns.getTargetHostsByName());
    }
    
    @Test
    public void testGeoInfoOnParentLocation() {
        app.start( ImmutableList.of(WEST_CHILD, EAST_CHILD) );
        
        waitForTargetHosts(geoDns);
        assertTrue(geoDns.getTargetHostsByName().containsKey("West child"), "targets="+geoDns.getTargetHostsByName());
        assertTrue(geoDns.getTargetHostsByName().containsKey("East child"), "targets="+geoDns.getTargetHostsByName());
    }
    
    //TODO
//    @Test
//    public void testMissingGeoInfo() {
//    }
//    
//    @Test
//    public void testEmptyGroup() {
//    }
    
    private static void waitForTargetHosts(final GeoDnsTestService service) {
        new Repeater("Wait for target hosts")
            .repeat()
            .every(500, TimeUnit.MILLISECONDS)
            .until(new Callable<Boolean>() {
                public Boolean call() {
                    return service.getTargetHostsByName().size() == 2;
                }})
            .limitIterationsTo(20)
            .run();
    }
    
    @ImplementedBy(GeoDnsTestServiceImpl.class)
    public static interface GeoDnsTestService extends AbstractGeoDnsService {
        public Map<String, HostGeoInfo> getTargetHostsByName();
    }
    
    public static class GeoDnsTestServiceImpl extends AbstractGeoDnsServiceImpl implements GeoDnsTestService {
        public Map<String, HostGeoInfo> targetHostsByName = new LinkedHashMap<String, HostGeoInfo>();

        public GeoDnsTestServiceImpl() {
        }

        @Override
        public Map<String, HostGeoInfo> getTargetHostsByName() {
            return targetHostsByName;
        }
        
        @Override
        protected boolean addTargetHost(Entity e) {
            //ignore geo lookup, override parent menu
            log.info("TestService adding target host {}", e);
            if (e.getLocations().isEmpty()) {
                return false;
            }
            Location l = Iterables.getOnlyElement(e.getLocations());
            HostGeoInfo geoInfo = new HostGeoInfo("127.0.0.1", l.getName(), 
                (Double) l.findLocationProperty("latitude"), (Double) l.findLocationProperty("longitude"));
            targetHosts.put(e, geoInfo);
            return true;
        }
        
        @Override
        protected void reconfigureService(Collection<HostGeoInfo> targetHosts) {
            targetHostsByName.clear();
            for (HostGeoInfo host : targetHosts) {
                if (host != null) targetHostsByName.put(host.displayName, host);
            }
        }

        @Override
        public String getHostname() {
            return "localhost";
        }
    }
    
}
