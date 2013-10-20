package brooklyn.entity.group.zoneaware;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.zoneaware.ProportionalZoneFailureDetector;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.time.Duration;

import com.google.common.base.Ticker;

public class ProportionalZoneFailureDetectorTest {

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity entity1;
    private SimulatedLocation loc1;
    private SimulatedLocation loc2;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        managementContext = app.getManagementContext();
        loc1 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        loc2 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        entity1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testRespectsMin() throws Exception {
        ProportionalZoneFailureDetector detector = new ProportionalZoneFailureDetector(2, Duration.ONE_HOUR, 0.9);
        
        detector.onStartupFailure(loc1, entity1, new Throwable("simulated failure"));
        assertFalse(detector.hasFailed(loc1));
        assertFalse(detector.hasFailed(loc2));
        
        detector.onStartupFailure(loc1, entity1, new Throwable("simulated failure"));
        assertTrue(detector.hasFailed(loc1));
        assertFalse(detector.hasFailed(loc2));
    }
    
    @Test
    public void testRespectsProportion() throws Exception {
        ProportionalZoneFailureDetector detector = new ProportionalZoneFailureDetector(2, Duration.ONE_HOUR, 0.9);

        for (int i = 0; i < 9; i++) {
            detector.onStartupFailure(loc1, entity1, new Throwable("simulated failure"));
        }
        assertTrue(detector.hasFailed(loc1));

        detector.onStartupSuccess(loc1, entity1);
        assertTrue(detector.hasFailed(loc1));

        detector.onStartupSuccess(loc1, entity1);
        assertFalse(detector.hasFailed(loc1));
    }
    
    @Test
    public void testRespectsTime() throws Exception {
        final long startTime = System.nanoTime();
        final AtomicLong currentTime = new AtomicLong(startTime);
        Ticker ticker = new Ticker() {
            @Override public long read() {
                return currentTime.get();
            }
        };
        ProportionalZoneFailureDetector detector = new ProportionalZoneFailureDetector(2, Duration.ONE_HOUR, 0.9, ticker);

        for (int i = 0; i < 2; i++) {
            detector.onStartupFailure(loc1, entity1, new Throwable("simulated failure"));
        }
        assertTrue(detector.hasFailed(loc1));
        
        currentTime.set(startTime + TimeUnit.MILLISECONDS.toNanos(1000*60*60 - 1));
        assertTrue(detector.hasFailed(loc1));

        currentTime.set(startTime + TimeUnit.MILLISECONDS.toNanos(1000*60*60 + 1));
        assertFalse(detector.hasFailed(loc1));
    }
    
    @Test
    public void testSeparatelyTracksLocations() throws Exception {
        ProportionalZoneFailureDetector detector = new ProportionalZoneFailureDetector(2, Duration.ONE_HOUR, 0.9);

        for (int i = 0; i < 2; i++) {
            detector.onStartupFailure(loc1, entity1, new Throwable("simulated failure"));
        }
        for (int i = 0; i < 2; i++) {
            detector.onStartupFailure(loc2, entity1, new Throwable("simulated failure"));
        }
        assertTrue(detector.hasFailed(loc1));
        assertTrue(detector.hasFailed(loc2));

        detector.onStartupSuccess(loc1, entity1);
        assertFalse(detector.hasFailed(loc1));
        assertTrue(detector.hasFailed(loc2));
    }
}
