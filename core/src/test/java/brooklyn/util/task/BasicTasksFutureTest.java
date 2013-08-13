package brooklyn.util.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;

public class BasicTasksFutureTest {

    private static final Logger log = LoggerFactory.getLogger(BasicTasksFutureTest.class);
    
    private BasicExecutionManager em;
    private BasicExecutionContext ec;
    private Map<Object,Object> data;
    private ExecutorService ex;
    private Semaphore cancelledWhileSleeping;

    @BeforeMethod
    public void setUp() {
        em = new BasicExecutionManager("mycontext");
        ec = new BasicExecutionContext(em);
        ex = Executors.newCachedThreadPool();
//        assertTrue em.allTasks.isEmpty()
        data = Collections.synchronizedMap(new LinkedHashMap<Object,Object>());
        data.clear();
        cancelledWhileSleeping = new Semaphore(0);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
        if (ex != null) ex.shutdownNow();
    }

    @Test
    public void testBlockAndGetWithTimeoutsAndListenableFuture() throws InterruptedException {
        // 100ms should be enough to ensure x finishes, at least in some runs
        // (test should pass even if no delay, but a delay causes a different code path sometimes)
        Task<String> t = sleep(Duration.millis(100), "x");
        
        Assert.assertFalse(t.blockUntilEnded(Duration.millis(1)));
        Assert.assertFalse(t.blockUntilEnded(Duration.ZERO));
        boolean didNotThrow = false;
        
        try { t.getUnchecked(Duration.millis(1)); didNotThrow = true; }
        catch (Exception e) { /* expected */ }
        Assert.assertFalse(didNotThrow);
        
        try { t.getUnchecked(Duration.ZERO); didNotThrow = true; }
        catch (Exception e) { /* expected */ }
        Assert.assertFalse(didNotThrow);

        addFutureListener(t, "before");
        ec.submit(t);
        
        Assert.assertFalse(t.blockUntilEnded(Duration.millis(1)));
        Assert.assertFalse(t.blockUntilEnded(Duration.ZERO));
        
        try { t.getUnchecked(Duration.millis(1)); didNotThrow = true; }
        catch (Exception e) { /* expected */ }
        Assert.assertFalse(didNotThrow);
        
        try { t.getUnchecked(Duration.ZERO); didNotThrow = true; }
        catch (Exception e) { /* expected */ }
        Assert.assertFalse(didNotThrow);

        addFutureListener(t, "during");
            
        synchronized (data) {
            // now let it finish
            Assert.assertTrue(t.blockUntilEnded(Duration.TEN_SECONDS));

            Assert.assertEquals(t.getUnchecked(Duration.millis(1)), "x");
            Assert.assertEquals(t.getUnchecked(Duration.ZERO), "x");
            
            Assert.assertNull(data.get("before"));
            Assert.assertNull(data.get("during"));
            // can't set the data(above) until we release the lock (in assert call below)
            assertSoonGetsData("before");
            assertSoonGetsData("during");
        }

        // and see that a listener added late also runs
        synchronized (data) {
            addFutureListener(t, "after");
            Assert.assertNull(data.get("after"));
            assertSoonGetsData("after");
        }
    }

    private void addFutureListener(Task<String> t, final String key) {
        t.addListener(new Runnable() { public void run() {
            synchronized (data) {
                log.info("notifying for "+key);
                data.notifyAll();
                data.put(key, true);
            }
        }}, ex);
    }

    private void assertSoonGetsData(String key) throws InterruptedException {
        for (int i=0; i<10; i++) {
            if (data.get(key)==Boolean.TRUE) {
                log.info("got data for "+key);
                return;
            }
            data.wait(Duration.ONE_SECOND.toMilliseconds());
        }
        Assert.fail("did not get data for '"+key+"' in time");
    }

    private <T> Task<T> sleep(final Duration time, final T result) {
        return Tasks.<T>builder().body(new Callable<T>() {
            public T call() { 
                try {
                    log.info("sleeping "+time+" before returning "+result);
                    Time.sleep(time); 
                } catch (Exception e) {
                    log.info("cancelled before returning "+result);
                    cancelledWhileSleeping.release();
                    throw Exceptions.propagate(e);
                }
                log.info("task returning "+result);
                return result; 
            }
        }).build();
    }

    @Test
    public void testCancelAfterStartTriggersListenableFuture() throws Exception {
        doTestCancelTriggersListenableFuture(Duration.millis(50));
    }
    @Test
    public void testCancelImmediateTriggersListenableFuture() throws Exception {
        // if cancel fires after submit but before it passes to the executor,
        // that needs handling separately; this doesn't guarantee this code path,
        // but it happens sometimes (and it should be handled)
        doTestCancelTriggersListenableFuture(Duration.ZERO);
    }
    public void doTestCancelTriggersListenableFuture(Duration delay) throws Exception {
        Task<String> t = sleep(Duration.TEN_SECONDS, "x");
        addFutureListener(t, "before");

        Stopwatch watch = new Stopwatch().start();
        ec.submit(t);
        
        addFutureListener(t, "during");

        log.info("test cancelling "+t+" ("+t.getClass()+") after "+delay);
        // NB: two different code paths (callers to this method) for notifying futures 
        // depending whether task is started 
        Time.sleep(delay);

        synchronized (data) {
            t.cancel(true);
            
            assertSoonGetsData("before");
            assertSoonGetsData("during");

            addFutureListener(t, "after");
            Assert.assertNull(data.get("after"));
            assertSoonGetsData("after");
        }
        
        Assert.assertTrue(t.isDone());
        Assert.assertTrue(t.isCancelled());
        try {
            t.get();
            Assert.fail("should have thrown CancellationException");
        } catch (CancellationException e) { /* expected */ }
        
        Assert.assertTrue(watch.elapsed(TimeUnit.MILLISECONDS) < Duration.FIVE_SECONDS.toMilliseconds(), 
            Time.makeTimeStringRounded(watch.elapsed(TimeUnit.MILLISECONDS))+" is too long; should have cancelled very quickly");

        if (t.isBegun())
            // if the task is begun, this should get released
            Assert.assertTrue(cancelledWhileSleeping.tryAcquire(5, TimeUnit.SECONDS));
    }

}
