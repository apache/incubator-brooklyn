package brooklyn.entity.brooklyn

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpec
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

class BrooklynMetricsTest {

    private static final long TIMEOUT_MS = 2*1000;
    
    TestApplication app
    SimulatedLocation loc
    BrooklynMetrics brooklynMetrics
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        loc = new SimulatedLocation()
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        brooklynMetrics = app.createAndManageChild(EntitySpec.create(BrooklynMetrics.class).configure("updatePeriod", 10L));
        Entities.manage(brooklynMetrics);
    }
    
    @Test
    public void testInitialBrooklynMetrics() {
        app.start([loc])

        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), 1)
            assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_TASKS_SUBMITTED) > 0)
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_INCOMPLETE_TASKS), 0)
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_ACTIVE_TASKS), 0)
            assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_PUBLISHED) > 0)
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_DELIVERED), 0)
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_SUBSCRIPTIONS), 0)
        }
    }
    
    @Test
    public void testBrooklynMetricsIncremented() {
        TestEntity e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start([loc])

        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), 2) // for app and testEntity's start
        }

        // Note if attribute has not yet been set, the value returned could be null
        long effsInvoked = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EFFECTORS_INVOKED, 0);
        long tasksSubmitted = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_TASKS_SUBMITTED, 0);
        long eventsPublished = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EVENTS_PUBLISHED, 0);
        long eventsDelivered = getAttribute(brooklynMetrics, BrooklynMetrics.TOTAL_EVENTS_DELIVERED, 0);
        long subscriptions = getAttribute(brooklynMetrics, BrooklynMetrics.NUM_SUBSCRIPTIONS, 0);

        // Invoking an effector increments effector/task count
        e.myEffector()
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EFFECTORS_INVOKED), effsInvoked+1)
            assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_TASKS_SUBMITTED) > tasksSubmitted)
        }
        
        // Setting attribute causes event to be published and delivered to the subscriber
        // Note that the brooklyn metrics entity itself is also publishing sensors
        app.subscribe(e, TestEntity.SEQUENCE, {} as SensorEventListener)
        e.setAttribute(TestEntity.SEQUENCE, 1)
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_PUBLISHED) > eventsPublished)
            assertTrue(brooklynMetrics.getAttribute(BrooklynMetrics.TOTAL_EVENTS_DELIVERED) > eventsDelivered)
            assertEquals(brooklynMetrics.getAttribute(BrooklynMetrics.NUM_SUBSCRIPTIONS), 1)
        }
    }
    
    private long getAttribute(Entity entity, AttributeSensor<Long> attribute, long defaultVal) {
        Long result = entity.getAttribute(attribute);
        return (result != null) ? result : defaultVal;
    }
}
