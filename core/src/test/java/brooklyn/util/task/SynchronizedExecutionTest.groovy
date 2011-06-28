package brooklyn.util.task

import static org.testng.Assert.*;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.testng.annotations.Test;

import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;

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
        Thread mon = new Thread({ try { Thread.sleep(1000); println "tasks for $em"; em.allTasks.each { println "$it: "+it.getStatusDetail(true); } } catch (Throwable t) {} })
        mon.start();
        
        em.setTaskPreprocessorForTag("category1", SingleThreadedExecution.class);
        
        em.submit tag:"category1", t1
        em.submit tag:"category1", t2

        t2.get()
        assertDataInOrder(size:3, last:2)
        
        mon.interrupt()
    }

    @Test
    public void runTwoSynchedTasks100Times() {
        100.times { runTwoSynchedTasks() }
    }
}
