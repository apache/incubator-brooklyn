package brooklyn.policy.resizing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.List
import java.util.Map
import java.util.concurrent.atomic.AtomicInteger

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.trait.Resizable
import brooklyn.event.basic.BasicNotificationSensor
import brooklyn.test.entity.TestCluster
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.ImmutableMap

class ResizingPolicyTest {
    
    /**
     * Test class for providing a Resizable LocallyManagedEntity for policy testing
     * It is hooked up to a TestCluster that can be used to make assertions against
     */
    public class LocallyResizableEntity extends LocallyManagedEntity implements Resizable {
        List<Integer> sizes = []
        TestCluster cluster
        public LocallyResizableEntity (TestCluster tc) { this.cluster = tc }
        Integer resize(Integer newSize) { Thread.sleep(resizeSleepTime); sizes.add(newSize); cluster.size = newSize }
        Integer getCurrentSize() { return cluster.size }
        String toString() { return getDisplayName() }
    }
    
    
    private static long TIMEOUT_MS = 10000
    private static long SHORT_WAIT_MS = 250
    private static long OVERHEAD_DURATION_MS = 250
    private static long EARLY_RETURN_MS = 10
    
    ResizingPolicy policy
    TestCluster cluster
    LocallyResizableEntity resizable
    long resizeSleepTime
    static { TimeExtras.init() }
    
    
    @BeforeMethod()
    public void before() {
        resizeSleepTime = 0
        policy = new ResizingPolicy([:])
        cluster = new TestCluster(1)
        resizable = new LocallyResizableEntity(cluster)
        resizable.addPolicy(policy)
    }

    @Test
    public void testShrinkColdPool() {
        resizable.resize(4)
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 30L, 4*10L, 4*20L))
        
        // expect pool to shrink to 3 (i.e. maximum to have >= 40 per container)
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 3) }
    }
    
    @Test
    public void testShrinkColdPoolRoundsUpDesiredNumberOfContainers() {
        resizable.resize(4)
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 1L, 4*10L, 4*20L))
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
    }
    
    @Test
    public void testGrowHotPool() {
        resizable.resize(2)
        resizable.emit(ResizingPolicy.POOL_HOT, message(2, 41L, 2*10L, 2*20L))
        
        // expect pool to grow to 3 (i.e. minimum to have <= 80 per container)
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 3) }
    }
    
    @Test
    public void testNeverShrinkBelowMinimum() {
        resizable.removePolicy(policy)
        policy = new ResizingPolicy([minPoolSize:2])
        resizable.addPolicy(policy)
        
        resizable.resize(4)
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 0L, 4*10L, 4*20L))
        
        // expect pool to shrink only to the minimum
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 2) }
    }
    
    @Test
    public void testNeverGrowAboveMaximmum() {
        resizable.removePolicy(policy)
        policy = new ResizingPolicy([maxPoolSize:5])
        resizable.addPolicy(policy)
        
        resizable.resize(4)
        resizable.emit(ResizingPolicy.POOL_HOT, message(4, 1000000L, 4*10L, 4*20L))
        
        // expect pool to grow only to the maximum
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 5) }
    }
    
    @Test
    public void testNeverGrowColdPool() {
        resizable.resize(2)
        resizable.emit(ResizingPolicy.POOL_COLD, message(2, 1000L, 2*10L, 2*20L))
        
        Thread.sleep(SHORT_WAIT_MS)
        assertEquals(resizable.currentSize, 2)
    }
    
    @Test
    public void testNeverShrinkHotPool() {
        resizeSleepTime = 0
        resizable.resize(2)
        resizable.emit(ResizingPolicy.POOL_HOT, message(2, 0L, 2*10L, 2*20L))
        
        // if had been a POOL_COLD, would have shrunk to 3
        Thread.sleep(SHORT_WAIT_MS)
        assertEquals(resizable.currentSize, 2)
    }
    
    @Test
    public void testConcurrentShrinkShrink() {
        resizeSleepTime = 250
        resizable.resize(4)
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 30L, 4*10L, 4*20L))
        // would cause pool to shrink to 3
        
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 1L, 4*10L, 4*20L))
        // now expect pool to shrink to 1
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
    }
    
    @Test
    public void testConcurrentGrowGrow() {
        resizeSleepTime = 250
        resizable.resize(2)
        resizable.emit(ResizingPolicy.POOL_HOT, message(2, 41L, 2*10L, 2*20L))
        // would cause pool to grow to 3
        
        resizable.emit(ResizingPolicy.POOL_HOT, message(2, 81L, 2*10L, 2*20L))
        // now expect pool to grow to 5
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 5) }
    }
    
    @Test
    public void testConcurrentGrowShrink() {
        resizeSleepTime = 250
        resizable.resize(2)
        resizable.emit(ResizingPolicy.POOL_HOT, message(2, 81L, 2*10L, 2*20L))
        // would cause pool to grow to 5
        
        resizable.emit(ResizingPolicy.POOL_COLD, message(2, 1L, 2*10L, 2*20L))
        // now expect pool to shrink to 1
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
    }
    
    @Test
    public void testConcurrentShrinkGrow() {
        resizeSleepTime = 250
        resizable.resize(4)
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 1L, 4*10L, 4*20L))
        // would cause pool to shrink to 1
        
        resizable.emit(ResizingPolicy.POOL_HOT, message(4, 81L, 4*10L, 4*20L))
        // now expect pool to grow to 5
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 5) }
    }
    
    // FIXME failed in jenkins (e.g. #1035); with "lists don't have the same size expected:<3> but was:<2>"
    // Is it just too time sensitive? But I'd have expected > 3 rather than less
    @Test(groups="WIP")
    public void testRepeatedQueuedResizeTakesLatestValueRatherThanIntermediateValues() {
        // TODO is this too time sensitive? the resize takes only 250ms so if it finishes before the next emit we'd also see size=2
        resizeSleepTime = 500
        resizable.resize(4)
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 30L, 4*10L, 4*20L)) // shrink to 3
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 20L, 4*10L, 4*20L)) // shrink to 2
        resizable.emit(ResizingPolicy.POOL_COLD, message(4, 10L, 4*10L, 4*20L)) // shrink to 1
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
        assertEquals(resizable.sizes, [4, 3, 1])
    }
    

    @Test
    public void testUsesResizeOperatorOverride() {
        resizable.removePolicy(policy)
        
        AtomicInteger counter = new AtomicInteger()
        policy = new ResizingPolicy(resizeOperator:{entity,desiredSize -> counter.incrementAndGet()})
        resizable.addPolicy(policy)
        
        resizable.emit(ResizingPolicy.POOL_HOT, message(1, 21L, 1*10L, 1*20L)) // grow to 2
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertTrue(counter.get() >= 1, "cccounter=$counter")
        }
    }
    
    @Test
    public void testUsesCustomSensorOverride() {
        resizable.removePolicy(policy)
        
        BasicNotificationSensor<Map> customPoolHotSensor = new BasicNotificationSensor<Map>(Map.class, "custom.hot", "")
        BasicNotificationSensor<Map> customPoolColdSensor = new BasicNotificationSensor<Map>(Map.class, "custom.cold", "")
        BasicNotificationSensor<Map> customPoolOkSensor = new BasicNotificationSensor<Map>(Map.class, "custom.ok", "")
        policy = new ResizingPolicy(poolHotSensor:customPoolHotSensor, poolColdSensor:customPoolColdSensor, poolOkSensor:customPoolOkSensor)
        resizable.addPolicy(policy)
        
        resizable.emit(customPoolHotSensor, message(1, 21L, 1*10L, 1*20L)) // grow to 2
        executeUntilSucceeds(timeout:TIMEOUT_MS*100) { assertEquals(resizable.currentSize, 2) }
        
        resizable.emit(customPoolColdSensor, message(2, 1L, 1*10L, 1*20L)) // shrink to 1
        executeUntilSucceeds(timeout:TIMEOUT_MS*100) { assertEquals(resizable.currentSize, 1) }
    }
    
    @Test
    public void testResizeUpStabilizationDelayIgnoresBlip() {
        long resizeUpStabilizationDelay = 1000L
        long minPeriodBetweenExecs = 0
        resizable.removePolicy(policy)
        
        policy = new ResizingPolicy(resizeUpStabilizationDelay:resizeUpStabilizationDelay, minPeriodBetweenExecs:minPeriodBetweenExecs)
        resizable.addPolicy(policy)
        resizable.resize(1)
        
        // Ignores temporary blip
        resizable.emit(ResizingPolicy.POOL_HOT, message(1, 61L, 1*10L, 1*20L)) // would grow to 4
        Thread.sleep(resizeUpStabilizationDelay-OVERHEAD_DURATION_MS)
        resizable.emit(ResizingPolicy.POOL_OK, message(1, 11L, 4*10L, 4*20L)) // but 1 is still adequate
        
        assertEquals(resizable.currentSize, 1)
        assertSucceedsContinually(duration:2000L) { assertEquals(resizable.sizes, [1]) }
    }

    @Test
    public void testResizeUpStabilizationDelayTakesMaxSustainedDesired() {
        long resizeUpStabilizationDelay = 1000L
        long minPeriodBetweenExecs = 0
        resizable.removePolicy(policy)
        
        policy = new ResizingPolicy(resizeUpStabilizationDelay:resizeUpStabilizationDelay, minPeriodBetweenExecs:minPeriodBetweenExecs)
        resizable.addPolicy(policy)
        resizable.resize(1)
        
        // Grows to max sustained in time window
        resizable.emit(ResizingPolicy.POOL_HOT, message(1, 61L, 1*10L, 1*20L)) // would grow to 4
        resizable.emit(ResizingPolicy.POOL_HOT, message(1, 21L, 1*10L, 1*20L)) // would grow to 2
        Thread.sleep(resizeUpStabilizationDelay-OVERHEAD_DURATION_MS)
        resizable.emit(ResizingPolicy.POOL_HOT, message(1, 61L, 1*10L, 1*20L)) // would grow to 4
        
        long emitTime = System.currentTimeMillis()
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 2) }
        long resizeDelay = System.currentTimeMillis() - emitTime
        assertTrue(resizeDelay >= (OVERHEAD_DURATION_MS*2))
    }

    @Test
    public void testResizeUpStabilizationDelayResizesAfterDelay() {
        long resizeUpStabilizationDelay = 1000L
        long minPeriodBetweenExecs = 0
        resizable.removePolicy(policy)
        
        policy = new ResizingPolicy(resizeUpStabilizationDelay:resizeUpStabilizationDelay, minPeriodBetweenExecs:minPeriodBetweenExecs)
        resizable.addPolicy(policy)
        resizable.resize(1)
        
        // After suitable delay, grows to desired
        long emitTime = System.currentTimeMillis()
        resizable.emit(ResizingPolicy.POOL_HOT, message(1, 61L, 1*10L, 1*20L)) // would grow to 4
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 4) }
        long resizeDelay = System.currentTimeMillis() - emitTime
        assertTrue(resizeDelay >= (resizeUpStabilizationDelay-EARLY_RETURN_MS), "resizeDelay=$resizeDelay")
    }

    @Test
    public void testResizeDownStabilizationDelayIgnoresBlip() {
        long resizeStabilizationDelay = 1000L
        long minPeriodBetweenExecs = 0
        resizable.removePolicy(policy)
        
        policy = new ResizingPolicy(resizeDownStabilizationDelay:resizeStabilizationDelay, minPeriodBetweenExecs:minPeriodBetweenExecs)
        resizable.addPolicy(policy)
        resizable.resize(2)
        
        // Ignores temporary blip
        resizable.emit(ResizingPolicy.POOL_COLD, message(2, 1L, 2*10L, 2*20L)) // would shrink to 1
        Thread.sleep(resizeStabilizationDelay-OVERHEAD_DURATION_MS)
        resizable.emit(ResizingPolicy.POOL_OK, message(2, 20L, 1*10L, 1*20L)) // but 2 is still adequate
        
        assertEquals(resizable.currentSize, 2)
        assertSucceedsContinually(duration:2000L) { assertEquals(resizable.sizes, [2]) }
    }

    @Test
    public void testResizeDownStabilizationDelayTakesMinSustainedDesired() {
        long resizeDownStabilizationDelay = 1000L
        long minPeriodBetweenExecs = 0
        resizable.removePolicy(policy)
        
        policy = new ResizingPolicy(resizeDownStabilizationDelay:resizeDownStabilizationDelay, minPeriodBetweenExecs:minPeriodBetweenExecs)
        resizable.addPolicy(policy)
        resizable.resize(3)
        
        // Shrink to min sustained in time window
        resizable.emit(ResizingPolicy.POOL_COLD, message(3, 1L, 3*10L, 3*20L)) // would shrink to 1
        resizable.emit(ResizingPolicy.POOL_COLD, message(3, 20L, 3*10L, 3*20L)) // would shrink to 2
        Thread.sleep(resizeDownStabilizationDelay-OVERHEAD_DURATION_MS)
        resizable.emit(ResizingPolicy.POOL_COLD, message(3, 1L, 3*10L, 3*20L)) // would shrink to 1
        
        long emitTime = System.currentTimeMillis()
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 2) }
        long resizeDelay = System.currentTimeMillis() - emitTime
        assertTrue(resizeDelay >= (OVERHEAD_DURATION_MS*2))
    }

    @Test
    public void testResizeDownStabilizationDelayResizesAfterDelay() {
        long resizeDownStabilizationDelay = 3000L
        long minPeriodBetweenExecs = 0
        resizable.removePolicy(policy)
        
        policy = new ResizingPolicy(resizeDownStabilizationDelay:resizeDownStabilizationDelay, minPeriodBetweenExecs:minPeriodBetweenExecs)
        resizable.addPolicy(policy)
        resizable.resize(2)
        
        // After suitable delay, grows to desired
        long emitTime = System.currentTimeMillis()
        resizable.emit(ResizingPolicy.POOL_COLD, message(2, 1L, 2*10L, 2*20L)) // would shrink to 1
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) { assertEquals(resizable.currentSize, 1) }
        long resizeDelay = System.currentTimeMillis() - emitTime
        assertTrue(resizeDelay >= (resizeDownStabilizationDelay-EARLY_RETURN_MS), "resizeDelay=$resizeDelay")
    }

    static Map<String, Object> message(int currentSize, double currentWorkrate, double lowThreshold, double highThreshold) {
        return ImmutableMap.of(
            ResizingPolicy.POOL_CURRENT_SIZE_KEY, currentSize,
            ResizingPolicy.POOL_CURRENT_WORKRATE_KEY, currentWorkrate,
            ResizingPolicy.POOL_LOW_THRESHOLD_KEY, lowThreshold,
            ResizingPolicy.POOL_HIGH_THRESHOLD_KEY, highThreshold)
    }
}
