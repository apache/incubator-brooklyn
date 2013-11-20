package brooklyn.util.task

import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.test.TestUtils
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.Lists

public class ScheduledExecutionTest {

	public static final Logger log = LoggerFactory.getLogger(ScheduledExecutionTest.class);
	
	static { TimeExtras.init() }
	
	@Test
	public void testScheduledTask() {
		int PERIOD = 20;
		BasicExecutionManager m = new BasicExecutionManager();
		int i=0;
		def t = new ScheduledTask(delay: 2*PERIOD*TimeUnit.MILLISECONDS, period: PERIOD*TimeUnit.MILLISECONDS, maxIterations: 5, { new BasicTask({
			log.debug "task running: "+Tasks.current()+" "+Tasks.current().getStatusDetail(false)
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
			if (i>=4) Tasks.current().submittedByTask.period = null
			log.info "task running (${i}): "+Tasks.current()+" "+Tasks.current().getStatusDetail(false)
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
			log.info "task running (${i}): "+Tasks.current()+" "+Tasks.current().getStatusDetail(false)
			++i;
			if (i>=5) Tasks.current().submittedByTask.cancel()
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

    @Test
    public void testScheduledTaskTakesLongerThanPeriod() {
        final int PERIOD = 1;
        final int SLEEP_TIME = 100;
        final int EARLY_RETURN_GRACE = 10;
        BasicExecutionManager m = new BasicExecutionManager();
        final List<Long> execTimes = new CopyOnWriteArrayList<Long>();
        
        def t = new ScheduledTask(delay: PERIOD*TimeUnit.MILLISECONDS, period: PERIOD*TimeUnit.MILLISECONDS, { new BasicTask({
            execTimes.add(System.currentTimeMillis());
            Thread.sleep(100);
        }) } );
    
        m.submit(t);
        
        TestUtils.executeUntilSucceeds {
            execTimes.size() > 3;
        }
        
        List<Long> timeDiffs = Lists.newArrayList();
        long prevExecTime = -1;
        for (Long execTime : execTimes) {
            if (prevExecTime == -1) {
                prevExecTime = execTime;
            } else {
                timeDiffs.add(execTime - prevExecTime);
                prevExecTime = execTime;
            }
        }
        
        for (Long timeDiff : timeDiffs) {
            if (timeDiff < (SLEEP_TIME - EARLY_RETURN_GRACE)) fail("timeDiffs="+timeDiffs+"; execTimes="+execTimes);
        }
    }
}
