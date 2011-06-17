package brooklyn.util.task;

import java.util.Map;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import org.junit.Test;

import brooklyn.util.internal.LanguageUtils;

public class BasicTaskExecutionTest {

	Map data = new ConcurrentHashMap()
	
	@Test
	public void runSimpleTask() {
		data.clear()
		Task t = [ { data.put(1, "b") } ] 
		data.put(1, "a")
		ExecutionManager em = []
		Task t2 = em.submit tag:"A", t
		assertEquals("a", t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runSimpleRunnable() {
		data.clear()
		data.put(1, "a")
		ExecutionManager em = []
		Task t = em.submit tag:"A", new Runnable() { public void run() { data.put(1, "b") } }
		assertEquals(null, t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runSimpleCallable() {
		data.clear()
		data.put(1, "a")
		ExecutionManager em = []
		Task t = em.submit tag:"A", new Callable() { public Object call() { data.put(1, "b") } }
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
			Task t2 = em.submit tag:"A", t
			assertEquals(t, t2)
			assertFalse(t.isDone())
			
			assertEquals("a", data.get(1))
			data.wait()
			assertEquals("b", data.get(1))
			assertFalse(t.isDone())
			
			println "runTaskWithWaits, task status:\n"+t.getStatusDetail(true)
			assertTrue(t.getStatusDetail(false).toLowerCase().contains("waiting"))
			
			data.notify()
		}
		assertEquals("a", t.get())
	}

	@Test
	public void runMultipleTasks() {
		println "runMultipleTasks"
		data.clear()
		data.put(1, 1)
		ExecutionManager em = []
		2.times { em.submit tag:"A", new Task({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		2.times { em.submit tag:"B", new Task({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		int total = 0;
		em.getTaskTags().each { println "tag $it"; em.getTasksWithTag(it).each { println "  task $it, has "+it.get(); total += it.get() } }
		assertEquals(10, total)
		//now that all have completed:
		assertEquals(5, data.get(1))
	}

	@Test
	public void runMultipleTasksMultipleTags() {
		println "runMultipleTasksWithMultipleTags"
		data.clear()
		data.put(1, 1)
		ExecutionManager em = []
		em.submit tag:"A", new Task({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["A","B"], new Task({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["B","C"], new Task({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["D"], new Task({ synchronized(data) { data.put(1, data.get(1)+1) } })
		int total = 0;
		em.getAllTasks().each { println "  task $it, has "+it.get(); total += it.get() }
		assertEquals(10, total)
		//now that all have completed:
		assertEquals(5, data.get(1))
		assertEquals(2, em.getTasksWithTag("A").size())
		assertEquals(2, em.getTasksWithAnyTag(["A"]).size())
		assertEquals(2, em.getTasksWithAllTags(["A"]).size())

		assertEquals(3, em.getTasksWithAnyTag(["A", "B"]).size())
		assertEquals(1, em.getTasksWithAllTags(["A", "B"]).size())
		assertEquals(1, em.getTasksWithAllTags(["B", "C"]).size())
		assertEquals(3, em.getTasksWithAnyTag(["A", "D"]).size())
	}

	@Test
	public void cancelBeforeRun() {
		ExecutionManager em = []
		Task t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
		t.cancel true
		assertTrue(t.isCancelled())
		assertTrue(t.isDone())
		assertTrue(t.isError())
		em.submit tag:"A", t
		try { t.get(); fail("get should have failed due to cancel"); } catch (CancellationException e) {}
		assertTrue(t.isCancelled())
		assertTrue(t.isDone())
		assertTrue(t.isError())
		
		println "cancelBeforeRun status: "+t.getStatusDetail(false)
		assertTrue(t.getStatusDetail(false).toLowerCase().contains("cancel"))
	}

	@Test
	public void cancelDuringRun() {
		ExecutionManager em = []
		Task t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
		assertFalse(t.isCancelled())
		assertFalse(t.isDone())
		assertFalse(t.isError())
		synchronized (data) {
			em.submit tag:"A", t
			data.wait()
			t.cancel true
		}
		assertTrue(t.isCancelled())
		assertTrue(t.isError())
		try { t.get(); fail("get should have failed due to cancel"); } catch (CancellationException e) {}
		assertTrue(t.isCancelled())
		assertTrue(t.isDone())
		assertTrue(t.isError())
	}
	
	@Test
	public void cancelAfterRun() {
		ExecutionManager em = []
		Task t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
		synchronized (data) {
			em.submit tag:"A", t
			data.wait()
			assertFalse(t.isCancelled())
			assertFalse(t.isDone())
			assertFalse(t.isError())
			data.notify()
		}
		assertEquals(42, t.get());
		t.cancel true
		assertFalse(t.isCancelled())
		assertFalse(t.isError())
		assertTrue(t.isDone())
	}
	
	@Test
	public void errorDuringRun() {
		ExecutionManager em = []
		Task t = [ { synchronized (data) { data.notify(); data.wait() }; throw new IllegalStateException("Aaargh"); } ]
		
		synchronized (data) {
			em.submit tag:"A", t
			data.wait()
			assertFalse(t.isCancelled())
			assertFalse(t.isDone())
			assertFalse(t.isError())
			data.notify()
		}
		
		try { t.get(); fail("get should have failed due to error"); } catch (Exception eo) { Exception e = LanguageUtils.getRoot(eo); assertEquals("Aaargh", e.getMessage()) }
		
		assertFalse(t.isCancelled())
		assertTrue(t.isError())
		assertTrue(t.isDone())
		
		println "errorDuringRun status: "+t.getStatusDetail(false)
		assertTrue(t.getStatusDetail(false).contains("Aaargh"))
	}

	@Test
	public void fieldsSetForSimpleTask() {
		ExecutionManager em = []
		Task t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
		assertEquals(null, t.submittedByTask)
		assertEquals(-1, t.submitTimeUtc)
		assertNull(t.getResultFuture())
		synchronized (data) {
			em.submit tag:"A", t
			data.wait()
		}
		assertTrue(t.submitTimeUtc > 0)
		assertTrue(t.startTimeUtc >= t.submitTimeUtc)
		assertNotNull(t.getResultFuture())
		assertEquals(-1, t.endTimeUtc)
		assertEquals(false, t.isCancelled())
		synchronized (data) { data.notify() }
		assertEquals(42, t.get())
		assertTrue(t.endTimeUtc >= t.startTimeUtc)

		println "task duration (millis): "+(t.endTimeUtc - t.submitTimeUtc)		
	}

	@Test
	public void fieldsSetForTaskSubmittedTask() {
		//submitted task B is started by A, and waits for A to complete
		ExecutionManager em = []
		Task t = new Task( displayName: "sample", description: "some descr", { em.submit tag:"B", {
				assertEquals(45, em.getTasksWithTag("A").iterator().next().get());
				46 };
			45 } )
		em.submit tag:"A", t

		t.blockUntilEnded()
		
		assertEquals(2, em.getAllTasks().size())
		
		Task tb = em.getTasksWithTag("B").iterator().next();
		assertEquals( 46, tb.get() )
		assertEquals( t, em.getTasksWithTag("A").iterator().next() )
		assertNull( t.submittedByTask )
		
		Task submitter = tb.submittedByTask;
		assertNotNull(submitter)
		assertEquals("sample", submitter.displayName)
		assertEquals("some descr", submitter.description)
		assertEquals(t, submitter)
		
		assertTrue(submitter.submitTimeUtc <= tb.submitTimeUtc)
		assertTrue(submitter.endTimeUtc <= tb.endTimeUtc)
		
		println "task $tb was submitted by $submitter"
	}

}
