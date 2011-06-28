package brooklyn.util.task;

import static org.testng.Assert.*

import java.util.Map
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.management.ExecutionManager
import brooklyn.management.Task
import brooklyn.util.internal.LanguageUtils

/**
 * Test the operation of the {@link BasicTask} class.
 * 
 * TODO clarify test purpose
 */
public class BasicTaskExecutionTest {
    private static final Logger log = LoggerFactory.getLogger(BasicTaskExecutionTest.class)
 
    private ExecutionManager em
	private Map data

    @BeforeMethod
    public void setUp() {
		em = new BasicExecutionManager()
		assertTrue em.allTasks.isEmpty()
        data = Collections.synchronizedMap(new HashMap())
        data.clear()
    }
	
	@Test
	public void runSimpleBasicTask() {
		data.clear()
		BasicTask t = [ { data.put(1, "b") } ] 
		data.put(1, "a")
		BasicTask t2 = em.submit tag:"A", t
		assertEquals("a", t.get())
		assertEquals("b", data.get(1))
	}
	
	@Test
	public void runSimpleRunnable() {
		data.clear()
		data.put(1, "a")
		BasicTask t = em.submit tag:"A", new Runnable() { public void run() { data.put(1, "b") } }
		assertEquals(null, t.get())
		assertEquals("b", data.get(1))
	}

	@Test
	public void runSimpleCallable() {
		data.clear()
		data.put(1, "a")
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
		synchronized (data) {
			BasicTask t2 = em.submit tag:"A", t
			assertEquals(t, t2)
			assertFalse(t.isDone())
			
			assertEquals("a", data.get(1))
			data.wait()
			assertEquals("b", data.get(1))
			assertFalse(t.isDone())
			
			log.debug "runBasicTaskWithWaits, BasicTask status: {}", t.getStatusDetail(false)
			assertTrue(t.getStatusDetail(false).toLowerCase().contains("waiting"))
			
			data.notify()
		}
		assertEquals("a", t.get())
	}

	@Test
	public void runMultipleBasicTasks() {
		data.clear()
		data.put(1, 1)
		BasicExecutionManager em = []
		2.times { em.submit tag:"A", new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		2.times { em.submit tag:"B", new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } }) }
		int total = 0;
		em.getTaskTags().each {
                log.debug "tag {}", it
                em.getTasksWithTag(it).each {
                    log.debug "BasicTask {}, has {}", it, it.get()
                    total += it.get()
                }
            }
		assertEquals(10, total)
		//now that all have completed:
		assertEquals(5, data.get(1))
	}

	@Test
	public void runMultipleBasicTasksMultipleTags() {
		data.clear()
		data.put(1, 1)
		em.submit tag:"A", new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["A","B"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["B","C"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		em.submit tags:["D"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
		int total = 0;
		em.getAllTasks().each { Task t ->
                log.debug "BasicTask {}, has {}", t, t.get()
                total += t.get()
            }
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
		
		log.debug "cancelBeforeRun status: {}", t.getStatusDetail(false)
		assertTrue(t.getStatusDetail(false).toLowerCase().contains("cancel"))
	}

	@Test
	public void cancelDuringRun() {
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
		
		log.debug "errorDuringRun status: {}", t.getStatusDetail(false)
		assertTrue(t.getStatusDetail(false).contains("Aaargh"))
	}

	@Test
	public void fieldsSetForSimpleBasicTask() {
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

		log.debug "BasicTask duration (millis): {}", (t.endTimeUtc - t.submitTimeUtc)		
	}

	@Test
	public void fieldsSetForBasicTaskSubmittedBasicTask() {
		//submitted BasicTask B is started by A, and waits for A to complete
		BasicTask t = new BasicTask( displayName: "sample", description: "some descr", { em.submit tag:"B", {
				assertEquals(45, em.getTasksWithTag("A").iterator().next().get());
				46 };
			45 } )
		em.submit tag:"A", t

		t.blockUntilEnded()
 
		assertEquals em.getAllTasks().size(), 2
		
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
		
		log.debug "BasicTask {} was submitted by {}", tb, submitter
	}
}
