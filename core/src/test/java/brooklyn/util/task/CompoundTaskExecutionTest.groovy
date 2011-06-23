package brooklyn.util.task;

import static org.junit.Assert.*

import java.util.Map
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import org.junit.Test

import brooklyn.management.Task

public class CompoundTaskExecutionTest {
	Map data = new ConcurrentHashMap()
	
	@Test
	public void runSequenceTask() {
		data.clear()
		
		data.put(1, "a")
		BasicTask t1 = [ { data.put(1, "b") } ]
		BasicTask t2 = [ { data.put(1, "c") } ]
		BasicTask t3 = [ { data.put(1, "d") } ]
		BasicTask t4 = [ { data.put(1, "e") } ]
		
		BasicExecutionManager em = []
		BasicTask tSequence = em.submit tag:"A", new SequentialTask(t1, t2, t3, t4)
		
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
		BasicTask tSequence = em.submit tag:"A", new ParallelTask(t4, t2, t1, t3)
		
		assertEquals(["a", "b", "c", "d", "e"], (tSequence.get() + data.get(1)).sort())
	}

}
