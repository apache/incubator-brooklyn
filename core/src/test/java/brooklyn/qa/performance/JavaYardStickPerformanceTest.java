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
package brooklyn.qa.performance;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Throwables;

public class JavaYardStickPerformanceTest extends AbstractPerformanceTest {

    protected static final long TIMEOUT_MS = 10*1000;

    private ExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        executor = Executors.newCachedThreadPool();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        super.tearDown();
        if (executor != null) executor.shutdownNow();
    }
    
    @Test(groups={"Integration", "Acceptance"})
    public void testPureJavaNoopToEnsureTestFrameworkIsVeryFast() {
        int numIterations = 1000000;
        double minRatePerSec = 1000000 * PERFORMANCE_EXPECTATION;
        final int[] i = {0};
        measureAndAssert("noop-java", numIterations, minRatePerSec, new Runnable() {
            @Override public void run() {
                i[0] = i[0] + 1;
            }});
        
        assertTrue(i[0] >= numIterations, "i="+i);
    }

    @Test(groups={"Integration", "Acceptance"})
    public void testPureJavaScheduleExecuteAndGet() {
        int numIterations = 100000;
        double minRatePerSec = 100000 * PERFORMANCE_EXPECTATION;
        final int[] i = {0};
        measureAndAssert("scheduleExecuteAndGet-java", numIterations, minRatePerSec, new Runnable() {
            @Override public void run() {
                Future<?> future = executor.submit(new Runnable() { public void run() { i[0] = i[0] + 1; }});
                try {
                    future.get();
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }});
        
        assertTrue(i[0] >= numIterations, "i="+i);
    }
}
