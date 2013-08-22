package brooklyn.qa.performance;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Throwables;

public class JavaYardStickPerformanceTest extends AbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(JavaYardStickPerformanceTest.class);
    
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
    
    @Test(groups={"Integration", "Acceptance"})
    public void testPureJavaNoopToEnsureTestFrameworkIsVeryFast() {
        int numIterations = 1000000;
        double minRatePerSec = 1000000 * PERFORMANCE_EXPECTATION;
        final int[] i = {0};
        measureAndAssert("noop-java", numIterations, minRatePerSec, new Runnable() {
            @Override public void run() {
                i[0] = i[0] + 1;
            }});
        
        assertTrue(i[0] >= numIterations, "i="+i);
    }

    @Test(groups={"Integration", "Acceptance"})
    public void testPureJavaScheduleExecuteAndGet() {
        int numIterations = 100000;
        double minRatePerSec = 100000 * PERFORMANCE_EXPECTATION;
        final int[] i = {0};
        measureAndAssert("scheduleExecuteAndGet-java", numIterations, minRatePerSec, new Runnable() {
            @Override public void run() {
                Future<?> future = executor.submit(new Runnable() { public void run() { i[0] = i[0] + 1; }});
                try {
                    future.get();
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }});
        
        assertTrue(i[0] >= numIterations, "i="+i);
    }
}
