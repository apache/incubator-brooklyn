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
import static org.testng.Assert.fail;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;

import com.google.common.util.concurrent.Callables;

public class SingleThreadedSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(SingleThreadedSchedulerTest.class);
    
    private BasicExecutionManager em;
    
    @BeforeMethod
    public void setUp() {
        em = new BasicExecutionManager("mycontextid");
        em.setTaskSchedulerForTag("category1", SingleThreadedScheduler.class);
    }
    
    @AfterMethod
    public void tearDown() {
        if (em != null) em.shutdownNow();
    }
    
    @Test
    public void testExecutesInOrder() throws Exception {
        final int NUM_TIMES = 1000;
        final List<Integer> result = new CopyOnWriteArrayList<Integer>();
        for (int i = 0; i < NUM_TIMES; i++) {
            final int counter = i;
            em.submit(MutableMap.of("tag", "category1"), new Runnable() {
                public void run() {
                    result.add(counter);
                }});
        }
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(result.size(), NUM_TIMES);
            }});

        for (int i = 0; i < NUM_TIMES; i++) {
            assertEquals(result.get(i), (Integer)i);
        }        
    }
    
    @Test
    public void testLargeQueueDoesNotConsumeTooManyThreads() throws Exception {
        final int NUM_TIMES = 3000;
        final CountDownLatch latch = new CountDownLatch(1);
        BasicTask<Void> blockingTask = new BasicTask<Void>(newLatchAwaiter(latch));
        em.submit(MutableMap.of("tag", "category1"), blockingTask);
        
        final AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < NUM_TIMES; i++) {
            BasicTask<Void> t = new BasicTask<Void>(new Runnable() {
                public void run() {
                    counter.incrementAndGet();
                }});
            em.submit(MutableMap.of("tag", "category1"), t);
            if (i % 500 == 0) log.info("Submitted "+i+" jobs...");
        }

        Thread.sleep(100); // give it more of a chance to create the threads before we let them execute
        latch.countDown();

        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(counter.get(), NUM_TIMES);
            }});
    }
    
    @Test
    public void testGetResultOfQueuedTaskBeforeItExecutes() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        em.submit(MutableMap.of("tag", "category1"), newLatchAwaiter(latch));
        
        BasicTask<Integer> t = new BasicTask<Integer>(Callables.returning(123));
        Future<Integer> future = em.submit(MutableMap.of("tag", "category1"), t);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            }});
        thread.start();
        assertEquals(future.get(), (Integer)123);
    }
    
    @Test
    public void testGetResultOfQueuedTaskBeforeItExecutesWithTimeout() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        em.submit(MutableMap.of("tag", "category1"), newLatchAwaiter(latch));
        
        BasicTask<Integer> t = new BasicTask<Integer>(Callables.returning(123));
        Future<Integer> future = em.submit(MutableMap.of("tag", "category1"), t);

        try {
            assertEquals(future.get(10, TimeUnit.MILLISECONDS), (Integer)123);
            fail();
        } catch (TimeoutException e) {
            // success
        }
    }
    
    @Test
    public void testCancelQueuedTaskBeforeItExecutes() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        em.submit(MutableMap.of("tag", "category1"), newLatchAwaiter(latch));
        
        final AtomicBoolean executed = new AtomicBoolean();
        BasicTask<?> t = new BasicTask<Void>(new Runnable() {
            public void run() {
                executed.set(true);
            }});
        Future<?> future = em.submit(MutableMap.of("tag", "category1"), t);

        future.cancel(true);
        latch.countDown();
        Thread.sleep(10);
        try {
            future.get();
        } catch (CancellationException e) {
            // success
        }
        assertFalse(executed.get());
    }
    
    @Test
    public void testGetResultOfQueuedTaskAfterItExecutes() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        em.submit(MutableMap.of("tag", "category1"), newLatchAwaiter(latch));
        
        BasicTask<Integer> t = new BasicTask<Integer>(Callables.returning(123));
        Future<Integer> future = em.submit(MutableMap.of("tag", "category1"), t);

        latch.countDown();
        assertEquals(future.get(), (Integer)123);
    }
    
    private Callable<Void> newLatchAwaiter(final CountDownLatch latch) {
        return new Callable<Void>() {
            public Void call() throws Exception {
                latch.await();
                return null;
            }
        };
    }
}
