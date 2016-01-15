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

import static org.testng.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.test.performance.PerformanceTestDescriptor;
import org.apache.brooklyn.test.performance.PerformanceTestResult;
import org.apache.brooklyn.test.performance.PerformanceMeasurer;
import org.apache.brooklyn.util.internal.DoubleSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.google.common.base.Stopwatch;

/**
 * For running simplistic performance tests, to measure the number of operations per second and compare 
 * it against some min rate.
 * 
 * This is "good enough" for eye-balling performance, to spot if it goes horrendously wrong. 
 * 
 * However, good performance measurement involves much more warm up (e.g. to ensure java HotSpot 
 * optimisation have been applied), and running the test for a reasonable length of time.
 * We are also not running the tests for long enough to check if object creation is going to kill
 * performance in the long-term, etc.
 */
public class AbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractPerformanceTest.class);
    
    public static final DoubleSystemProperty PERFORMANCE_EXPECTATION_SYSPROP = 
            new DoubleSystemProperty("brooklyn.test.performanceExpectation");
    
    /**
     * A scaling factor for the expected performance, where 1 is a conservative expectation of
     * minimum to expect every time in normal circumstances.
     * 
     * However, for running in CI, defaults to 0.1 so if GC kicks in during the test we won't fail...
     */
    public static double PERFORMANCE_EXPECTATION = PERFORMANCE_EXPECTATION_SYSPROP.isAvailable() ? 
            PERFORMANCE_EXPECTATION_SYSPROP.getValue() : 0.1d;
    
    protected static final long TIMEOUT_MS = 10*1000;
    
    protected TestApplication app;
    protected SimulatedLocation loc;
    protected ManagementContext mgmt;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        for (int i = 0; i < 5; i++) System.gc();
        loc = new SimulatedLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        mgmt = app.getManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    protected PerformanceTestResult measure(PerformanceTestDescriptor options) {
        PerformanceTestResult result = PerformanceMeasurer.run(options);
        System.out.println("test="+options+"; result="+result);
        return result;
    }

    /**
     * @deprecated since 0.9.0; use {@link #measure(PerformanceTestDescriptor)}
     */
    @Deprecated
    protected void measureAndAssert(String prefix, int numIterations, double minRatePerSec, Runnable r) {
        measure(PerformanceTestDescriptor.create()
                .summary(prefix)
                .iterations(numIterations)
                .minAcceptablePerSecond(minRatePerSec)
                .job(r));
    }

    /**
     * @deprecated since 0.9.0; use {@link #measure(PerformanceTestDescriptor)}
     */
    @Deprecated
    protected void measureAndAssert(String prefix, int numIterations, double minRatePerSec, Runnable r, CountDownLatch completionLatch) {
        measure(PerformanceTestDescriptor.create()
                .summary(prefix)
                .iterations(numIterations)
                .completionLatch(completionLatch)
                .minAcceptablePerSecond(minRatePerSec)
                .job(r));
    }
    
    /**
     * @deprecated since 0.9.0; use {@link #measure(PerformanceTestDescriptor)}
     */
    @Deprecated
    protected void measureAndAssert(String prefix, int numIterations, double minRatePerSec, Runnable r, Runnable postIterationPhase) {
        long durationMillis = measure(prefix, numIterations, r);
        long postIterationDurationMillis = (postIterationPhase != null) ? measure(postIterationPhase) : 0;
        
        double numPerSec = ((double)numIterations/durationMillis * 1000);
        double numPerSecIncludingPostIteration = ((double)numIterations/(durationMillis+postIterationDurationMillis) * 1000);
        
        String msg1 = prefix+": "+durationMillis+"ms for "+numIterations+" iterations"+
                    (postIterationPhase != null ? "(+"+postIterationDurationMillis+"ms for post-iteration phase)" : "")+
                    ": numPerSec="+numPerSec+"; minAcceptableRate="+minRatePerSec;
        String msg2 = (postIterationPhase != null ? " (or "+numPerSecIncludingPostIteration+" per sec including post-iteration phase time)" : "");
        
        LOG.info(msg1+msg2);
        System.out.println("\n"+msg1+"\n"+msg2+"\n");  //make it easier to see in the console in eclipse :)
        assertTrue(numPerSecIncludingPostIteration >= minRatePerSec, msg1+msg2);
    }
    
    /**
     * @deprecated since 0.9.0; use {@link #measure(PerformanceTestDescriptor)}
     */
    @Deprecated
    protected long measure(String prefix, int numIterations, Runnable r) {
        final int logInterval = 5*1000;
        long nextLogTime = logInterval;
        
        // Give it some warm-up cycles
        Stopwatch warmupWatch = Stopwatch.createStarted();
        for (int i = 0; i < (numIterations/10); i++) {
            if (warmupWatch.elapsed(TimeUnit.MILLISECONDS) >= nextLogTime) {
                LOG.info("Warm-up "+prefix+" iteration="+i+" at "+warmupWatch.elapsed(TimeUnit.MILLISECONDS)+"ms");
                nextLogTime += logInterval;
            }
            r.run();
        }
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        nextLogTime = 0;
        for (int i = 0; i < numIterations; i++) {
            if (stopwatch.elapsed(TimeUnit.MILLISECONDS) >= nextLogTime) {
                LOG.info(prefix+" iteration="+i+" at "+stopwatch.elapsed(TimeUnit.MILLISECONDS)+"ms");
                nextLogTime += logInterval;
            }
            r.run();
        }
        return stopwatch.elapsed(TimeUnit.MILLISECONDS);
    }
    
    /**
     * @deprecated since 0.9.0; use {@link #measure(PerformanceTestDescriptor)}
     */
    @Deprecated
    protected long measure(Runnable r) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        r.run();
        return stopwatch.elapsed(TimeUnit.MILLISECONDS);
    }
}
