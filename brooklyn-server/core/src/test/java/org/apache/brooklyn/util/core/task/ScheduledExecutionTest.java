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
package org.apache.brooklyn.util.core.task;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.RuntimeInterruptedException;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

@SuppressWarnings({"unchecked","rawtypes"})
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

    @Test
    public void testScheduledTaskCancelledIfExceptionThrown() throws Exception {
        BasicExecutionManager m = new BasicExecutionManager("mycontextid");
        final AtomicInteger calls = new AtomicInteger(0);
        ScheduledTask t = new ScheduledTask(MutableMap.of("period", Duration.ONE_MILLISECOND, "maxIterations", 5), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<>(new Callable<Integer>() {
                    public Integer call() {
                        calls.incrementAndGet();
                        throw new RuntimeException("boo");
                    }});
            }});

        m.submit(t);
        Runnable callsIsOne = new Runnable() {
            @Override public void run() {
                if (calls.get() != 1) {
                    throw new RuntimeException("not yet");
                }
            }

        };
        Asserts.succeedsEventually(callsIsOne);
        Asserts.succeedsContinually(callsIsOne);
    }

    @Test
    public void testScheduledTaskResubmittedIfExceptionThrownAndCancelOnExceptionFalse() {
        BasicExecutionManager m = new BasicExecutionManager("mycontextid");
        final AtomicInteger calls = new AtomicInteger(0);
        ScheduledTask t = new ScheduledTask(MutableMap.of("period", Duration.ONE_MILLISECOND, "maxIterations", 5, "cancelOnException", false), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<>(new Callable<Integer>() {
                    public Integer call() {
                        calls.incrementAndGet();
                        throw new RuntimeException("boo");
                    }});
            }});

        m.submit(t);
        t.blockUntilEnded();
        assertEquals(calls.get(), 5, "Expected task to be resubmitted despite throwing an exception");
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
                        ScheduledTask submitter = (ScheduledTask) ((BasicTask)Tasks.current()).getSubmittedByTask();
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
        Duration PERIOD = Duration.millis(20);
        BasicExecutionManager m = new BasicExecutionManager("mycontextid");
        final AtomicInteger i = new AtomicInteger();
        ScheduledTask t = new ScheduledTask(MutableMap.of("delay", PERIOD.times(2), "period", PERIOD), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<Integer>(new Callable<Integer>() {
                    public Integer call() {
                        log.info("task running ("+i+"): "+Tasks.current()+" "+Tasks.current().getStatusDetail(false));
                        ScheduledTask submitter = (ScheduledTask) ((BasicTask)Tasks.current()).getSubmittedByTask();
                        i.incrementAndGet();
                        if (i.get() >= 5) submitter.cancel();
                        return i.get();
                    }});
            }});
    
        log.info(JavaClassNames.niceClassAndMethod()+" - submitting {} {}", t, t.getStatusDetail(false));
        m.submit(t);
        log.info("submitted {} {}", t, t.getStatusDetail(false));
        Integer interimResult = (Integer) t.get();
        log.info("done one ({}) {} {}", new Object[] {interimResult, t, t.getStatusDetail(false)});
        assertTrue(i.get() > 0);
        t.blockUntilEnded();
//      int finalResult = t.get()
        log.info("ended ({}) {} {}", new Object[] {i, t, t.getStatusDetail(false)});
//      assertEquals(finalResult, 5)
        assertEquals(i.get(), 5);
    }

    @Test(groups="Integration")
    public void testScheduledTaskCancelOuter() throws Exception {
        final Duration PERIOD = Duration.millis(20);
        final Duration CYCLE_DELAY = Duration.ONE_SECOND;
        // this should be enough to start the next cycle, but not so much that the cycle ends;
        // and enough that when a task is interrupted it terminates within this period
        final Duration SMALL_FRACTION_OF_CYCLE_DELAY = PERIOD.add(CYCLE_DELAY.multiply(0.1));
        
        BasicExecutionManager m = new BasicExecutionManager("mycontextid");
        final AtomicInteger i = new AtomicInteger();
        ScheduledTask t = new ScheduledTask(MutableMap.of("delay", PERIOD.times(2), "period", PERIOD), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<Integer>(new Callable<Integer>() {
                    public Integer call() {
                        log.info("task running ("+i+"): "+Tasks.current()+" "+Tasks.current().getStatusDetail(false));
                        Time.sleep(CYCLE_DELAY);
                        i.incrementAndGet();
                        return i.get();
                    }});
            }});
    
        log.info(JavaClassNames.niceClassAndMethod()+" - submitting {} {}", t, t.getStatusDetail(false));
        m.submit(t);
        log.info("submitted {} {}", t, t.getStatusDetail(false));
        Integer interimResult = (Integer) t.get();
        log.info("done one ({}) {} {}", new Object[] {interimResult, t, t.getStatusDetail(false)});
        assertEquals(i.get(), 1);
        
        Time.sleep(SMALL_FRACTION_OF_CYCLE_DELAY);
        assertEquals(t.get(), 2);
        
        Time.sleep(SMALL_FRACTION_OF_CYCLE_DELAY);
        Stopwatch timer = Stopwatch.createUnstarted();
        t.cancel(true);
        t.blockUntilEnded();
//      int finalResult = t.get()
        log.info("blocked until ended ({}) {} {}, in {}", new Object[] {i, t, t.getStatusDetail(false), Duration.of(timer)});
        try {
            t.get();
            Assert.fail("Should have failed getting result of cancelled "+t);
        } catch (Exception e) {
            /* expected */
        }
        assertEquals(i.get(), 2);
        log.info("ended ({}) {} {}, in {}", new Object[] {i, t, t.getStatusDetail(false), Duration.of(timer)});
        Assert.assertTrue(Duration.of(timer).isShorterThan(SMALL_FRACTION_OF_CYCLE_DELAY));
    }

    @Test(groups="Integration")
    public void testScheduledTaskCancelInterrupts() throws Exception {
        final Duration PERIOD = Duration.millis(20);
        final Duration CYCLE_DELAY = Duration.ONE_SECOND;
        // this should be enough to start the next cycle, but not so much that the cycle ends;
        // and enough that when a task is interrupted it terminates within this period
        final Duration SMALL_FRACTION_OF_CYCLE_DELAY = PERIOD.add(CYCLE_DELAY.multiply(0.1));
        
        BasicExecutionManager m = new BasicExecutionManager("mycontextid");
        final Semaphore interruptedSemaphore = new Semaphore(0);
        final AtomicInteger i = new AtomicInteger();
        ScheduledTask t = new ScheduledTask(MutableMap.of("delay", PERIOD.times(2), "period", PERIOD), new Callable<Task<?>>() {
            public Task<?> call() throws Exception {
                return new BasicTask<Integer>(new Callable<Integer>() {
                    public Integer call() {
                        try {
                            log.info("task running ("+i+"): "+Tasks.current()+" "+Tasks.current().getStatusDetail(false));
                            Time.sleep(CYCLE_DELAY);
                            i.incrementAndGet();
                            return i.get();
                        } catch (RuntimeInterruptedException e) {
                            interruptedSemaphore.release();
                            throw Exceptions.propagate(e);
                        }
                    }});
            }});
    
        log.info(JavaClassNames.niceClassAndMethod()+" - submitting {} {}", t, t.getStatusDetail(false));
        m.submit(t);
        log.info("submitted {} {}", t, t.getStatusDetail(false));
        Integer interimResult = (Integer) t.get();
        log.info("done one ({}) {} {}", new Object[] {interimResult, t, t.getStatusDetail(false)});
        assertEquals(i.get(), 1);
        
        Time.sleep(SMALL_FRACTION_OF_CYCLE_DELAY);
        assertEquals(t.get(), 2);
        
        Time.sleep(SMALL_FRACTION_OF_CYCLE_DELAY);
        Stopwatch timer = Stopwatch.createUnstarted();
        t.cancel(true);
        t.blockUntilEnded();
//      int finalResult = t.get()
        log.info("blocked until ended ({}) {} {}, in {}", new Object[] {i, t, t.getStatusDetail(false), Duration.of(timer)});
        try {
            t.get();
            Assert.fail("Should have failed getting result of cancelled "+t);
        } catch (Exception e) {
            /* expected */
        }
        assertEquals(i.get(), 2);
        Assert.assertTrue(interruptedSemaphore.tryAcquire(1, SMALL_FRACTION_OF_CYCLE_DELAY.toMilliseconds(), TimeUnit.MILLISECONDS), "child thread was not interrupted");
        log.info("ended ({}) {} {}, in {}", new Object[] {i, t, t.getStatusDetail(false), Duration.of(timer)});
        Assert.assertTrue(Duration.of(timer).isShorterThan(SMALL_FRACTION_OF_CYCLE_DELAY));
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
