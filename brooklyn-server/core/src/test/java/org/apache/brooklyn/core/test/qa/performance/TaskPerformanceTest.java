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
package org.apache.brooklyn.core.test.qa.performance;


import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.test.performance.PerformanceTestDescriptor;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;
import org.apache.brooklyn.util.core.task.SingleThreadedScheduler;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class TaskPerformanceTest extends AbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(TaskPerformanceTest.class);
    
    BasicExecutionManager executionManager;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        app.start(ImmutableList.of(loc));
        
        executionManager = (BasicExecutionManager) app.getManagementContext().getExecutionManager();
    }

    public static final int numIterations = 200000;

    @Test(groups={"Integration", "Acceptance"})
    public void testExecuteSimplestRunnable() {
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        
        final Runnable work = new Runnable() {
            public void run() {
                int val = counter.incrementAndGet();
                if (val >= numIterations) completionLatch.countDown();
            }};

        measure(PerformanceTestDescriptor.create()
                .summary("TaskPerformanceTest.testExecuteSimplestRunnable")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        executionManager.submit(work);
                    }})
                .completionLatch(completionLatch));
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testExecuteRunnableWithTags() {
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;
        
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);

        final Runnable work = new Runnable() { public void run() {
                int val = counter.incrementAndGet();
                if (val >= numIterations) completionLatch.countDown();
            }
        };

        final Map<String, ?> flags = MutableMap.of("tags", ImmutableList.of("a","b"));
        
        measure(PerformanceTestDescriptor.create()
                .summary("TaskPerformanceTest.testExecuteRunnableWithTags")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        executionManager.submit(flags, work);
                    }})
                .completionLatch(completionLatch));
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testExecuteWithSingleThreadedScheduler() throws Exception {
        double minRatePerSec = 1000 * PERFORMANCE_EXPECTATION;

        executionManager.setTaskSchedulerForTag("singlethreaded", SingleThreadedScheduler.class);
        
        final AtomicInteger concurrentCallCount = new AtomicInteger();
        final AtomicInteger submitCount = new AtomicInteger();
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        final List<Exception> exceptions = Lists.newCopyOnWriteArrayList();
        
        final Runnable work = new Runnable() { public void run() {
                int numConcurrentCalls = concurrentCallCount.incrementAndGet();
                try {
                    if (numConcurrentCalls > 1) throw new IllegalStateException("numConcurrentCalls="+numConcurrentCalls);
                    int val = counter.incrementAndGet();
                    if (val >= numIterations) completionLatch.countDown();
                } catch (Exception e) {
                    exceptions.add(e);
                    LOG.warn("Exception in runnable of testExecuteWithSingleThreadedScheduler", e);
                    throw Exceptions.propagate(e);
                } finally {
                    concurrentCallCount.decrementAndGet();
                }
            }
        };

        measure(PerformanceTestDescriptor.create()
                .summary("TaskPerformanceTest.testExecuteWithSingleThreadedScheduler")
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(new Runnable() {
                    public void run() {
                        while (submitCount.get() > counter.get() + 5000) {
                            LOG.info("delaying because "+submitCount.get()+" submitted and only "+counter.get()+" run");
                            Time.sleep(500);
                        }
                        executionManager.submit(MutableMap.of("tags", ImmutableList.of("singlethreaded")), work); 
                        submitCount.incrementAndGet();
                    }})
                .completionLatch(completionLatch));
        
        if (exceptions.size() > 0) throw exceptions.get(0);
    }
    
    public static void main(String[] args) throws Exception {
        TaskPerformanceTest t = new TaskPerformanceTest();
        t.setUp();
        try {
            t.testExecuteWithSingleThreadedScheduler();
        } finally {
            t.tearDown();
        }
    }
}
