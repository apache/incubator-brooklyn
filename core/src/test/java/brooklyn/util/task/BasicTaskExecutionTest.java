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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;

/**
 * Test the operation of the {@link BasicTask} class.
 *
 * TODO clarify test purpose
 */
public class BasicTaskExecutionTest {
    private static final Logger log = LoggerFactory.getLogger(BasicTaskExecutionTest.class);
 
    private static final int TIMEOUT_MS = 10*1000;
    
    private BasicExecutionManager em;
    private Map<Object, Object> data;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        em = new BasicExecutionManager("mycontext");
        data = Collections.synchronizedMap(new HashMap<Object, Object>());
        data.clear();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (em != null) em.shutdownNow();
        if (data != null) data.clear();
    }
    
    @Test
    public void runSimpleBasicTask() throws Exception {
        BasicTask<Object> t = new BasicTask<Object>(newPutCallable(1, "b"));
        data.put(1, "a");
        Task<Object> t2 = em.submit(MutableMap.of("tag", "A"), t);
        assertEquals("a", t.get());
        assertEquals("a", t2.get());
        assertEquals("b", data.get(1));
    }
    
    @Test
    public void runSimpleRunnable() throws Exception {
        data.put(1, "a");
        Task<?> t = em.submit(MutableMap.of("tag", "A"), newPutRunnable(1, "b"));
        assertEquals(null, t.get());
        assertEquals("b", data.get(1));
    }

    @Test
    public void runSimpleCallable() throws Exception {
        data.put(1, "a");
        Task<?> t = em.submit(MutableMap.of("tag", "A"), newPutCallable(1, "b"));
        assertEquals("a", t.get());
        assertEquals("b", data.get(1));
    }

    @Test
    public void runBasicTaskWithWaits() throws Exception {
        final CountDownLatch signalStarted = new CountDownLatch(1);
        final CountDownLatch allowCompletion = new CountDownLatch(1);
        final BasicTask<Object> t = new BasicTask<Object>(new Callable<Object>() {
            public Object call() throws Exception {
                Object result = data.put(1, "b");
                signalStarted.countDown();
                assertTrue(allowCompletion.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
                return result;
            }});
        data.put(1, "a");

        Task<?> t2 = em.submit(MutableMap.of("tag", "A"), t);
        assertEquals(t, t2);
        assertFalse(t.isDone());
        
        assertTrue(signalStarted.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals("b", data.get(1));
        assertFalse(t.isDone());
        
        log.debug("runBasicTaskWithWaits, BasicTask status: {}", t.getStatusDetail(false));
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String status = t.getStatusDetail(false);
                assertTrue(status != null && status.toLowerCase().contains("waiting"), "status="+status);
            }});
        
        allowCompletion.countDown();
        assertEquals("a", t.get());
    }

    @Test
    public void runMultipleBasicTasks() throws Exception {
        data.put(1, 1);
        BasicExecutionManager em = new BasicExecutionManager("mycontext");
        for (int i = 0; i < 2; i++) {
            em.submit(MutableMap.of("tag", "A"), new BasicTask<Integer>(newIncrementCallable(1)));
            em.submit(MutableMap.of("tag", "B"), new BasicTask<Integer>(newIncrementCallable((1))));
        }
        int total = 0;
        for (Object tag : em.getTaskTags()) {
                log.debug("tag {}", tag);
                for (Task<?> task : em.getTasksWithTag(tag)) {
                    log.debug("BasicTask {}, has {}", task, task.get());
                    total += (Integer)task.get();
                }
            }
        assertEquals(10, total);
        //now that all have completed:
        assertEquals(5, data.get(1));
    }

    @Test
    public void runMultipleBasicTasksMultipleTags() throws Exception {
        data.put(1, 1);
        Collection<Task<Integer>> tasks = Lists.newArrayList();
        tasks.add(em.submit(MutableMap.of("tag", "A"), new BasicTask<Integer>(newIncrementCallable(1))));
        tasks.add(em.submit(MutableMap.of("tags", ImmutableList.of("A","B")), new BasicTask<Integer>(newIncrementCallable(1))));
        tasks.add(em.submit(MutableMap.of("tags", ImmutableList.of("B","C")), new BasicTask<Integer>(newIncrementCallable(1))));
        tasks.add(em.submit(MutableMap.of("tags", ImmutableList.of("D")), new BasicTask<Integer>(newIncrementCallable(1))));
        int total = 0;

        for (Task<Integer> t : tasks) {
            log.debug("BasicTask {}, has {}", t, t.get());
            total += t.get();
            }
        assertEquals(10, total);
 
        //now that all have completed:
        assertEquals(data.get(1), 5);
        assertEquals(em.getTasksWithTag("A").size(), 2);
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("A")).size(), 2);
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("A")).size(), 2);

        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("A", "B")).size(), 3);
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("A", "B")).size(), 1);
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("B", "C")).size(), 1);
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("A", "D")).size(), 3);
    }

    @Test
    public void testGetTaskById() throws Exception {
        Task<?> t = new BasicTask<Void>(newNoop());
        em.submit(MutableMap.of("tag", "A"), t);
        assertEquals(em.getTask(t.getId()), t);
    }

    @Test
    public void testRetrievingTasksWithTagsReturnsExpectedTask() throws Exception {
        Task<?> t = new BasicTask<Void>(newNoop());
        em.submit(MutableMap.of("tag", "A"), t);
        t.get();

        assertEquals(em.getTasksWithTag("A"), ImmutableList.of(t));
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("A")), ImmutableList.of(t));
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("A", "B")), ImmutableList.of(t));
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("A")), ImmutableList.of(t));
    }

    @Test
    public void testRetrievingTasksWithTagsExcludesNonMatchingTasks() throws Exception {
        Task<?> t = new BasicTask<Void>(newNoop());
        em.submit(MutableMap.of("tag", "A"), t);
        t.get();

        assertEquals(em.getTasksWithTag("B"), ImmutableSet.of());
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("B")), ImmutableSet.of());
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("A", "B")), ImmutableSet.of());
    }
    
    @Test
    public void testRetrievingTasksWithMultipleTags() throws Exception {
        Task<?> t = new BasicTask<Void>(newNoop());
        em.submit(MutableMap.of("tags", ImmutableList.of("A", "B")), t);
        t.get();

        assertEquals(em.getTasksWithTag("A"), ImmutableList.of(t));
        assertEquals(em.getTasksWithTag("B"), ImmutableList.of(t));
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("A")), ImmutableList.of(t));
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("B")), ImmutableList.of(t));
        assertEquals(em.getTasksWithAnyTag(ImmutableList.of("A", "B")), ImmutableList.of(t));
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("A", "B")), ImmutableList.of(t));
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("A")), ImmutableList.of(t));
        assertEquals(em.getTasksWithAllTags(ImmutableList.of("B")), ImmutableList.of(t));
    }

    // ENGR-1796: if nothing matched first tag, then returned whatever matched second tag!
    @Test
    public void testRetrievingTasksWithAllTagsWhenFirstNotMatched() throws Exception {
        Task<?> t = new BasicTask<Void>(newNoop());
        em.submit(MutableMap.of("tags", ImmutableList.of("A")), t);
        t.get();

        assertEquals(em.getTasksWithAllTags(ImmutableList.of("not_there","A")), ImmutableSet.of());
    }
    
    @Test
    public void testRetrievedTasksIncludesTasksInProgress() throws Exception {
        final CountDownLatch runningLatch = new CountDownLatch(1);
        final CountDownLatch finishLatch = new CountDownLatch(1);
        Task<Void> t = new BasicTask<Void>(new Callable<Void>() {
            public Void call() throws Exception {
                runningLatch.countDown();
                finishLatch.await();
                return null;
            }});
        em.submit(MutableMap.of("tags", ImmutableList.of("A")), t);
        
        try {
            runningLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    
            assertEquals(em.getTasksWithTag("A"), ImmutableList.of(t));
        } finally {
            finishLatch.countDown();
        }
    }
    
    @Test
    public void cancelBeforeRun() throws Exception {
        final CountDownLatch blockForever = new CountDownLatch(1);
        
        BasicTask<Integer> t = new BasicTask<Integer>(new Callable<Integer>() {
            public Integer call() throws Exception {
                blockForever.await(); return 42;
            }});
        t.cancel(true);
        assertTrue(t.isCancelled());
        assertTrue(t.isDone());
        assertTrue(t.isError());
        em.submit(MutableMap.of("tag", "A"), t);
        try {
            t.get();
            fail("get should have failed due to cancel");
        } catch (CancellationException e) {
            // expected
        }
        assertTrue(t.isCancelled());
        assertTrue(t.isDone());
        assertTrue(t.isError());
        
        log.debug("cancelBeforeRun status: {}", t.getStatusDetail(false));
        assertTrue(t.getStatusDetail(false).toLowerCase().contains("cancel"));
    }

    @Test
    public void cancelDuringRun() throws Exception {
        final CountDownLatch signalStarted = new CountDownLatch(1);
        final CountDownLatch blockForever = new CountDownLatch(1);
        
        BasicTask<Integer> t = new BasicTask<Integer>(new Callable<Integer>() {
            public Integer call() throws Exception {
                synchronized (data) {
                    signalStarted.countDown();
                    blockForever.await();
                }
                return 42;
            }});
        em.submit(MutableMap.of("tag", "A"), t);
        assertFalse(t.isCancelled());
        assertFalse(t.isDone());
        assertFalse(t.isError());
        
        assertTrue(signalStarted.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        t.cancel(true);
        
        assertTrue(t.isCancelled());
        assertTrue(t.isError());
        try {
            t.get();
            fail("get should have failed due to cancel");
        } catch (CancellationException e) {
            // expected
        }
        assertTrue(t.isCancelled());
        assertTrue(t.isDone());
        assertTrue(t.isError());
    }
    
    @Test
    public void cancelAfterRun() throws Exception {
        BasicTask<Integer> t = new BasicTask<Integer>(Callables.returning(42));
        em.submit(MutableMap.of("tag", "A"), t);

        assertEquals(t.get(), (Integer)42);
        t.cancel(true);
        assertFalse(t.isCancelled());
        assertFalse(t.isError());
        assertTrue(t.isDone());
    }
    
    @Test
    public void errorDuringRun() throws Exception {
        BasicTask<Void> t = new BasicTask<Void>(new Callable<Void>() {
            public Void call() throws Exception {
                throw new IllegalStateException("Simulating failure in errorDuringRun");
            }});
        
        em.submit(MutableMap.of("tag", "A"), t);
        
        try {
            t.get();
            fail("get should have failed due to error"); 
        } catch (Exception eo) { 
            Throwable e = Throwables.getRootCause(eo);
            assertEquals("Simulating failure in errorDuringRun", e.getMessage());
        }
        
        assertFalse(t.isCancelled());
        assertTrue(t.isError());
        assertTrue(t.isDone());
        
        log.debug("errorDuringRun status: {}", t.getStatusDetail(false));
        assertTrue(t.getStatusDetail(false).contains("Simulating failure in errorDuringRun"), "details="+t.getStatusDetail(false));
    }

    @Test
    public void fieldsSetForSimpleBasicTask() throws Exception {
        final CountDownLatch signalStarted = new CountDownLatch(1);
        final CountDownLatch allowCompletion = new CountDownLatch(1);
        
        BasicTask<Integer> t = new BasicTask<Integer>(new Callable<Integer>() {
            public Integer call() throws Exception {
                signalStarted.countDown();
                allowCompletion.await();
                return 42;
            }});
        assertEquals(null, t.submittedByTask);
        assertEquals(-1, t.submitTimeUtc);
        assertNull(t.getResult());

        em.submit(MutableMap.of("tag", "A"), t);
        assertTrue(signalStarted.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        
        assertTrue(t.submitTimeUtc > 0);
        assertTrue(t.startTimeUtc >= t.submitTimeUtc);
        assertNotNull(t.getResult());
        assertEquals(-1, t.endTimeUtc);
        assertEquals(false, t.isCancelled());
        
        allowCompletion.countDown();
        assertEquals(t.get(), (Integer)42);
        assertTrue(t.endTimeUtc >= t.startTimeUtc);

        log.debug("BasicTask duration (millis): {}", (t.endTimeUtc - t.submitTimeUtc));
    }

    @Test
    public void fieldsSetForBasicTaskSubmittedBasicTask() throws Exception {
        //submitted BasicTask B is started by A, and waits for A to complete
        BasicTask<Integer> t = new BasicTask<Integer>(MutableMap.of("displayName", "sample", "description", "some descr"), new Callable<Integer>() {
            public Integer call() throws Exception {
                em.submit(MutableMap.of("tag", "B"), new Callable<Integer>() {
                    public Integer call() throws Exception {
                        assertEquals(45, em.getTasksWithTag("A").iterator().next().get());
                        return 46;
                    }});
                return 45;
            }});
        em.submit(MutableMap.of("tag", "A"), t);

        t.blockUntilEnded();
 
//        assertEquals(em.getAllTasks().size(), 2
        
        BasicTask<?> tb = (BasicTask<?>) em.getTasksWithTag("B").iterator().next();
        assertEquals( 46, tb.get() );
        assertEquals( t, em.getTasksWithTag("A").iterator().next() );
        assertNull( t.submittedByTask );
        
        BasicTask<?> submitter = (BasicTask<?>) tb.submittedByTask;
        assertNotNull(submitter);
        assertEquals("sample", submitter.displayName);
        assertEquals("some descr", submitter.description);
        assertEquals(t, submitter);
        
        assertTrue(submitter.submitTimeUtc <= tb.submitTimeUtc);
        assertTrue(submitter.endTimeUtc <= tb.endTimeUtc);
        
        log.debug("BasicTask {} was submitted by {}", tb, submitter);
    }
    
    private Callable<Object> newPutCallable(final Object key, final Object val) {
        return new Callable<Object>() {
            public Object call() {
                return data.put(key, val);
            }
        };
    }
    
    private Callable<Integer> newIncrementCallable(final Object key) {
        return new Callable<Integer>() {
            public Integer call() {
                synchronized (data) {
                    return (Integer) data.put(key, (Integer)data.get(key) + 1);
                }
            }
        };
    }
    
    private Runnable newPutRunnable(final Object key, final Object val) {
        return new Runnable() {
            public void run() {
                data.put(key, val);
            }
        };
    }
    
    private Runnable newNoop() {
        return new Runnable() {
            public void run() {
            }
        };
    }
}
