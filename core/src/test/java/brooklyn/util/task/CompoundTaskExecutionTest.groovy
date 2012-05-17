package brooklyn.util.task;

import static org.testng.Assert.*

import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test

import brooklyn.management.Task

/**
 * Test the operation of the {@link CompoundTask} class.
 */
public class CompoundTaskExecutionTest {
    private static final Logger log = LoggerFactory.getLogger(CompoundTaskExecutionTest.class)
 
    private Map data = new ConcurrentHashMap()

    BasicExecutionManager em;
    BasicExecutionContext ec;
    
    @BeforeClass
    public void setup() {
        em = new BasicExecutionManager();
        ec = new BasicExecutionContext(em)
    }
    
    @AfterClass
    public void teardown() {
        if (em) em.shutdownNow();
        em = null;
    }
    
    @Test
    public void runSequenceTask() {
        data.clear()
        
        data.put(1, "a")
        BasicTask t1 = [ { data.put(1, "b") } ]
        BasicTask t2 = [ { data.put(1, "c") } ]
        BasicTask t3 = [ { data.put(1, "d") } ]
        BasicTask t4 = [ { data.put(1, "e") } ]
        
        Task tSequence = ec.submit tag:"A", new SequentialTask(t1, t2, t3, t4)
        
        assertEquals(["a", "b", "c", "d", "e"], tSequence.get() + data.get(1))
    }

    @Test
    // for legacy compatibility; generally we will require execution contexts, stronger than managers
    // (this test will cause a warning)
    public void runSequenceTaskWithoutContext() {
        data.clear()
        
        data.put(1, "a")
        BasicTask t1 = [ { data.put(1, "b") } ]
        BasicTask t2 = [ { data.put(1, "c") } ]
        BasicTask t3 = [ { data.put(1, "d") } ]
        BasicTask t4 = [ { data.put(1, "e") } ]
        
        BasicExecutionManager legacyDirectExecMgrUse = []
        Task tSequence = legacyDirectExecMgrUse.submit tag:"A", new SequentialTask(t1, t2, t3, t4)
        
        assertEquals(["a", "b", "c", "d", "e"], tSequence.get() + data.get(1))
    }

    @Test
    public void runParallelTask() {
        data.clear()
        
        data.put(1, "a")
        BasicTask t1 = [ { data.put(1, "b") } ]
        BasicTask t2 = [ { data.put(1, "c") } ]
        BasicTask t3 = [ { data.put(1, "d") } ]
        BasicTask t4 = [ { data.put(1, "e") } ]
        
        Task tSequence = ec.submit tag:"A", new ParallelTask(t4, t2, t1, t3)
        
        assertEquals(["a", "b", "c", "d", "e"], (tSequence.get() + data.get(1)).sort())
    }

    Semaphore locker = new Semaphore(0);
    
    @Test
    public void runParallelTaskWithDelay() {
        data.clear()
        
        data.put(1, "a")
        BasicTask t1 = [ { locker.acquire(); data.put(1, "b"); } ]
        BasicTask t2 = [ { data.put(1, "c") } ]
        BasicTask t3 = [ { data.put(1, "d") } ]
        BasicTask t4 = [ { data.put(1, "e") } ]
        
        Task tSequence = ec.submit tag:"A", new ParallelTask(t4, t2, t1, t3)
        
        assertEquals(["a", "c", "d", "e"], [t2.get(), t3.get(), t4.get(), data.get(1)].sort());
        assertFalse(t1.isDone());
        assertFalse(tSequence.isDone());
        
        //get blocks until tasks have completed
        Thread t = new Thread({tSequence.get(); locker.release();});
        t.start();
        Thread.sleep(30);
        assertTrue(t.isAlive());
                
        locker.release();
        
        assertEquals(["a", "b", "c", "d", "e"], (tSequence.get() + data.get(1)).sort())
        assertTrue(t1.isDone());
        assertTrue(tSequence.isDone());
        
        locker.acquire();
    }

    @Test
    public void testAlexsComplex() {
        List vals = Collections.synchronizedList([] as List)
        
        Task t = ec.submit(tag:"A", new ParallelTask(
            new SequentialTask((1..5).collect { int i -> { def ignored -> Thread.sleep((int)(100*Math.random())); log.debug "running a{}", i; vals.add("a$i"); } }),
            new SequentialTask((1..5).collect({ int i -> { def ignored -> Thread.sleep((int)(100*Math.random())); log.debug "running b{}", i; vals.add("b$i"); } }))
        ));
        def result = t.get()
        
        log.debug "tasks happened in order: {}", vals
        
        assertEquals(10, vals.size())

        //a1, ..., a5 should be _in order_        
        List va = []; va += vals; (1..5).each { int i-> va.remove("b$i") }
        List vb = []; vb += vals; (1..5).each { int i-> vb.remove("a$i") }
        
        assertEquals((1..5).collect{int i->"a$i"}, va)
        //b1, ..., also        
        assertEquals((1..5).collect{int i->"b$i"}, vb)
        // sequential tasks return all intermediate results; a pipeline task type/framework may also be wanted (to just return the last)
    }
    
}
