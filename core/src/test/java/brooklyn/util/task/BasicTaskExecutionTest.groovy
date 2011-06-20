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
		BasicTask t = [ {
			synchronized(data) {
				def result = data.put(1, "b")
				data.notify()
				data.wait()
				result
			}
		} ]
		data.put(1, "a")
		BasicExecutionManager em = []
		synchronized (data) {
			BasicTask t2 = em.submit tag:"A", t
			assertEquals(t, t2)
			assertFalse(t.isDone())
			
			assertEquals("a", data.get(1))
			data.wait()
			assertEquals("b", data.get(1))
			assertFalse(t.isDone())
			
			println "runBasicTaskWithWaits, BasicTask status:\n"+t.getStatusDetail(true)
			assertTrue(t.getStatusDetail(false).toLowerCase().contains("waiting"))
			
			data.notify()
		}
		assertEquals("a", t.get())
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
		em.getBasicTaskTags().each { println "tag $it"; em.getBasicTasksWithTag(it).each { println "  BasicTask $it, has "+it.get(); total += it.get() } }
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
		em.getAllBasicTasks().each { println "  BasicTask $it, has "+it.get(); total += it.get() }
		assertEquals(10, total)
		//now that all have completed:
		assertEquals(5, data.get(1))
		assertEquals(2, em.getBasicTasksWithTag("A").size())
		assertEquals(2, em.getBasicTasksWithAnyTag(["A"]).size())
		assertEquals(2, em.getBasicTasksWithAllTags(["A"]).size())

		assertEquals(3, em.getBasicTasksWithAnyTag(["A", "B"]).size())
		assertEquals(1, em.getBasicTasksWithAllTags(["A", "B"]).size())
		assertEquals(1, em.getBasicTasksWithAllTags(["B", "C"]).size())
		assertEquals(3, em.getBasicTasksWithAnyTag(["A", "D"]).size())
	}

	@Test
	public void cancelBeforeRun() {
		BasicExecutionManager em = []
		BasicTask t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
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
		BasicExecutionManager em = []
		BasicTask t = [ { synchronized (data) { data.notify(); data.wait() }; return 42 } ]
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
		assertEquals(null, t.submittedByBasicTask)
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

		println "BasicTask duration (millis): "+(t.endTimeUtc - t.submitTimeUtc)		
	}

	@Test
	public void fieldsSetForBasicTaskSubmittedBasicTask() {
		//submitted BasicTask B is started by A, and waits for A to complete
		BasicExecutionManager em = []
		BasicTask t = new BasicTask( displayName: "sample", description: "some descr", { em.submit tag:"B", {
				assertEquals(45, em.getBasicTasksWithTag("A").iterator().next().get());
				46 };
			45 } )
		em.submit tag:"A", t

		t.blockUntilEnded()
		
		assertEquals(2, em.getAllBasicTasks().size())
		
		BasicTask tb = em.getBasicTasksWithTag("B").iterator().next();
		assertEquals( 46, tb.get() )
		assertEquals( t, em.getBasicTasksWithTag("A").iterator().next() )
		assertNull( t.submittedByBasicTask )
		
		BasicTask submitter = tb.submittedByBasicTask;
		assertNotNull(submitter)
		assertEquals("sample", submitter.displayName)
		assertEquals("some descr", submitter.description)
		assertEquals(t, submitter)
		
		assertTrue(submitter.submitTimeUtc <= tb.submitTimeUtc)
		assertTrue(submitter.endTimeUtc <= tb.endTimeUtc)
		
		println "BasicTask $tb was submitted by $submitter"
	}

}
