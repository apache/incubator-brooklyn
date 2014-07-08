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

import static org.testng.Assert.assertTrue

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

public class GroovyYardStickPerformanceTest extends AbstractPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(GroovyYardStickPerformanceTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;

    private ExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        super.setUp();
        executor = Executors.newCachedThreadPool();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        super.tearDown();
        if (executor != null) executor.shutdownNow();
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testGroovyNoopToEnsureTestFrameworkIsVeryFast() {
        int numIterations = 1000000;
        double minRatePerSec = 1000000 * PERFORMANCE_EXPECTATION;
        AtomicInteger i = new AtomicInteger();
        
        measureAndAssert("noop-groovy", numIterations, minRatePerSec, { i.incrementAndGet() });
        assertTrue(i.get() >= numIterations, "i="+i);
    }
}
