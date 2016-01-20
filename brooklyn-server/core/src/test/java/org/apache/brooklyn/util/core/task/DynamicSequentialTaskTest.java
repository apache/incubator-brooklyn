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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.task.TaskInternal.TaskCancellationMode;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.math.MathPredicates;
import org.apache.brooklyn.util.time.CountdownTimer;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DynamicSequentialTaskTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicSequentialTaskTest.class);
    
    public static final Duration TIMEOUT = Duration.TEN_SECONDS;
    public static final Duration TINY_TIME = Duration.millis(20);
    
    BasicExecutionManager em;
    BasicExecutionContext ec;
    List<String> messages;
    Semaphore cancellations;
    Stopwatch stopwatch;
    Map<String,Semaphore> monitorableJobSemaphoreMap;
    Map<String,Task<String>> monitorableTasksMap;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        em = new BasicExecutionManager("mycontext");
        ec = new BasicExecutionContext(em);
        cancellations = new Semaphore(0);
        messages = new ArrayList<String>();
        monitorableJobSemaphoreMap = MutableMap.of();
        monitorableTasksMap = MutableMap.of();
        monitorableTasksMap.clear();
        stopwatch = Stopwatch.createStarted();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
    }

    @Test
    public void testSimple() throws Exception {
        Callable<String> mainJob = new Callable<String>() {
            public String call() {
                log.info("main job - "+Tasks.current());
                messages.add("main");
                DynamicTasks.queue( sayTask("world") );
                return "bye";
            }            
        };
        DynamicSequentialTask<String> t = new DynamicSequentialTask<String>(mainJob);
        // this should be added before anything added when the task is invoked
        t.queue(sayTask("hello"));
        
        Assert.assertEquals(messages, Lists.newArrayList());
        Assert.assertEquals(t.isBegun(), false);
        Assert.assertEquals(Iterables.size(t.getChildren()), 1);
        
        ec.submit(t);
        Assert.assertEquals(t.isSubmitted(), true);
        Assert.assertEquals(t.getUnchecked(Duration.ONE_SECOND), "bye");
        long elapsed = t.getEndTimeUtc() - t.getSubmitTimeUtc();
        Assert.assertTrue(elapsed < 1000, "elapsed time should have been less than 1s but was "+
                Time.makeTimeString(elapsed, true));
        Assert.assertEquals(Iterables.size(t.getChildren()), 2);
        Assert.assertEquals(messages.size(), 3, "expected 3 entries, but had "+messages);
        // either main or hello can be first, but world should be last 
        Assert.assertEquals(messages.get(2), "world");
    }
    
    public Callable<String> sayCallable(final String message, final Duration duration, final String message2) {
        return new Callable<String>() {
            public String call() {
                try {
                    if (message != null) {
                        log.info("saying: "+message+ " - "+Tasks.current());
                        synchronized (messages) {
                            messages.add(message);
                            messages.notifyAll();
                        }
                    }
                    if (message2 != null) {
                        log.info("will say "+message2+" after "+duration);
                    }
                    if (duration != null && duration.toMilliseconds() > 0) {
                        Thread.sleep(duration.toMillisecondsRoundingUp());
                    }
                } catch (InterruptedException e) {
                    cancellations.release();
                    throw Exceptions.propagate(e);
                }
                if (message2 != null) {
                    log.info("saying: "+message2+ " - "+Tasks.current());
                    synchronized (messages) {
                        messages.add(message2);
                        messages.notifyAll();
                    }
                }
                return message;
            }            
        };
    }
    
    public Task<String> sayTask(String message) {
        return sayTask(message, null, null);
    }
    
    public Task<String> sayTask(String message, Duration duration, String message2) {
        return Tasks.<String>builder().displayName("say:"+message).body(sayCallable(message, duration, message2)).build();
    }
    
    public <T> Task<T> submitting(final Task<T> task) {
        return Tasks.<T>builder().displayName("submitting:"+task.getId()).body(new Callable<T>() {
            @Override
            public T call() throws Exception {
                ec.submit(task);
                return task.get();
            }
        }).build();
    }
    
    @Test
    public void testComplex() throws Exception {
        Task<List<?>> t = Tasks.sequential(
                sayTask("1"),
                sayTask("2"),
                Tasks.parallel(sayTask("4"), sayTask("3")),
                sayTask("5")
            );
        ec.submit(t);
        Assert.assertEquals(t.get().size(), 4); 
        Asserts.assertEqualsIgnoringOrder((List<?>)t.get().get(2), ImmutableSet.of("3", "4"));
        Assert.assertTrue(messages.equals(Arrays.asList("1", "2", "3", "4", "5")) || messages.equals(Arrays.asList("1", "2", "4", "3", "5")), "messages="+messages);
    }
    
    @Test
    public void testCancelled() throws Exception {
        Task<List<?>> t = Tasks.sequential(
                sayTask("1"),
                sayTask("2a", Duration.THIRTY_SECONDS, "2b"),
                sayTask("3"));
        ec.submit(t);
        
        waitForMessages(Predicates.compose(MathPredicates.greaterThanOrEqual(2), CollectionFunctionals.sizeFunction()), TIMEOUT);
        Assert.assertEquals(messages, Arrays.asList("1", "2a"));
        Time.sleep(Duration.millis(50));
        t.cancel(true);
        Assert.assertTrue(t.isDone());
        // 2 should get cancelled, and invoke the cancellation semaphore
        // 3 should get cancelled and not run at all
        Assert.assertEquals(messages, Arrays.asList("1", "2a"));
        
        // Need to ensure that 2 has been started; race where we might cancel it before its run method
        // is even begun. Hence doing "2a; pause; 2b" where nothing is interruptable before pause.
        Assert.assertTrue(cancellations.tryAcquire(10, TimeUnit.SECONDS));
        
        Iterator<Task<?>> ci = ((HasTaskChildren)t).getChildren().iterator();
        Assert.assertEquals(ci.next().get(), "1");
        Task<?> task2 = ci.next();
        Assert.assertTrue(task2.isBegun());
        Assert.assertTrue(task2.isDone());
        Assert.assertTrue(task2.isCancelled());
        
        Task<?> task3 = ci.next();
        Assert.assertFalse(task3.isBegun());
        Assert.assertTrue(task2.isDone());
        Assert.assertTrue(task2.isCancelled());
        
        // but we do _not_ get a mutex from task3 as it does not run (is not interrupted)
        Assert.assertEquals(cancellations.availablePermits(), 0);
    }
    
    @Test
    public void testCancellationModeAndSubmitted() throws Exception {
        doTestCancellationModeAndSubmitted(true, TaskCancellationMode.DO_NOT_INTERRUPT, false, false);
        
        doTestCancellationModeAndSubmitted(true, TaskCancellationMode.INTERRUPT_TASK_AND_ALL_SUBMITTED_TASKS, true, true);
        doTestCancellationModeAndSubmitted(true, TaskCancellationMode.INTERRUPT_TASK_AND_DEPENDENT_SUBMITTED_TASKS, true, true);
        doTestCancellationModeAndSubmitted(true, TaskCancellationMode.INTERRUPT_TASK_BUT_NOT_SUBMITTED_TASKS, true, false);
        
        // if it's not transient, it should only be cancelled on "all submitted"
        doTestCancellationModeAndSubmitted(false, TaskCancellationMode.INTERRUPT_TASK_AND_DEPENDENT_SUBMITTED_TASKS, true, false);
        doTestCancellationModeAndSubmitted(false, TaskCancellationMode.INTERRUPT_TASK_AND_ALL_SUBMITTED_TASKS, true, true);
        
        // cancellation mode left off should be the same as TASK_AND_DEPENDENT, i.e. don't cancel non-transient bg submitted
        doTestCancellationModeAndSubmitted(true, null, true, true);
        doTestCancellationModeAndSubmitted(false, null, true, false);
        // and 'true' should be the same
        doTestCancellationModeAndSubmitted(true, true, true, true);
        doTestCancellationModeAndSubmitted(false, true, true, false);
        
        // cancellation mode false should be the same as DO_NOT_INTERRUPT
        doTestCancellationModeAndSubmitted(true, false, false, false);
    }
    
    public void doTestCancellationModeAndSubmitted(
            boolean isSubtaskTransient,
            Object cancellationMode,
            boolean expectedTaskInterrupted,
            boolean expectedSubtaskCancelled
            ) throws Exception {
        tearDown(); setUp();
        
        final Task<String> t1 = sayTask("1-wait", Duration.minutes(10), "1-done");
        if (isSubtaskTransient) {
            BrooklynTaskTags.addTagDynamically(t1, BrooklynTaskTags.TRANSIENT_TASK_TAG);
        }
        
        final Task<List<?>> t = Tasks.parallel(
                submitting(t1),
                sayTask("2-wait", Duration.minutes(10), "2-done"));
        ec.submit(t);
        
        waitForMessages(Predicates.compose(MathPredicates.greaterThanOrEqual(2), CollectionFunctionals.sizeFunction()), TIMEOUT);
        Asserts.assertEquals(MutableSet.copyOf(messages), MutableSet.of("1-wait", "2-wait"));

        if (cancellationMode==null) {
            ((TaskInternal<?>)t).cancel();
        } else if (cancellationMode instanceof Boolean) {
            t.cancel((Boolean)cancellationMode);
        } else if (cancellationMode instanceof TaskCancellationMode) {
            ((TaskInternal<?>)t).cancel((TaskCancellationMode)cancellationMode);
        } else {
            throw new IllegalStateException("Invalid cancellationMode: "+cancellationMode);
        }

        // the cancelled task always reports cancelled and done
        Assert.assertEquals(t.isDone(), true);
        Assert.assertEquals(t.isCancelled(), true);
        // end time might not be set for another fraction of a second
        if (expectedTaskInterrupted) { 
            Asserts.eventually(new Supplier<Number>() {
                @Override public Number get() { return t.getEndTimeUtc(); }}, 
                MathPredicates.<Number>greaterThanOrEqual(0));
        } else {
            Assert.assertTrue(t.getEndTimeUtc() < 0, "Wrong end time: "+t.getEndTimeUtc());
        }
        
        if (expectedSubtaskCancelled) {
            Asserts.eventually(Suppliers.ofInstance(t1), TaskPredicates.isDone());
            Assert.assertTrue(t1.isCancelled());
            Asserts.eventually(new Supplier<Number>() {
                @Override public Number get() { return t1.getEndTimeUtc(); }}, 
                MathPredicates.<Number>greaterThanOrEqual(0));
        } else {
            Time.sleep(Duration.millis(5));
            Assert.assertFalse(t1.isCancelled());
            Assert.assertFalse(t1.isDone());
        }
    }

    protected void waitForMessages(Predicate<? super List<String>> predicate, Duration timeout) throws Exception {
        long endtime = System.currentTimeMillis() + timeout.toMilliseconds();
        synchronized (messages) {
            while (true) {
                if (predicate.apply(messages)) {
                    return;
                }
                long waittime = endtime - System.currentTimeMillis();
                if (waittime > 0) {
                    messages.wait(waittime);
                } else {
                    throw new TimeoutException("Timeout after "+timeout+"; messages="+messages+"; predicate="+predicate);
                }
            }
        }
    }
    
    protected Task<String> monitorableTask(final String id) {
        return monitorableTask(null, id, null);
    }
    protected Task<String> monitorableTask(final Runnable pre, final String id, final Callable<String> post) {
        Task<String> t = Tasks.<String>builder().body(monitorableJob(pre, id, post)).build();
        monitorableTasksMap.put(id, t);
        return t;
    }
    protected Callable<String> monitorableJob(final String id) {
        return monitorableJob(null, id, null);
    }
    protected Callable<String> monitorableJob(final Runnable pre, final String id, final Callable<String> post) {
        monitorableJobSemaphoreMap.put(id, new Semaphore(0));
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (pre!=null) pre.run();
                // wait for semaphore
                if (!monitorableJobSemaphoreMap.get(id).tryAcquire(1, TIMEOUT.toMilliseconds(), TimeUnit.MILLISECONDS))
                    throw new IllegalStateException("timeout for "+id);
                synchronized (messages) {
                    messages.add(id);
                    messages.notifyAll();
                }
                if (post!=null) return post.call();
                return id;
            }
        };
    }
    protected void releaseMonitorableJob(final String id) {
        monitorableJobSemaphoreMap.get(id).release();
    }
    protected void waitForMessage(final String id) {
        CountdownTimer timer = CountdownTimer.newInstanceStarted(TIMEOUT);
        synchronized (messages) {
            while (!timer.isExpired()) {
                if (messages.contains(id)) return;
                timer.waitOnForExpiryUnchecked(messages);
            }
        }
        Assert.fail("Did not see message "+id);
    }
    protected void releaseAndWaitForMonitorableJob(final String id) {
        releaseMonitorableJob(id);
        waitForMessage(id);
    }
    
    @Test
    public void testChildrenRunConcurrentlyWithPrimary() {
        Task<String> t = Tasks.<String>builder().dynamic(true)
            .body(monitorableJob("main"))
            .add(monitorableTask("1")).add(monitorableTask("2")).build();
        ec.submit(t);
        releaseAndWaitForMonitorableJob("1");
        releaseAndWaitForMonitorableJob("main");
        Assert.assertFalse(t.blockUntilEnded(TINY_TIME));
        releaseMonitorableJob("2");
        
        Assert.assertTrue(t.blockUntilEnded(TIMEOUT));
        Assert.assertEquals(messages, MutableList.of("1", "main", "2"));
        Assert.assertTrue(stopwatch.elapsed(TimeUnit.MILLISECONDS) < TIMEOUT.toMilliseconds(), "took too long: "+stopwatch);
        Assert.assertFalse(t.isError());
    }
    
    protected static class FailRunnable implements Runnable {
        @Override public void run() { throw new RuntimeException("Planned exception for test"); }
    }
    protected static class FailCallable implements Callable<String> {
        @Override public String call() { throw new RuntimeException("Planned exception for test"); }
    }
    
    @Test
    public void testByDefaultChildrenFailureAbortsSecondaryFailsPrimaryButNotAbortsPrimary() {
        Task<String> t1 = monitorableTask(null, "1", new FailCallable());
        Task<String> t = Tasks.<String>builder().dynamic(true)
            .body(monitorableJob("main"))
            .add(t1).add(monitorableTask("2")).build();
        ec.submit(t);
        releaseAndWaitForMonitorableJob("1");
        Assert.assertFalse(t.blockUntilEnded(TINY_TIME));
        releaseMonitorableJob("main");
        
        Assert.assertTrue(t.blockUntilEnded(TIMEOUT));
        Assert.assertEquals(messages, MutableList.of("1", "main"));
        Assert.assertTrue(stopwatch.elapsed(TimeUnit.MILLISECONDS) < TIMEOUT.toMilliseconds(), "took too long: "+stopwatch);
        Assert.assertTrue(t.isError());
        Assert.assertTrue(t1.isError());
    }

    @Test
    public void testWhenSwallowingChildrenFailureDoesNotAbortSecondaryOrFailPrimary() {
        Task<String> t1 = monitorableTask(null, "1", new FailCallable());
        Task<String> t = Tasks.<String>builder().dynamic(true)
            .body(monitorableJob("main"))
            .add(t1).add(monitorableTask("2")).swallowChildrenFailures(true).build();
        ec.submit(t);
        releaseAndWaitForMonitorableJob("1");
        Assert.assertFalse(t.blockUntilEnded(TINY_TIME));
        releaseAndWaitForMonitorableJob("2");
        Assert.assertFalse(t.blockUntilEnded(TINY_TIME));
        releaseMonitorableJob("main");
        Assert.assertTrue(t.blockUntilEnded(TIMEOUT));
        Assert.assertEquals(messages, MutableList.of("1", "2", "main"));
        Assert.assertTrue(stopwatch.elapsed(TimeUnit.MILLISECONDS) < TIMEOUT.toMilliseconds(), "took too long: "+stopwatch);
        Assert.assertFalse(t.isError());
        Assert.assertTrue(t1.isError());
    }

    @Test
    public void testInessentialChildrenFailureDoesNotAbortSecondaryOrFailPrimary() {
        Task<String> t1 = monitorableTask(null, "1", new FailCallable());
        TaskTags.markInessential(t1);
        Task<String> t = Tasks.<String>builder().dynamic(true)
            .body(monitorableJob("main"))
            .add(t1).add(monitorableTask("2")).build();
        ec.submit(t);
        releaseAndWaitForMonitorableJob("1");
        Assert.assertFalse(t.blockUntilEnded(TINY_TIME));
        releaseAndWaitForMonitorableJob("2");
        Assert.assertFalse(t.blockUntilEnded(TINY_TIME));
        releaseMonitorableJob("main");
        Assert.assertTrue(t.blockUntilEnded(TIMEOUT));
        Assert.assertEquals(messages, MutableList.of("1", "2", "main"));
        Assert.assertTrue(stopwatch.elapsed(TimeUnit.MILLISECONDS) < TIMEOUT.toMilliseconds(), "took too long: "+stopwatch);
        Assert.assertFalse(t.isError());
        Assert.assertTrue(t1.isError());
    }

    @Test
    public void testTaskBuilderUsingAddVarargChildren() {
        Task<String> t = Tasks.<String>builder().dynamic(true)
            .body(monitorableJob("main"))
            .add(monitorableTask("1"), monitorableTask("2"))
            .build();
        ec.submit(t);
        releaseAndWaitForMonitorableJob("1");
        releaseAndWaitForMonitorableJob("2");
        releaseAndWaitForMonitorableJob("main");
        
        Assert.assertEquals(messages, MutableList.of("1", "2", "main"));
    }
    
    @Test
    public void testTaskBuilderUsingAddAllChildren() {
        Task<String> t = Tasks.<String>builder().dynamic(true)
            .body(monitorableJob("main"))
            .addAll(ImmutableList.of(monitorableTask("1"), monitorableTask("2")))
            .build();
        ec.submit(t);
        releaseAndWaitForMonitorableJob("1");
        releaseAndWaitForMonitorableJob("2");
        releaseAndWaitForMonitorableJob("main");
        
        Assert.assertEquals(messages, MutableList.of("1", "2", "main"));
    }
}
