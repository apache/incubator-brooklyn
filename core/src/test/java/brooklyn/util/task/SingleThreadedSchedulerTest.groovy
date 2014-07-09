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
package brooklyn.util.task

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

public class SingleThreadedSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(SingleThreadedSchedulerTest)
    
    private BasicExecutionManager em
    
    @BeforeMethod
    public void setUp() {
        em = new BasicExecutionManager()
        em.setTaskSchedulerForTag("category1", SingleThreadedScheduler.class);
    }
    
    @AfterMethod
    public void tearDown() {
        em?.shutdownNow()
    }
    
    @Test
    public void testExecutesInOrder() {
        final int NUM_TIMES = 1000
        final List<Integer> result = new CopyOnWriteArrayList()
        for (i in 0..(NUM_TIMES-1)) {
            final counter = i
            em.submit(tag:"category1", { result.add(counter) })
        }
        
        executeUntilSucceeds {
            assertEquals(result.size(), NUM_TIMES)
        }

        for (i in 0..(NUM_TIMES-1)) {
            assertEquals(result.get(i), i)
        }        
    }
    
    @Test
    public void testLargeQueueDoesNotConsumeTooManyThreads() {
        final int NUM_TIMES = 3000
        final CountDownLatch latch = new CountDownLatch(1)
        BasicTask blockingTask = [ { latch.await() } ]
        em.submit tag:"category1", blockingTask
        
        final AtomicInteger counter = new AtomicInteger(0)
        for (i in 1..NUM_TIMES) {
            BasicTask t = [ {counter.incrementAndGet()} ]
            em.submit tag:"category1", t
            if (i % 500 == 0) log.info("Submitted $i jobs...")
        }

        Thread.sleep(100) // give it more of a chance to create the threads before we let them execute
        latch.countDown()

        executeUntilSucceeds {
            assertEquals(counter.get(), NUM_TIMES)
        }
    }
    
    @Test
    public void testGetResultOfQueuedTaskBeforeItExecutes() {
        final CountDownLatch latch = new CountDownLatch(1)
        em.submit([tag:"category1"], { latch.await() })
        
        BasicTask t = [ {return 123} ]
        Future future = em.submit tag:"category1", t

        new Thread({Thread.sleep(10);latch.countDown()}).start();
        assertEquals(future.get(), 123)
    }
    
    @Test
    public void testGetResultOfQueuedTaskBeforeItExecutesWithTimeout() {
        final CountDownLatch latch = new CountDownLatch(1)
        em.submit([tag:"category1"], { latch.await() })
        
        BasicTask t = [ {return 123} ]
        Future future = em.submit tag:"category1", t

        try {
            assertEquals(future.get(10, TimeUnit.MILLISECONDS), 123)
            fail()
        } catch (TimeoutException e) {
            // success
        }
    }
    
    @Test
    public void testCancelQueuedTaskBeforeItExecutes() {
        final CountDownLatch latch = new CountDownLatch(1)
        em.submit([tag:"category1"], { latch.await() })
        
        boolean executed = false
        BasicTask t = [ {execututed = true} ]
        Future future = em.submit tag:"category1", t

        future.cancel(true)
        latch.countDown()
        Thread.sleep(10)
        try {
            future.get()
        } catch (CancellationException e) {
            // success
        }
        assertFalse(executed)
    }
    
    @Test
    public void testGetResultOfQueuedTaskAfterItExecutes() {
        final CountDownLatch latch = new CountDownLatch(1)
        em.submit([tag:"category1"], { latch.await() })
        
        BasicTask t = [ {return 123} ]
        Future future = em.submit tag:"category1", t

        latch.countDown()
        assertEquals(future.get(), 123)
    }
}
