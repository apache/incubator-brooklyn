package brooklyn.qa.performance;

import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Stopwatch;

public class AbstractPerformanceTest {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractPerformanceTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;
    
    protected TestApplication app;
    protected SimulatedLocation loc;
    
    @BeforeMethod
    public void setUp() {
        for (int i = 0; i < 5; i++) System.gc();
        loc = new SimulatedLocation();
        app = new TestApplication();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app.isDeployed()) {
            app.stop();
            app.getManagementContext().unmanage(app);
        }
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
                    ": numPerSec="+numPerSec;
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
            if (warmupWatch.elapsedMillis() >= nextLogTime) {
                LOG.info("Warm-up "+prefix+" iteration="+i+" at "+warmupWatch.elapsedMillis()+"ms");
                nextLogTime += logInterval;
            }
            r.run();
        }
        
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        nextLogTime = 0;
        for (int i = 0; i < numIterations; i++) {
            if (stopwatch.elapsedMillis() >= nextLogTime) {
                LOG.info(prefix+" iteration="+i+" at "+stopwatch.elapsedMillis()+"ms");
                nextLogTime += logInterval;
            }
            r.run();
        }
        return stopwatch.elapsedMillis();
    }
    
    protected long measure(Runnable r) {
        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        r.run();
        return stopwatch.elapsedMillis();
    }
}
