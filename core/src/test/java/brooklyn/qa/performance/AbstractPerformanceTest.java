package brooklyn.qa.performance;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.internal.DoubleSystemProperty;

import com.google.common.base.Stopwatch;

/**
 * For running simplistic performance tests, to measure the number of operations per second and compare 
 * it against some min rate.
 * 
 * This is "good enough" for eye-balling performance, to spot if it goes horrendously wrong. 
 * 
 * However, good performance measurement involves much more warm up (e.g. to ensure java HotSpot 
 * optimisation have been applied), and running the test for a reasonable length of time.
 * We are also not running the tests for long enough to check if object creation is going to kill
 * performance in the long-term, etc.
 */
public class AbstractPerformanceTest {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractPerformanceTest.class);
    
    public static final DoubleSystemProperty PERFORMANCE_EXPECTATION_SYSPROP = 
            new DoubleSystemProperty("brooklyn.test.performanceExpectation");
    
    /**
     * A scaling factor for the expected performance, where 1 is a conservative expectation of
     * minimum to expect every time in normal circumstances.
     * 
     * However, for running in CI, defaults to 0.1 so if GC kicks in during the test we won't fail...
     */
    public static double PERFORMANCE_EXPECTATION = PERFORMANCE_EXPECTATION_SYSPROP.isAvailable() ? 
            PERFORMANCE_EXPECTATION_SYSPROP.getValue() : 0.1d;
    
    protected static final long TIMEOUT_MS = 10*1000;
    
    protected TestApplication app;
    protected SimulatedLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        for (int i = 0; i < 5; i++) System.gc();
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    protected void measureAndAssert(String prefix, int numIterations, double minRatePerSec, Runnable r) {
        measureAndAssert(prefix, numIterations, minRatePerSec, r, null);
    }
    
    protected void measureAndAssert(String prefix, int numIterations, double minRatePerSec, Runnable r, Runnable postIterationPhase) {
        long durationMillis = measure(prefix, numIterations, r);
        long postIterationDurationMillis = (postIterationPhase != null) ? measure(postIterationPhase) : 0;
        
        double numPerSec = ((double)numIterations/durationMillis * 1000);
        double numPerSecIncludingPostIteration = ((double)numIterations/(durationMillis+postIterationDurationMillis) * 1000);
        
        String msg1 = prefix+": "+durationMillis+"ms for "+numIterations+" iterations"+
                    (postIterationPhase != null ? "(+"+postIterationDurationMillis+"ms for post-iteration phase)" : "")+
                    ": numPerSec="+numPerSec+"; minAcceptableRate="+minRatePerSec;
        String msg2 = (postIterationPhase != null ? " (or "+numPerSecIncludingPostIteration+" per sec including post-iteration phase time)" : "");
        
        LOG.info(msg1+msg2);
        System.out.println("\n"+msg1+"\n"+msg2+"\n");  //make it easier to see in the console in eclipse :)
        assertTrue(numPerSecIncludingPostIteration >= minRatePerSec, msg1+msg2);
    }
    
    protected long measure(String prefix, int numIterations, Runnable r) {
        final int logInterval = 5*1000;
        long nextLogTime = logInterval;
        
        // Give it some warm-up cycles
        Stopwatch warmupWatch = new Stopwatch();
        warmupWatch.start();
        for (int i = 0; i < (numIterations/10); i++) {
            if (warmupWatch.elapsed(TimeUnit.MILLISECONDS) >= nextLogTime) {
                LOG.info("Warm-up "+prefix+" iteration="+i+" at "+warmupWatch.elapsed(TimeUnit.MILLISECONDS)+"ms");
                nextLogTime += logInterval;
            }
            r.run();
        }
        
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        nextLogTime = 0;
        for (int i = 0; i < numIterations; i++) {
            if (stopwatch.elapsed(TimeUnit.MILLISECONDS) >= nextLogTime) {
                LOG.info(prefix+" iteration="+i+" at "+stopwatch.elapsed(TimeUnit.MILLISECONDS)+"ms");
                nextLogTime += logInterval;
            }
            r.run();
        }
        return stopwatch.elapsed(TimeUnit.MILLISECONDS);
    }
    
    protected long measure(Runnable r) {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        r.run();
        return stopwatch.elapsed(TimeUnit.MILLISECONDS);
    }
}
