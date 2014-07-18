/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.task;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.Lists;

public class ScheduledExecutionTest {

	public static final Logger log = LoggerFactory.getLogger(ScheduledExecutionTest.class);
	
	@Test
	public void testScheduledTask() throws Exception {
		int PERIOD = 20;
		BasicExecutionManager m = new BasicExecutionManager("mycontextid");
		final AtomicInteger i = new AtomicInteger(0);
		ScheduledTask t = new ScheduledTask(MutableMap.of("delay", 2*PERIOD, "period", PERIOD, "maxIterations", 5), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<Integer>(new Callable<Integer>() {
                    public Integer call() {
            			log.debug("task running: "+Tasks.current()+" "+Tasks.current().getStatusDetail(false));
            			return i.incrementAndGet();
                    }});
            }});
	
		log.info("submitting {} {}", t, t.getStatusDetail(false));
		m.submit(t);
		log.info("submitted {} {}", t, t.getStatusDetail(false));
		Integer interimResult = (Integer) t.get();
		log.info("done one ({}) {} {}", new Object[] {interimResult, t, t.getStatusDetail(false)});
		assertTrue(i.get() > 0, "i="+i);
		t.blockUntilEnded();
		Integer finalResult = (Integer) t.get();
		log.info("ended ({}) {} {}", new Object[] {finalResult, t, t.getStatusDetail(false)});
		assertEquals(finalResult, (Integer)5);
		assertEquals(i.get(), 5);
	}

	/** like testScheduledTask but the loop is terminated by the task itself adjusting the period */
	@Test
	public void testScheduledTaskSelfEnding() throws Exception {
		int PERIOD = 20;
		BasicExecutionManager m = new BasicExecutionManager("mycontextid");
		final AtomicInteger i = new AtomicInteger(0);
		ScheduledTask t = new ScheduledTask(MutableMap.of("delay", 2*PERIOD, "period", PERIOD), new Callable<Task<?>>() {
		    public Task<?> call() throws Exception {
		        return new BasicTask<Integer>(new Callable<Integer>() {
		            public Integer call() {
		                ScheduledTask submitter = (ScheduledTask) ((BasicTask)Tasks.current()).submittedByTask;
            			if (i.get() >= 4) submitter.period = null;
            			log.info("task running ("+i+"): "+Tasks.current()+" "+Tasks.current().getStatusDetail(false));
            			return i.incrementAndGet();
		            }});
		    }});
	
		log.info("submitting {} {}", t, t.getStatusDetail(false));
		m.submit(t);
		log.info("submitted {} {}", t, t.getStatusDetail(false));
		Integer interimResult = (Integer) t.get();
		log.info("done one ({}) {} {}", new Object[] {interimResult, t, t.getStatusDetail(false)});
		assertTrue(i.get() > 0);
		t.blockUntilEnded();
		Integer finalResult = (Integer) t.get();
		log.info("ended ({}) {} {}", new Object[] {finalResult, t, t.getStatusDetail(false)});
		assertEquals(finalResult, (Integer)5);
		assertEquals(i.get(), 5);
	}

	@Test
	public void testScheduledTaskCancelEnding() throws Exception {
		int PERIOD = 20;
		BasicExecutionManager m = new BasicExecutionManager("mycontextid");
		final AtomicInteger i = new AtomicInteger();
		ScheduledTask t = new ScheduledTask(MutableMap.of("delay", 2*PERIOD, "period", PERIOD), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<Integer>(new Callable<Integer>() {
                    public Integer call() {
            			log.info("task running ("+i+"): "+Tasks.current()+" "+Tasks.current().getStatusDetail(false));
            			ScheduledTask submitter = (ScheduledTask) ((BasicTask)Tasks.current()).submittedByTask;
            			i.incrementAndGet();
            			if (i.get() >= 5) submitter.cancel();
            			return i.get();
                    }});
            }});
	
		log.info("submitting {} {}", t, t.getStatusDetail(false));
		m.submit(t);
		log.info("submitted {} {}", t, t.getStatusDetail(false));
		Integer interimResult = (Integer) t.get();
		log.info("done one ({}) {} {}", new Object[] {interimResult, t, t.getStatusDetail(false)});
		assertTrue(i.get() > 0);
		t.blockUntilEnded();
//		int finalResult = t.get()
		log.info("ended ({}) {} {}", new Object[] {i, t, t.getStatusDetail(false)});
//		assertEquals(finalResult, 5)
		assertEquals(i.get(), 5);
	}

    @Test(groups="Integration")
    public void testScheduledTaskTakesLongerThanPeriod() throws Exception {
        final int PERIOD = 1;
        final int SLEEP_TIME = 100;
        final int EARLY_RETURN_GRACE = 10;
        BasicExecutionManager m = new BasicExecutionManager("mycontextid");
        final List<Long> execTimes = new CopyOnWriteArrayList<Long>();
        
        ScheduledTask t = new ScheduledTask(MutableMap.of("delay", PERIOD, "period", PERIOD), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<Void>(new Runnable() {
                    public void run() {
                        execTimes.add(System.currentTimeMillis());
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        }
                    }});
            }});
    
        m.submit(t);
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(execTimes.size() > 3, "size="+execTimes.size());
            }});
        
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
