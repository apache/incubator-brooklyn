package brooklyn.qa.performance;

import static org.testng.Assert.assertTrue

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

public class GroovyYardStickPerformanceTest extends AbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyYardStickPerformanceTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;

    private ExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        super.setUp();
        executor = Executors.newCachedThreadPool();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        super.tearDown();
        if (executor != null) executor.shutdownNow();
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testGroovyNoopToEnsureTestFrameworkIsVeryFast() {
        int numIterations = 1000000;
        double minRatePerSec = 1000000 * PERFORMANCE_EXPECTATION;
        AtomicInteger i = new AtomicInteger();
        
        measureAndAssert("noop-groovy", numIterations, minRatePerSec, { i.incrementAndGet() });
        assertTrue(i.get() >= numIterations, "i="+i);
    }
}
