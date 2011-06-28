package brooklyn.util.task;

import static org.testng.Assert.*

import java.util.Map
import java.util.concurrent.ConcurrentHashMap

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.management.Task

/**
 * Test the operation of the {@link CompoundTask} class.
 * 
 * TODO clarify test purpose
 */
public class CompoundTaskExecutionTest {
    private static final Logger log = LoggerFactory.getLogger(CompoundTaskExecutionTest.class)
 
    private Map data = new ConcurrentHashMap()
    
    @Test
    public void runSequenceTask() {
        data.clear()
        
        data.put(1, "a")
        BasicTask t1 = [ { data.put(1, "b") } ]
        BasicTask t2 = [ { data.put(1, "c") } ]
        BasicTask t3 = [ { data.put(1, "d") } ]
        BasicTask t4 = [ { data.put(1, "e") } ]
        
        BasicExecutionManager em = []
        Task tSequence = em.submit tag:"A", new SequentialTask(t1, t2, t3, t4)
        
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
        
        BasicExecutionManager em = []
        Task tSequence = em.submit tag:"A", new ParallelTask(t4, t2, t1, t3)
        
        assertEquals(["a", "b", "c", "d", "e"], (tSequence.get() + data.get(1)).sort())
    }

    @Test
    public void testAlexsComplex() {
        List vals = Collections.synchronizedList([] as List)
        
        BasicExecutionManager em = []
        Task t = em.submit(tag:"A", new ParallelTask(
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
    
        //TODO figure out what return value should be (currenlty is everything...); see comments in compound task    
//        assertEquals(["a5","b5"], result)
    }
    
}
