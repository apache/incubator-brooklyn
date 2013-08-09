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
    private Semaphore cancelled;

    @BeforeMethod
    public void setUp() {
        em = new BasicExecutionManager("mycontext");
        ec = new BasicExecutionContext(em);
        ex = Executors.newCachedThreadPool();
//        assertTrue em.allTasks.isEmpty()
        data = Collections.synchronizedMap(new LinkedHashMap<Object,Object>());
        data.clear();
        cancelled = new Semaphore(0);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
        if (ex != null) ex.shutdownNow();
    }

    /** 
     * We do a whole bunch in one test which runs as a normal (non-integration) test,
     * because a delay is needed, but to keep delays in normal tests to a minimum!
     */
    @Test
    public void testBlockAndGetWithTimeoutsAndListenableFuture() throws InterruptedException {
        Task<String> t = sleep(Duration.millis(500), "x");
        
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
            Assert.assertTrue(t.blockUntilEnded(Duration.FIVE_SECONDS));

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
                    cancelled.release();
                    throw Exceptions.propagate(e);
                }
                log.info("task returning "+result);
                return result; 
            }
        }).build();
    }

    @Test
    public void testCancelAfterStartTriggersListenableFuture() throws Exception {
        doTestCancelImmediateTriggersListenableFuture(Duration.millis(50));
    }
    @Test
    public void testCancelImmediateTriggersListenableFuture() throws Exception {
        doTestCancelImmediateTriggersListenableFuture(Duration.ZERO);
    }
    public void doTestCancelImmediateTriggersListenableFuture(Duration delay) throws Exception {
        Task<String> t = sleep(Duration.millis(1000), "x");
        addFutureListener(t, "before");

        Stopwatch watch = new Stopwatch().start();
        ec.submit(t);
        
        addFutureListener(t, "during");

        log.info("test cancelling "+t+" ("+t.getClass()+") after "+delay);
        // NB: two different code paths for notifying futures depending whether task is started 
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
        
        Assert.assertTrue(watch.elapsed(TimeUnit.MILLISECONDS) < 500, 
            Time.makeTimeStringRounded(watch.elapsed(TimeUnit.MILLISECONDS))+" is too long; should have cancelled < 500ms");
        
        Assert.assertTrue(cancelled.tryAcquire(500, TimeUnit.MILLISECONDS));
    }

}
