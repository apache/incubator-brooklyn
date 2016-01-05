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

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;
import org.apache.brooklyn.util.core.task.BasicTask;
import org.apache.brooklyn.util.core.task.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;

/**
 * Test the operation of the {@link BasicTask} class.
 *
 * TODO clarify test purpose
 */
public class BasicTaskExecutionPerformanceTest {
    private static final Logger log = LoggerFactory.getLogger(BasicTaskExecutionPerformanceTest.class);
 
    private static final int TIMEOUT_MS = 10*1000;
    
    private BasicExecutionManager em;

    public static final int MAX_OVERHEAD_MS = 1500; // was 750ms but saw 1.3s on buildhive
    public static final int EARLY_RETURN_GRACE = 25; // saw 13ms early return on jenkins!

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        em = new BasicExecutionManager("mycontext");
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testScheduledTaskExecutedAfterDelay() throws Exception {
        int delay = 100;
        final CountDownLatch latch = new CountDownLatch(1);
        
        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<?> call() {
                return new BasicTask<Void>(new Runnable() {
                    @Override public void run() {
                        latch.countDown();
                    }});
            }};
        ScheduledTask t = new ScheduledTask(taskFactory).delay(delay);

        Stopwatch stopwatch = Stopwatch.createStarted();
        em.submit(t);
        
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        long actualDelay = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        
        assertTrue(actualDelay > (delay-EARLY_RETURN_GRACE), "actualDelay="+actualDelay+"; delay="+delay);
        assertTrue(actualDelay < (delay+MAX_OVERHEAD_MS), "actualDelay="+actualDelay+"; delay="+delay);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testScheduledTaskExecutedAtRegularPeriod() throws Exception {
        final int period = 100;
        final int numTimestamps = 4;
        final CountDownLatch latch = new CountDownLatch(1);
        final List<Long> timestamps = Collections.synchronizedList(Lists.<Long>newArrayList());
        final Stopwatch stopwatch = Stopwatch.createStarted();
        
        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<?> call() {
                return new BasicTask<Void>(new Runnable() {
                    @Override public void run() {
                        timestamps.add(stopwatch.elapsed(TimeUnit.MILLISECONDS));
                        if (timestamps.size() >= numTimestamps) latch.countDown();
                    }});
            }};
        ScheduledTask t = new ScheduledTask(taskFactory).delay(1).period(period);
        em.submit(t);
        
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        
        synchronized (timestamps) {
            long prev = timestamps.get(0);
            for (long timestamp : timestamps.subList(1, timestamps.size())) {
                assertTrue(timestamp > prev+period-EARLY_RETURN_GRACE, "timestamps="+timestamps);
                assertTrue(timestamp < prev+period+MAX_OVERHEAD_MS, "timestamps="+timestamps);
                prev = timestamp;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCanCancelScheduledTask() throws Exception {
        final int period = 1;
        final long checkPeriod = 250;
        final List<Long> timestamps = Collections.synchronizedList(Lists.<Long>newArrayList());
        
        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<?> call() {
                return new BasicTask<Void>(new Runnable() {
                    @Override public void run() {
                        timestamps.add(System.currentTimeMillis());
                    }});
            }};
        ScheduledTask t = new ScheduledTask(taskFactory).period(period);
        em.submit(t);

        t.cancel();
        long cancelTime = System.currentTimeMillis();
        int countImmediatelyAfterCancel = timestamps.size();
        Thread.sleep(checkPeriod);
        int countWellAfterCancel = timestamps.size();

        // should have at most 1 more execution after cancel
        log.info("testCanCancelScheduledTask saw "+countImmediatelyAfterCancel+" then cancel then "+countWellAfterCancel+" total");                
        assertTrue(countWellAfterCancel - countImmediatelyAfterCancel <= 2, "timestamps="+timestamps+"; cancelTime="+cancelTime);
    }

    // Previously, when we used a CopyOnWriteArraySet, performance for submitting new tasks was
    // terrible, and it degraded significantly as the number of previously executed tasks increased
    // (e.g. 9s for first 1000; 26s for next 1000; 42s for next 1000).
    @Test
    public void testExecutionManagerPerformance() throws Exception {
        // Was fixed at 1000 tasks, but was running out of virtual memory due to excessive thread creation
        // on machines which were not able to execute the threads quickly.
        final int NUM_TASKS = Math.min(500 * Runtime.getRuntime().availableProcessors(), 1000);
        final int NUM_TIMES = 10;
        final int MAX_ACCEPTABLE_TIME = 7500; // saw 5601ms on buildhive
        
        long tWarmup = execTasksAndWaitForDone(NUM_TASKS, ImmutableList.of("A"));
        
        List<Long> times = Lists.newArrayList();
        for (int i = 1; i <= NUM_TIMES; i++) {
            times.add(execTasksAndWaitForDone(NUM_TASKS, ImmutableList.of("A")));
        }
        
        Long toobig = Iterables.find(
                times, 
                new Predicate<Long>() {
                    public boolean apply(Long input) {
                        return input > MAX_ACCEPTABLE_TIME;
                    }},
                null);
        assertNull(toobig, "warmup="+tWarmup+"; times="+times);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private long execTasksAndWaitForDone(int numTasks, List<?> tags) throws Exception {
        List<Task<?>> tasks = Lists.newArrayList();
        long startTimestamp = System.currentTimeMillis();
        for (int i = 1; i < numTasks; i++) {
            Task<?> t = new BasicTask(Callables.returning(null)); // no-op
            em.submit(MutableMap.of("tags", tags), t);
            tasks.add(t);
        }
        long submittedTimestamp = System.currentTimeMillis();

        for (Task t : tasks) {
            t.get();
        }
        long endTimestamp = System.currentTimeMillis();
        long submitTime = submittedTimestamp - startTimestamp;
        long totalTime = endTimestamp - startTimestamp;
        
        log.info("Executed {} tasks; {}ms total; {}ms to submit", new Object[] {numTasks, totalTime, submitTime});

        return totalTime;
    }
}
