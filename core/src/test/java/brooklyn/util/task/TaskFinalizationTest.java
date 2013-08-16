package brooklyn.util.task;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.management.Task;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;

public class TaskFinalizationTest {

    private static final Logger log = LoggerFactory.getLogger(TaskFinalizationTest.class);
    
    // integration because it can take a while (and finalizers aren't even guaranteed)
    @Test(groups="Integration")
    public void testFinalizerInvoked() throws InterruptedException {
        BasicTask<?> t = new BasicTask<Void>(new Runnable() { public void run() { /* no op */ }});
        final Semaphore x = new Semaphore(0);
        t.setFinalizer(new BasicTask.TaskFinalizer() {
            public void onTaskFinalization(Task<?> t) {
                synchronized (x) { 
                    x.release();
                }
            }
        });
        t = null;
        Stopwatch watch = new Stopwatch().start();
        for (int i=0; i<30; i++) {
            System.gc(); System.gc();
            if (x.tryAcquire(1, TimeUnit.SECONDS)) {
                log.info("finalizer ran after "+Time.makeTimeStringRounded(watch));
                return;
            }
        }
        Assert.fail("finalizer did not run in time");
    }

}
