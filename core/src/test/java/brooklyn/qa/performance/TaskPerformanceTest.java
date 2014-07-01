package brooklyn.qa.performance;

import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.SingleThreadedScheduler;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class TaskPerformanceTest extends AbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(TaskPerformanceTest.class);
    
    private static final long LONG_TIMEOUT_MS = 30*1000;
    
    BasicExecutionManager executionManager;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        app.start(ImmutableList.of(loc));
        
        executionManager = (BasicExecutionManager) app.getManagementContext().getExecutionManager();
    }

    public static final int numIterations = 200000;

    @Test(groups={"Integration", "Acceptance"})
    public void testExecuteSimplestRunnable() {
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        
        final Runnable work = new Runnable() {
            public void run() {
                int val = counter.incrementAndGet();
                if (val >= numIterations) completionLatch.countDown();
            }};

        measureAndAssert("executeSimplestRunnable", numIterations, minRatePerSec,
                new Runnable() {
                    public void run() {
                        executionManager.submit(work);
                    }},
                new Runnable() {
                    public void run() {
                        try {
                            completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        } 
                        assertTrue(completionLatch.getCount() <= 0);
                    }});
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testExecuteRunnableWithTags() {
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);

        final Runnable work = new Runnable() { public void run() {
                int val = counter.incrementAndGet();
                if (val >= numIterations) completionLatch.countDown();
            }
        };

        final Map<String, ?> flags = MutableMap.of("tags", ImmutableList.of("a","b"));
        
        measureAndAssert("testExecuteRunnableWithTags", numIterations, minRatePerSec,
                new Runnable() {
                    public void run() {
                        executionManager.submit(flags, work);
                    }},
                new Runnable() {
                    public void run() {
                        try {
                            completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        } 
                        assertTrue(completionLatch.getCount() <= 0);
                    }});
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testExecuteWithSingleThreadedScheduler() throws Exception {
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;

        executionManager.setTaskSchedulerForTag("singlethreaded", SingleThreadedScheduler.class);
        
        final AtomicInteger concurrentCallCount = new AtomicInteger();
        final AtomicInteger submitCount = new AtomicInteger();
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final List<Exception> exceptions = Lists.newCopyOnWriteArrayList();
        
        final Runnable work = new Runnable() { public void run() {
                int numConcurrentCalls = concurrentCallCount.incrementAndGet();
                try {
                    if (numConcurrentCalls > 1) throw new IllegalStateException("numConcurrentCalls="+numConcurrentCalls);
                    int val = counter.incrementAndGet();
                    if (val >= numIterations) completionLatch.countDown();
                } catch (Exception e) {
                    exceptions.add(e);
                    LOG.warn("Exception in runnable of testExecuteWithSingleThreadedScheduler", e);
                    throw Exceptions.propagate(e);
                } finally {
                    concurrentCallCount.decrementAndGet();
                }
            }
        };

        measureAndAssert("testExecuteWithSingleThreadedScheduler", numIterations, minRatePerSec,
                new Runnable() {
                    public void run() {
                        while (submitCount.get() > counter.get() + 5000) {
                            LOG.info("delaying because "+submitCount.get()+" submitted and only "+counter.get()+" run");
                            Time.sleep(500);
                        }
                        executionManager.submit(MutableMap.of("tags", ImmutableList.of("singlethreaded")), work); 
                        submitCount.incrementAndGet();
                    }},
                new Runnable() {
                    public void run() {
                        try {
                            completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        } 
                        assertTrue(completionLatch.getCount() <= 0);
                    }});
        
        if (exceptions.size() > 0) throw exceptions.get(0);
    }
    
    public static void main(String[] args) throws Exception {
        TaskPerformanceTest t = new TaskPerformanceTest();
        t.setUp();
        try {
            t.testExecuteWithSingleThreadedScheduler();
        } finally {
            t.tearDown();
        }
    }
}
