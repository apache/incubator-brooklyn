package brooklyn.qa.performance

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.proxying.EntitySpec
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.SubscriptionManager
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class SubscriptionPerformanceTest extends AbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionPerformanceTest.class)
    
    private static final long LONG_TIMEOUT_MS = 30*1000
    private static final int NUM_ITERATIONS = 10000
    
    TestEntity entity
    List<TestEntity> entities
    SubscriptionManager subscriptionManager
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        super.setUp()
        
        entities = []
        for (int i = 0; i < 10; i++) {
            entities += app.createAndManageChild(EntitySpec.create(TestEntity.class));
        }
        entity = entities[0]
        app.start([loc])
        
        subscriptionManager = app.managementContext.subscriptionManager
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testManyPublishedOneSubscriber() {
        int numSubscribers = 1
        int numIterations = NUM_ITERATIONS
        double minRatePerSec = 100 * PERFORMANCE_EXPECTATION; // i.e. 100*10 events delivered per sec
        int iter = 0
        int expectedCount = numIterations*numSubscribers
        
        AtomicInteger listenerCount = new AtomicInteger()
        CountDownLatch completionLatch = new CountDownLatch(1)
        
        for (int i = 0; i < numSubscribers; i++) {
            subscriptionManager.subscribe([subscriber:i], entity, TestEntity.SEQUENCE,
                {
                    int count = listenerCount.incrementAndGet()
                    if (count >= expectedCount) completionLatch.countDown()
                } as SensorEventListener)
        }
        
        measureAndAssert("updateAttributeWithManyPublishedOneSubscriber", numIterations, minRatePerSec,
                { entity.setAttribute(TestEntity.SEQUENCE, (iter++)) },
                { completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS); assertTrue(completionLatch.getCount() <= 0) })
    }
    
    // TODO but surely parallel should be much faster?!
    @Test(groups=["Integration", "Acceptance"])
    public void testManyListenersForSensorEvent() {
        int numSubscribers = 10
        int numIterations = NUM_ITERATIONS
        double minRatePerSec = 100 * PERFORMANCE_EXPECTATION; // i.e. 100*10 events delivered per sec
        int iter = 0
        int expectedCount = numIterations*numSubscribers
        
        AtomicInteger listenerCount = new AtomicInteger()
        CountDownLatch completionLatch = new CountDownLatch(1)
        
        for (int i = 0; i < numSubscribers; i++) {
            subscriptionManager.subscribe([subscriber:i], entity, TestEntity.SEQUENCE, 
                {
                    int count = listenerCount.incrementAndGet()
                    if (count >= expectedCount) completionLatch.countDown()
                } as SensorEventListener)
        }
        
        measureAndAssert("updateAttributeWithManyListeners", numIterations, minRatePerSec,
                { entity.setAttribute(TestEntity.SEQUENCE, (iter++)) },
                { completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS); assertTrue(completionLatch.getCount() <= 0) })
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testUpdateAttributeWithNoListenersButManyUnrelatedListeners() {
        int numUnrelatedSubscribers = 1000
        int numIterations = NUM_ITERATIONS
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        int iter = 0
        int lastVal = 0
        Exception exception
        
        for (int i = 0; i < (numUnrelatedSubscribers/2); i++) {
            subscriptionManager.subscribe([subscriber:i], entities[1], TestEntity.SEQUENCE, 
                    {
                        exception = new RuntimeException("Unrelated subscriber called with $it")
                        throw exception 
                    } as SensorEventListener)
            subscriptionManager.subscribe([subscriber:i], entity, TestEntity.MY_NOTIF, 
                    {
                        exception = new RuntimeException("Unrelated subscriber called with $it")
                        throw exception 
                    } as SensorEventListener)
        }
        
        measureAndAssert("updateAttributeWithUnrelatedListeners", numIterations, minRatePerSec) {
            entity.setAttribute(TestEntity.SEQUENCE, (++iter))
        }
        
        if (exception != null) {
            throw exception
        }
    }

}
