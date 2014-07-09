package brooklyn.util.task;

import static org.testng.Assert.*

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.management.ExecutionManager
import brooklyn.management.Task
import brooklyn.test.TestUtils

import com.google.common.base.Throwables

/**
 * Test the operation of the {@link BasicTask} class.
 *
 * TODO clarify test purpose
 */
public class BasicTaskExecutionTest {
    private static final Logger log = LoggerFactory.getLogger(BasicTaskExecutionTest.class)
 
    private static final int TIMEOUT_MS = 10*1000
    
    private BasicExecutionManager em;
    private Map data;

    @BeforeMethod
    public void setUp() {
        em = new BasicExecutionManager("mycontext");
//        assertTrue em.allTasks.isEmpty()
        data = Collections.synchronizedMap(new HashMap())
        data.clear()
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
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
        CountDownLatch signalStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        data.clear()
        BasicTask t = [ {
            def result = data.put(1, "b")
            signalStarted.countDown();
            assertTrue(allowCompletion.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            result
        } ]
        data.put(1, "a")

        BasicTask t2 = em.submit tag:"A", t
        assertEquals(t, t2)
        assertFalse(t.isDone())
        
        assertTrue(signalStarted.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals("b", data.get(1))
        assertFalse(t.isDone())
        
        log.debug "runBasicTaskWithWaits, BasicTask status: {}", t.getStatusDetail(false)
        
        TestUtils.executeUntilSucceeds { t.getStatusDetail(false).toLowerCase().contains("waiting") }
        // "details="+t.getStatusDetail(false))
        
        allowCompletion.countDown();
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
        Collection<Task> tasks = []
        tasks += em.submit tag:"A", new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
        tasks += em.submit tags:["A","B"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
        tasks += em.submit tags:["B","C"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
        tasks += em.submit tags:["D"], new BasicTask({ synchronized(data) { data.put(1, data.get(1)+1) } })
        int total = 0;

        tasks.each { Task t ->
                log.debug "BasicTask {}, has {}", t, t.get()
                total += t.get()
            }
        assertEquals(10, total)
 
        //now that all have completed:
        assertEquals data.get(1), 5
        assertEquals em.getTasksWithTag("A").size(), 2
        assertEquals em.getTasksWithAnyTag(["A"]).size(), 2
        assertEquals em.getTasksWithAllTags(["A"]).size(), 2

        assertEquals em.getTasksWithAnyTag(["A", "B"]).size(), 3
        assertEquals em.getTasksWithAllTags(["A", "B"]).size(), 1
        assertEquals em.getTasksWithAllTags(["B", "C"]).size(), 1
        assertEquals em.getTasksWithAnyTag(["A", "D"]).size(), 3
    }

    @Test
    public void testGetTaskById() {
        Task t = new BasicTask({ /*no-op*/ })
        em.submit tag:"A",t
        assertEquals(em.getTask(t.id), t);
    }

    @Test
    public void testRetrievingTasksWithTagsReturnsExpectedTask() {
        Task t = new BasicTask({ /*no-op*/ })
        em.submit tag:"A",t
        t.get();

        assertEquals(em.getTasksWithTag("A"), [t]);
        assertEquals(em.getTasksWithAnyTag(["A"]), [t]);
        assertEquals(em.getTasksWithAnyTag(["A","B"]), [t]);
        assertEquals(em.getTasksWithAllTags(["A"]), [t]);
    }

    @Test
    public void testRetrievingTasksWithTagsExcludesNonMatchingTasks() {
        Task t = new BasicTask({ /*no-op*/ })
        em.submit tag:"A",t
        t.get();

        assertEquals(em.getTasksWithTag("B"), []);
        assertEquals(em.getTasksWithAnyTag(["B"]), []);
        assertEquals(em.getTasksWithAllTags(["A","B"]), []);
    }
    
    @Test
    public void testRetrievingTasksWithMultipleTags() {
        Task t = new BasicTask({ /*no-op*/ })
        em.submit tags:["A","B"], t
        t.get();

        assertEquals(em.getTasksWithTag("A"), [t]);
        assertEquals(em.getTasksWithTag("B"), [t]);
        assertEquals(em.getTasksWithAnyTag(["A"]), [t]);
        assertEquals(em.getTasksWithAnyTag(["B"]), [t]);
        assertEquals(em.getTasksWithAnyTag(["A","B"]), [t]);
        assertEquals(em.getTasksWithAllTags(["A","B"]), [t]);
        assertEquals(em.getTasksWithAllTags(["A"]), [t]);
        assertEquals(em.getTasksWithAllTags(["B"]), [t]);
    }

    // ENGR-1796: if nothing matched first tag, then returned whatever matched second tag!
    @Test
    public void testRetrievingTasksWithAllTagsWhenFirstNotMatched() {
        Task t = new BasicTask({ /*no-op*/ })
        em.submit tags:["A"], t
        t.get();

        assertEquals(em.getTasksWithAllTags(["not_there","A"]), []);
    }
    
    @Test
    public void testRetrievedTasksIncludesTasksInProgress() {
        CountDownLatch runningLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        Task t = new BasicTask({ runningLatch.countDown(); finishLatch.await() })
        em.submit tags:["A"], t
        
        try {
            runningLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    
            assertEquals(em.getTasksWithTag("A"), [t]);
        } finally {
            finishLatch.countDown();
        }
    }
    
    @Test
    public void cancelBeforeRun() {
        CountDownLatch blockForever = new CountDownLatch(1);
        
        BasicTask t = [ { blockForever.await(); return 42 } ]
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
        CountDownLatch signalStarted = new CountDownLatch(1);
        CountDownLatch blockForever = new CountDownLatch(1);
        
        BasicTask t = [ { synchronized (data) { signalStarted.countDown(); blockForever.await() }; return 42 } ]
        em.submit tag:"A", t
        assertFalse(t.isCancelled())
        assertFalse(t.isDone())
        assertFalse(t.isError())
        
        assertTrue(signalStarted.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        t.cancel true
        
        assertTrue(t.isCancelled())
        assertTrue(t.isError())
        try { t.get(); fail("get should have failed due to cancel"); } catch (CancellationException e) {}
        assertTrue(t.isCancelled())
        assertTrue(t.isDone())
        assertTrue(t.isError())
    }
    
    @Test
    public void cancelAfterRun() {
        BasicTask t = [ { return 42 } ]
        em.submit tag:"A", t

        assertEquals(42, t.get());
        t.cancel true
        assertFalse(t.isCancelled())
        assertFalse(t.isError())
        assertTrue(t.isDone())
    }
    
    @Test
    public void errorDuringRun() {
        BasicTask t = [ { throw new IllegalStateException("Aaargh"); } ]
        
        em.submit tag:"A", t
        
        try { t.get(); fail("get should have failed due to error"); } catch (Exception eo) { Exception e = Throwables.getRootCause(eo); assertEquals("Aaargh", e.getMessage()) }
        
        assertFalse(t.isCancelled())
        assertTrue(t.isError())
        assertTrue(t.isDone())
        
        log.debug "errorDuringRun status: {}", t.getStatusDetail(false)
        assertTrue(t.getStatusDetail(false).contains("Aaargh"), "details="+t.getStatusDetail(false))
    }

    @Test
    public void fieldsSetForSimpleBasicTask() {
        CountDownLatch signalStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        
        BasicTask t = [ { signalStarted.countDown(); allowCompletion.await(); return 42 } ]
        assertEquals(null, t.submittedByTask)
        assertEquals(-1, t.submitTimeUtc)
        assertNull(t.getResult())

        em.submit tag:"A", t
        assertTrue(signalStarted.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        
        assertTrue(t.submitTimeUtc > 0)
        assertTrue(t.startTimeUtc >= t.submitTimeUtc)
        assertNotNull(t.getResult())
        assertEquals(-1, t.endTimeUtc)
        assertEquals(false, t.isCancelled())
        
        allowCompletion.countDown()
        assertEquals(42, t.get())
        assertTrue(t.endTimeUtc >= t.startTimeUtc)

        log.debug "BasicTask duration (millis): {}", (t.endTimeUtc - t.submitTimeUtc)
    }

    @Test
    public void fieldsSetForBasicTaskSubmittedBasicTask() {
        //submitted BasicTask B is started by A, and waits for A to complete
        BasicTask t = new BasicTask( displayName: "sample", description: "some descr", {
                em.submit tag:"B", {
                assertEquals(45, em.getTasksWithTag("A").iterator().next().get());
                46 };
            45 } )
        em.submit tag:"A", t

        t.blockUntilEnded()
 
//        assertEquals em.getAllTasks().size(), 2
        
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
