package brooklyn.util.task

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.Map
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import brooklyn.test.TestUtils

import org.testng.Assert
import org.testng.annotations.Test

/**
 * @deprecated will be deleted in 0.5. // use SingleThreadedScheduler; FIXME delete this class when we're definitely
 * happy with SingleThreadedScheduler
 */
@Deprecated
class SynchronizedExecutionTest {
    List data = new ArrayList()
    
    /** accepts flags last and size */
    protected void assertDataInOrder(Map flags=[:]) {
        Object last = flags["last"]
        Object size = flags["size"]?:null
        
        if (size!=null) assertEquals(data.size(), size, "expected size $size but it is ${data.size}; list is $data")
        
        Object lo = null
        for (Object o in data) {
            if (lo!=null) assertTrue(lo.compareTo(o) < 0, "expected $o greater than $lo; list is $data")
            lo = o
        }
        if (last!=null) assertTrue(lo == last, "expected last element $last but it is $lo; list is $data")
    }
    
    @Test
    public void runTwoSynchedTasks() {
        data.clear()
        BasicTask t1 = [ { data << 1;  } ]
        BasicTask t2 = [ { data << 2; } ]
        data << 0
        
        BasicExecutionManager em = []
        
        //for debug
//        Thread mon = new Thread({ try { Thread.sleep(1000); println "tasks for $em"; em.allTasks.each { println "$it: "+it.getStatusDetail(true); } } catch (Throwable t) {} })
//        mon.start();
        
        em.setTaskPreprocessorForTag("category1", SingleThreadedExecution.class);
        
        em.submit tag:"category1", t1
        em.submit tag:"category1", t2

        t2.get()
        assertDataInOrder(size:3, last:2)
        
//        mon.interrupt()
    }

    @Test(enabled=false)
    public void runManySynchedTasks() {
        BasicExecutionManager em = []
        try {
            em.setTaskPreprocessorForTag("category1", SingleThreadedExecution.class);
            
            final CountDownLatch latch = new CountDownLatch(1)
            BasicTask blockingTask = [ { latch.await() } ]
            em.submit tag:"category1", blockingTask
            
            final AtomicInteger counter = new AtomicInteger(0)
            for (i in 1..10000) {
                BasicTask t = [ {counter.incrementAndGet()} ]
                em.submit tag:"category1", t
            }
    
            Thread.sleep(10000)
            latch.countDown()
    
            executeUntilSucceeds {
                assertEquals(counter.get(), 10000)
            }
        } finally {
            em?.shutdownNow()
        } 
    }

    @Test(enabled = false)
    public void runTwoSynchedTasks100Times() {
        100.times { runTwoSynchedTasks() }
    }
}
