package brooklyn.util.task

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test
import static org.testng.Assert.*

import brooklyn.util.internal.TimeExtras

public class ScheduledExecutionTest {

	public static final Logger log = LoggerFactory.getLogger(ScheduledExecutionTest.class);
	
	static { TimeExtras.init() }
	
	@Test
	public void testScheduledTask() {
		int PERIOD = 20;
		BasicExecutionManager m = new BasicExecutionManager();
		int i=0;
		def t = new ScheduledTask(delay: 2*PERIOD*TimeUnit.MILLISECONDS, period: PERIOD*TimeUnit.MILLISECONDS, maxIterations: 5, { new BasicTask({
			log.debug "task running: "+BasicExecutionManager.currentTask+" "+BasicExecutionManager.currentTask.getStatusDetail(false)
			++i; 
		}) } );
	
		log.info "submitting {} {}", t, t.getStatusDetail(false)
		m.submit(t);
		log.info "submitted {} {}", t, t.getStatusDetail(false)
		int interimResult = t.get()
		log.info "done one ({}) {} {}", interimResult, t, t.getStatusDetail(false)
		assertTrue(i>0)
		t.blockUntilEnded()
		int finalResult = t.get()
		log.info "ended ({}) {} {}", finalResult, t, t.getStatusDetail(false)
		assertEquals(finalResult, 5)
		assertEquals(i, 5)
	}

	/** like testScheduledTask but the loop is terminated by the task itself adjusting the period */
	@Test
	public void testScheduledTaskSelfEnding() {
		int PERIOD = 20;
		BasicExecutionManager m = new BasicExecutionManager();
		int i=0;
		def t = new ScheduledTask(delay: 2*PERIOD*TimeUnit.MILLISECONDS, period: PERIOD*TimeUnit.MILLISECONDS, { new BasicTask({
			if (i>=4) BasicExecutionManager.currentTask.submittedByTask.period = null
			log.info "task running (${i}): "+BasicExecutionManager.currentTask+" "+BasicExecutionManager.currentTask.getStatusDetail(false)
			++i;
		}) } );
	
		log.info "submitting {} {}", t, t.getStatusDetail(false)
		m.submit(t);
		log.info "submitted {} {}", t, t.getStatusDetail(false)
		int interimResult = t.get()
		log.info "done one ({}) {} {}", interimResult, t, t.getStatusDetail(false)
		assertTrue(i>0)
		t.blockUntilEnded()
		int finalResult = t.get()
		log.info "ended ({}) {} {}", finalResult, t, t.getStatusDetail(false)
		assertEquals(finalResult, 5)
		assertEquals(i, 5)
	}

	@Test
	public void testScheduledTaskCancelEnding() {
		int PERIOD = 20;
		BasicExecutionManager m = new BasicExecutionManager();
		int i=0;
		def t = new ScheduledTask(delay: 2*PERIOD*TimeUnit.MILLISECONDS, period: PERIOD*TimeUnit.MILLISECONDS, { new BasicTask({
			log.info "task running (${i}): "+BasicExecutionManager.currentTask+" "+BasicExecutionManager.currentTask.getStatusDetail(false)
			++i;
			if (i>=5) BasicExecutionManager.currentTask.submittedByTask.cancel()
			i
		}) } );
	
		log.info "submitting {} {}", t, t.getStatusDetail(false)
		m.submit(t);
		log.info "submitted {} {}", t, t.getStatusDetail(false)
		int interimResult = t.get()
		log.info "done one ({}) {} {}", interimResult, t, t.getStatusDetail(false)
		assertTrue(i>0)
		t.blockUntilEnded()
//		int finalResult = t.get()
		log.info "ended ({}) {} {}", i, t, t.getStatusDetail(false)
//		assertEquals(finalResult, 5)
		assertEquals(i, 5)
	}

}
