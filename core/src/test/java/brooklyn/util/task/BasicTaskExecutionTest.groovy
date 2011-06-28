package brooklyn.util.task;

import static org.junit.Assert.*

import java.util.Map
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

import org.junit.Test

import brooklyn.util.internal.LanguageUtils

public class BasicTaskExecutionTest {
	Map data = new ConcurrentHashMap()
	
	@Test
	public void runSimpleBasicTask() {
		data.clear()
		BasicTask t = [ { data.put(1, "b") } ] 
		data.put(1, "a")
		BasicExecutionManager em = []
		BasicTask t2 = em.submit tag:"A", t
		assertEquals("a", t.get())
		assertEquals("b", data.get(1))
	}
	
	@Test
	public void runSimpleRunnable() {
		data.clear()
		data.put(1, "a")
		BasicExecutionManager em = []
		BasicTask t = em.submit tag:"A", new Runnable() { public void run() { data.put(1, "b") } }
		assertEquals(null, t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runSimpleCallable() {
		data.clear()
		data.put(1, "a")
		BasicExecutionManager em = []
		BasicTask t = em.submit tag:"A", new Callable() { public Object call() { data.put(1, "b") } }
		assertEquals("a", t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runBasicTaskWithWaits() {
		data.clear()
		String status;
		BasicTask t = [ {
			synchronized(data) {
				def result = data.put(1, "b")
				data.notify()
				ExecutionContext.getCurrentTask().blockingDetails = "here my friend"
				data.wait()
				ExecutionContext.getCurrentTask().blockingDetails = null
				result
			}
		} ]
		status = t.getStatusDetail(true)
		data.put(1, "a")
		BasicExecutionManager em = []
		synchronized (data) {
			BasicTask t2 = em.submit tag:"A", t
			status = t.getStatusDetail(true)
			assertEquals(t, t2)
			assertFalse(t.isDone())
			
			assertEquals("a", data.get(1))
			data.wait()
			assertEquals("b", data.get(1))
			assertFalse(t.isDone())
			
			status = t.getStatusDetail(true)  //just checking it doesn't throw an exception
			status = t.getStatusDetail(false)
			println "runBasicTaskWithWaits, BasicTask status:\n"+status
			assertTrue(status.toLowerCase().contains("waiting"))
			assertTrue(status.toLowerCase().contains("here my friend"))
			
			data.notify()
		}
		assertEquals("a", t.get())
		status = t.getStatusDetail(false)
	}

	@Test
	public void runMultipleBasicTasks() {
		println "runMultipleBasicTasks"
		data.clear()
		data.put(1, 1)
		BasicExecutionManager em = []
		2.times { em.submit tag:"A", new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		2.times { em.submit tag:"B", new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		int total = 0;
		em.getTaskTags().each { println "tag $it"; em.getTasksWithTag(it).each { println "  BasicTask $it, has "+it.get(); total += it.get() } }
		assertEquals(10, total)
		//now that all have completed:
		assertEquals(5, data.get(1))
	}

	@Test
	public void runMultipleBasicTasksMultipleTags() {
		println "runMultipleBasicTasksWithMultipleTags"
		data.clear()
		data.put(1, 1)
		BasicExecutionManager em = []
		em.submit tag:"A", new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["A","B"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["B","C"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["D"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		int total = 0;
		em.getAllTasks().each { println "  BasicTask $it, has "+it.get(); total += it.get() }
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
		BasicExecutionManager em = []
		BasicTask t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
		t.cancel true
		assertTrue(t.isCancelled())
		assertTrue(t.isDone())
		assertTrue(t.isError())
		assertFalse(t.isBegun())
		em.submit tag:"A", t
		try { t.get(); fail("get should have failed due to cancel"); } catch (CancellationException e) {}
		assertTrue(t.isCancelled())
		assertTrue(t.isDone())
		assertTrue(t.isError())
		assertFalse(t.isBegun())
		
		println "cancelBeforeRun status: "+t.getStatusDetail(false)
		assertTrue(t.getStatusDetail(false).toLowerCase().contains("cancel"))
	}

	@Test
	public void cancelDuringRun() {
		BasicExecutionManager em = []
		BasicTask t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
		assertFalse(t.isCancelled())
		assertFalse(t.isDone())
		assertFalse(t.isError())
		assertFalse(t.isBegun())
		synchronized (data) {
			em.submit tag:"A", t
			data.wait()
			assertTrue(t.isBegun())
			assertFalse(t.isDone())
			t.cancel true
		}
		assertTrue(t.isCancelled())
		assertTrue(t.isError())
		assertTrue(t.isBegun())
		try { t.get(); fail("get should have failed due to cancel"); } catch (CancellationException e) {}
		assertTrue(t.isCancelled())
		assertTrue(t.isDone())
		assertTrue(t.isError())
		assertTrue(t.isBegun())
	}
	
	@Test
	public void cancelAfterRun() {
		BasicExecutionManager em = []
		BasicTask t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
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
		BasicExecutionManager em = []
		BasicTask t = [ { synchronized (data) { data.notify(); data.wait() }; throw new IllegalStateException("Aaargh"); } ]
		
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
	public void fieldsSetForSimpleBasicTask() {
		BasicExecutionManager em = []
		BasicTask t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
		assertEquals(null, t.submittedByTask)
		assertEquals(-1, t.submitTimeUtc)
		assertNull(t.getResult())
		synchronized (data) {
			em.submit tag:"A", t
			data.wait()
		}
		assertTrue(t.submitTimeUtc > 0)
		assertTrue(t.startTimeUtc >= t.submitTimeUtc)
		assertNotNull(t.getResult())
		assertEquals(-1, t.endTimeUtc)
		assertEquals(false, t.isCancelled())
		synchronized (data) { data.notify() }
		assertEquals(42, t.get())
		assertTrue(t.endTimeUtc >= t.startTimeUtc)

		println "BasicTask duration (millis): "+(t.endTimeUtc - t.submitTimeUtc)		
	}

	@Test
	public void fieldsSetForBasicTaskSubmittedBasicTask() {
		//submitted BasicTask B is started by A, and waits for A to complete
		BasicExecutionManager em = []
		BasicTask t = new BasicTask( displayName: "sample", description: "some descr", { em.submit tag:"B", {
				assertEquals(45, em.getTasksWithTag("A").iterator().next().get());
				46 };
			45 } )
		em.submit tag:"A", t

		t.blockUntilEnded()
		
		assertEquals(2, em.getAllTasks().size())
		
		BasicTask tb = em.getTasksWithTag("B").iterator().next();
		assertEquals( 46, tb.get() )
		assertEquals( t, em.getTasksWithTag("A").iterator().next() )
		assertNull( t.submittedByTask )
		
		BasicTask submitter = tb.submittedByTask;
		assertNotNull(submitter)
		assertEquals("sample", submitter.displayName)
		assertEquals("some descr", submitter.description)
		assertEquals(t, submitter)
		
		assertTrue(submitter.submitTimeUtc <= tb.submitTimeUtc)
		assertTrue(submitter.endTimeUtc <= tb.endTimeUtc)
		
		println "BasicTask $tb was submitted by $submitter"
	}

}
