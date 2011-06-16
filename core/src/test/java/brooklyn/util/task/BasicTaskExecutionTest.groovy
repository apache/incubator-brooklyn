package brooklyn.util.task;

import java.util.Map;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import org.junit.Test;

public class BasicTaskExecutionTest {

	Map data = new ConcurrentHashMap()
	
	@Test
	public void runSimpleTask() {
		data.clear()
		Task t = [ { data.put(1, "b") } ] 
		data.put(1, "a")
		ExecutionManager em = []
		Task t2 = em.submit "A", t
		assertEquals("a", t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runSimpleRunnable() {
		data.clear()
		data.put(1, "a")
		ExecutionManager em = []
		Task t = em.submit "A", new Runnable() { public void run() { data.put(1, "b") } }
		assertEquals(null, t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runSimpleCallable() {
		data.clear()
		data.put(1, "a")
		ExecutionManager em = []
		Task t = em.submit "A", new Callable() { public Object call() { data.put(1, "b") } }
		assertEquals("a", t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runTaskWithWaits() {
		data.clear()
		Task t = [ {
			synchronized(data) {
				def result = data.put(1, "b")
				data.notify()
				data.wait()
				result
			}
		} ]
		data.put(1, "a")
		ExecutionManager em = []
		synchronized (data) {
			Task t2 = em.submit "A", t
			assertEquals(t, t2)
			assertFalse(t.isDone())
			
			assertEquals("a", data.get(1))
			data.wait()
			assertEquals("b", data.get(1))
			assertFalse(t.isDone())
			
			data.notify()
		}
		assertEquals("a", t.get())
	}

	@Test
	public void runMultipleTasks() {
		data.clear()
		data.put(1, 1)
		ExecutionManager em = []
		2.times { em.submit "A", new Task({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		2.times { em.submit "B", new Task({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		int total = 0;
		em.getTaskBuckets().each { em.getTasksByBucket(it).each { total += it.get() } }
		assertEquals(10, total)
		assertEquals(5, data.get(1))
	}

}
