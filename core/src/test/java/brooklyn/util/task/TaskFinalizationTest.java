package brooklyn.util.task;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.management.Task;

public class TaskFinalizationTest {

    // integration because it can take a while
    @Test(groups="Integration")
    public void testFinalizerInvoked() throws InterruptedException {
        BasicTask<?> t = new BasicTask<Void>(new Runnable() { public void run() { /* no op */ }});
        final int[] x = new int[1];
        t.setFinalizer(new BasicTask.TaskFinalizer() {
            public void onTaskFinalization(Task<?> t) {
                synchronized (x) { 
                    x[0]++;
                    x.notifyAll(); 
                }
            }
        });
        t = null;
        System.gc(); System.gc();
        synchronized (x) {
            if (x[0]==1) return;
            x.wait(5*1000);
            if (x[0]==1) return;
        }
        Assert.fail("finalizer did not run in time");
    }

}
