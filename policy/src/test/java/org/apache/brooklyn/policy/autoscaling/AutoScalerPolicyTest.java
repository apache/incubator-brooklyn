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
package org.apache.brooklyn.policy.autoscaling;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.test.entity.TestCluster;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.basic.BasicNotificationSensor;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class AutoScalerPolicyTest {

    private static final Logger log = LoggerFactory.getLogger(AutoScalerPolicyTest.class);
    
    private static long TIMEOUT_MS = 10*1000;
    private static long SHORT_WAIT_MS = 250;
    private static long OVERHEAD_DURATION_MS = 500;
    private static long EARLY_RETURN_MS = 10;

    private static final int MANY_TIMES_INVOCATION_COUNT = 10;
    
    AutoScalerPolicy policy;
    TestCluster cluster;
    LocallyResizableEntity resizable;
    TestApplication app;
    List<Integer> policyResizes = MutableList.of();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        log.info("resetting "+getClass().getSimpleName());
        app = TestApplication.Factory.newManagedInstanceForTests();
        cluster = app.createAndManageChild(EntitySpec.create(TestCluster.class).configure(TestCluster.INITIAL_SIZE, 1));
        resizable = new LocallyResizableEntity(cluster, cluster);
        Entities.manage(resizable);
        PolicySpec<AutoScalerPolicy> policySpec = PolicySpec.create(AutoScalerPolicy.class).configure(AutoScalerPolicy.RESIZE_OPERATOR, new ResizeOperator() {
            @Override
            public Integer resize(Entity entity, Integer desiredSize) {
                log.info("resizing to "+desiredSize);
                policyResizes.add(desiredSize);
                return ((Resizable)entity).resize(desiredSize);
            }
        });
        policy = resizable.addPolicy(policySpec);
        policyResizes.clear();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (policy != null) policy.destroy();
        if (app != null) Entities.destroyAll(app.getManagementContext());
        cluster = null;
        resizable = null;
        policy = null;
    }

    public void assertSizeEventually(Integer targetSize) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, targetSize));
        assertEquals(policyResizes.get(policyResizes.size()-1), targetSize);
    }
    
    @Test
    public void testShrinkColdPool() throws Exception {
        resizable.resize(4);
        // all metrics as per-node here
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(30d/4, 10, 20));
        
        // expect pool to shrink to 3 (i.e. maximum to have >= 10 per container)
        assertSizeEventually(3);
    }
    
    @Test
    public void testShrinkColdPoolTotals() throws Exception {
        resizable.resize(4);
        // all metrics as totals here
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(30L, 4*10L, 4*20L));
        
        // expect pool to shrink to 3 (i.e. maximum to have >= 10 per container)
        assertSizeEventually(3);
    }
    
    @Test
    public void testShrinkColdPoolRoundsUpDesiredNumberOfContainers() throws Exception {
        resizable.resize(4);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(1L, 4*10L, 4*20L));
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 1));
    }

    @Test
    public void testGrowHotPool() throws Exception {
        resizable.resize(2);
        // all metrics as per-node here
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(21L, 10L, 20L));
        
        // expect pool to grow to 3 (i.e. minimum to have <= 20 per container)
        assertSizeEventually(3);
    }

    @Test
    public void testGrowHotPoolTotals() throws Exception {
        resizable.resize(2);
        // all metrics as totals here
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(41L, 2*10L, 2*20L));
        
        // expect pool to grow to 3 (i.e. minimum to have <= 20 per container)
        assertSizeEventually(3);
    }

    @Test
    public void testGrowShrinkRespectsResizeIterationIncrementAndResizeIterationMax() throws Exception {
        resizable.resize(2);
        policy.config().set(AutoScalerPolicy.RESIZE_UP_ITERATION_INCREMENT, 2);
        policy.config().set(AutoScalerPolicy.RESIZE_UP_ITERATION_MAX, 4);
        policy.config().set(AutoScalerPolicy.RESIZE_DOWN_ITERATION_INCREMENT, 3);
        policy.config().set(AutoScalerPolicy.RESIZE_DOWN_ITERATION_MAX, 3);
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(42/2, 10, 20));
        // expect pool to grow to 4 (i.e. to have <= 20 per container we need 3, but increment is 2)
        assertSizeEventually(4);
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(200/4, 10, 20));
        // a single hot message can only make it go to 8
        assertSizeEventually(8);
        assertEquals(policyResizes, MutableList.of(4, 8));
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(200/8, 10, 20));
        assertSizeEventually(10);
        assertEquals(policyResizes, MutableList.of(4, 8, 10));
        
        // now shrink
        policyResizes.clear();
        policy.config().set(AutoScalerPolicy.MIN_POOL_SIZE, 2);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(1, 10, 20));
        assertSizeEventually(7);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(1, 10, 20));
        assertSizeEventually(4);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(1, 10, 20));
        assertSizeEventually(2);
        assertEquals(policyResizes, MutableList.of(7, 4, 2));
    }

    @Test
    public void testHasId() throws Exception {
        resizable.removePolicy(policy);
        policy = AutoScalerPolicy.builder()
                .minPoolSize(2)
                .build();
        resizable.addPolicy(policy);
        Assert.assertTrue(policy.getId()!=null);
    }
    
    @Test
    public void testNeverShrinkBelowMinimum() throws Exception {
        resizable.removePolicy(policy);
        policy = AutoScalerPolicy.builder()
                .minPoolSize(2)
                .build();
        resizable.addPolicy(policy);
        
        resizable.resize(4);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(4, 0L, 4*10L, 4*20L));
        
        // expect pool to shrink only to the minimum
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 2));
    }
    
    @Test
    public void testNeverGrowAboveMaximmum() throws Exception {
        resizable.removePolicy(policy);
        policy = AutoScalerPolicy.builder()
                .maxPoolSize(5)
                .build();
        resizable.addPolicy(policy);
        
        resizable.resize(4);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(4, 1000000L, 4*10L, 4*20L));
        
        // expect pool to grow only to the maximum
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 5));
    }
    
    @Test
    public void testNeverGrowColdPool() throws Exception {
        resizable.resize(2);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(2, 1000L, 2*10L, 2*20L));
        
        Thread.sleep(SHORT_WAIT_MS);
        assertEquals(resizable.getCurrentSize(), (Integer)2);
    }
    
    @Test
    public void testNeverShrinkHotPool() throws Exception {
        resizable.resizeSleepTime = 0;
        resizable.resize(2);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(2, 0L, 2*10L, 2*20L));
        
        // if had been a POOL_COLD, would have shrunk to 3
        Thread.sleep(SHORT_WAIT_MS);
        assertEquals(resizable.getCurrentSize(), (Integer)2);
    }
    
    @Test(groups="Integration")
    public void testConcurrentShrinkShrink() throws Exception {
        resizable.resizeSleepTime = 250;
        resizable.resize(4);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(4, 30L, 4*10L, 4*20L));
        // would cause pool to shrink to 3
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(4, 1L, 4*10L, 4*20L));
        // now expect pool to shrink to 1
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 1));
    }
    
    @Test(groups="Integration")
    public void testConcurrentGrowGrow() throws Exception {
        resizable.resizeSleepTime = 250;
        resizable.resize(2);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(2, 41L, 2*10L, 2*20L));
        // would cause pool to grow to 3
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(2, 81L, 2*10L, 2*20L));
        // now expect pool to grow to 5
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 5));
    }
    
    @Test(groups="Integration")
    public void testConcurrentGrowShrink() throws Exception {
        resizable.resizeSleepTime = 250;
        resizable.resize(2);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(2, 81L, 2*10L, 2*20L));
        // would cause pool to grow to 5
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(2, 1L, 2*10L, 2*20L));
        // now expect pool to shrink to 1
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 1));
    }
    
    @Test(groups="Integration")
    public void testConcurrentShrinkGrow() throws Exception {
        resizable.resizeSleepTime = 250;
        resizable.resize(4);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(4, 1L, 4*10L, 4*20L));
        // would cause pool to shrink to 1
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(4, 81L, 4*10L, 4*20L));
        // now expect pool to grow to 5
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 5));
    }
    
    // FIXME failed in jenkins (e.g. #1035); with "lists don't have the same size expected:<3> but was:<2>"
    // Is it just too time sensitive? But I'd have expected > 3 rather than less
    @Test(groups="WIP")
    public void testRepeatedQueuedResizeTakesLatestValueRatherThanIntermediateValues() throws Exception {
        // TODO is this too time sensitive? the resize takes only 250ms so if it finishes before the next emit we'd also see size=2
        resizable.resizeSleepTime = 500;
        resizable.resize(4);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(4, 30L, 4*10L, 4*20L)); // shrink to 3
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(4, 20L, 4*10L, 4*20L)); // shrink to 2
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(4, 10L, 4*10L, 4*20L)); // shrink to 1
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 1));
        assertEquals(resizable.sizes, ImmutableList.of(4, 3, 1));
    }
    

    @Test
    public void testUsesResizeOperatorOverride() throws Exception {
        resizable.removePolicy(policy);
        
        final AtomicInteger counter = new AtomicInteger();
        policy = AutoScalerPolicy.builder()
                .resizeOperator(new ResizeOperator() {
                        @Override public Integer resize(Entity entity, Integer desiredSize) {
                            counter.incrementAndGet();
                            return desiredSize;
                        }})
                .build();
        resizable.addPolicy(policy);
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(1, 21L, 1*10L, 1*20L)); // grow to 2
        
        Asserts.succeedsEventually(MutableMap.of("timeout",TIMEOUT_MS), new Runnable() {
                public void run() {
                    assertTrue(counter.get() >= 1, "cccounter="+counter);
                }});
    }
    
    @Test
    public void testUsesCustomSensorOverride() throws Exception {
        resizable.removePolicy(policy);
        
        @SuppressWarnings("rawtypes")
        BasicNotificationSensor<Map> customPoolHotSensor = new BasicNotificationSensor<Map>(Map.class, "custom.hot", "");
        @SuppressWarnings("rawtypes")
        BasicNotificationSensor<Map> customPoolColdSensor = new BasicNotificationSensor<Map>(Map.class, "custom.cold", "");
        @SuppressWarnings("rawtypes")
        BasicNotificationSensor<Map> customPoolOkSensor = new BasicNotificationSensor<Map>(Map.class, "custom.ok", "");
        policy = AutoScalerPolicy.builder()
                .poolHotSensor(customPoolHotSensor) 
                .poolColdSensor(customPoolColdSensor)
                .poolOkSensor(customPoolOkSensor)
                .build();
        resizable.addPolicy(policy);
        
        resizable.emit(customPoolHotSensor, message(1, 21L, 1*10L, 1*20L)); // grow to 2
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 2));
        
        resizable.emit(customPoolColdSensor, message(2, 1L, 1*10L, 1*20L)); // shrink to 1
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), currentSizeAsserter(resizable, 1));
    }
    
    @Test(groups="Integration")
    public void testResizeUpStabilizationDelayIgnoresBlip() throws Exception {
        long resizeUpStabilizationDelay = 1000L;
        Duration minPeriodBetweenExecs = Duration.ZERO;
        resizable.removePolicy(policy);
        
        policy = AutoScalerPolicy.builder()
                .resizeUpStabilizationDelay(Duration.of(resizeUpStabilizationDelay, TimeUnit.MILLISECONDS)) 
                .minPeriodBetweenExecs(minPeriodBetweenExecs)
                .build();
        resizable.addPolicy(policy);
        resizable.resize(1);
        
        // Ignores temporary blip
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(1, 61L, 1*10L, 1*20L)); // would grow to 4
        Thread.sleep(resizeUpStabilizationDelay-OVERHEAD_DURATION_MS);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_OK_SENSOR, message(1, 11L, 4*10L, 4*20L)); // but 1 is still adequate
        
        assertEquals(resizable.getCurrentSize(), (Integer)1);
        Asserts.succeedsContinually(MutableMap.of("duration", 2000L), new Runnable() {
                @Override public void run() {
                    assertEquals(resizable.sizes, ImmutableList.of(1));
                }});
    }

    // FIXME failing in jenkins occassionally - have put it in the "Acceptance" group for now
    //
    // Error was things like it taking a couple of seconds too long to scale-up. This is *not*
    // just caused by a slow GC (running with -verbose:gc shows during a failure several 
    // incremental GCs that usually don't amount to more than 0.2 of a second at most, often less).
    // Doing a thread-dump etc immediately after the too-long delay shows no strange thread usage,
    // and shows releng3 system load averages of numbers like 1.73, 2.87 and 1.22.
    // 
    // Is healthy on normal machines.
    @Test(groups={"Integration", "Acceptance"}, invocationCount=MANY_TIMES_INVOCATION_COUNT)
    public void testRepeatedResizeUpStabilizationDelayTakesMaxSustainedDesired() throws Throwable {
        try {
            testResizeUpStabilizationDelayTakesMaxSustainedDesired();
        } catch (Throwable t) {
            dumpThreadsEtc();
            throw t;
        }
    }

    @Test(groups="Integration")
    public void testResizeUpStabilizationDelayTakesMaxSustainedDesired() throws Exception {
        long resizeUpStabilizationDelay = 1100L;
        Duration minPeriodBetweenExecs = Duration.ZERO;
        resizable.removePolicy(policy);
        
        policy = AutoScalerPolicy.builder()
                .resizeUpStabilizationDelay(Duration.of(resizeUpStabilizationDelay, TimeUnit.MILLISECONDS)) 
                .minPeriodBetweenExecs(minPeriodBetweenExecs)
                .build();
        resizable.addPolicy(policy);
        resizable.resize(1);
        
        // Will grow to only the max sustained in this time window 
        // (i.e. to 2 within the first $resizeUpStabilizationDelay milliseconds)
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(1, 61L, 1*10L, 1*20L)); // would grow to 4
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(1, 21L, 1*10L, 1*20L)); // would grow to 2
        Thread.sleep(resizeUpStabilizationDelay-OVERHEAD_DURATION_MS);
        
        long postSleepTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, message(1, 61L, 1*10L, 1*20L)); // would grow to 4

        // Wait for it to reach size 2, and confirm take expected time
        // TODO This is time sensitive, and sometimes fails in CI with size=4 if we wait for currentSize==2 (presumably GC kicking in?)
        //      Therefore do strong assertion of currentSize==2 later, so can write out times if it goes wrong.
        Asserts.succeedsEventually(MutableMap.of("period", 1, "timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertTrue(resizable.getCurrentSize() >= 2, "currentSize="+resizable.getCurrentSize());
            }});
        assertEquals(resizable.getCurrentSize(), (Integer)2, 
                stopwatch.elapsed(TimeUnit.MILLISECONDS)+"ms after first emission; "+(stopwatch.elapsed(TimeUnit.MILLISECONDS)-postSleepTime)+"ms after last");
        
        long timeToResizeTo2 = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        assertTrue(timeToResizeTo2 >= resizeUpStabilizationDelay-EARLY_RETURN_MS &&
                timeToResizeTo2 <= resizeUpStabilizationDelay+OVERHEAD_DURATION_MS,
                "Resizing to 2: time="+timeToResizeTo2+"; resizeUpStabilizationDelay="+resizeUpStabilizationDelay);

        // Will then grow to 4 $resizeUpStabilizationDelay milliseconds after that emission
        Asserts.succeedsEventually(MutableMap.of("period", 1, "timeout", TIMEOUT_MS), 
                currentSizeAsserter(resizable, 4));
        long timeToResizeTo4 = stopwatch.elapsed(TimeUnit.MILLISECONDS) - postSleepTime;
        
        assertTrue(timeToResizeTo4 >= resizeUpStabilizationDelay-EARLY_RETURN_MS &&
                timeToResizeTo4 <= resizeUpStabilizationDelay+OVERHEAD_DURATION_MS,
                "Resizing to 4: timeToResizeTo4="+timeToResizeTo4+"; timeToResizeTo2="+timeToResizeTo2+"; resizeUpStabilizationDelay="+resizeUpStabilizationDelay);
    }

    @Test(groups="Integration")
    public void testResizeUpStabilizationDelayResizesAfterDelay() {
        final long resizeUpStabilizationDelay = 1000L;
        Duration minPeriodBetweenExecs = Duration.ZERO;
        resizable.removePolicy(policy);
        
        policy = resizable.addPolicy(AutoScalerPolicy.builder()
                .resizeUpStabilizationDelay(Duration.of(resizeUpStabilizationDelay, TimeUnit.MILLISECONDS)) 
                .minPeriodBetweenExecs(minPeriodBetweenExecs)
                .buildSpec());
        resizable.resize(1);
        
        // After suitable delay, grows to desired
        final long emitTime = System.currentTimeMillis();
        final Map<String, Object> need4 = message(1, 61L, 1*10L, 1*20L);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, need4); // would grow to 4
        final AtomicInteger emitCount = new AtomicInteger(0);
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                if (System.currentTimeMillis() - emitTime > (2+emitCount.get())*resizeUpStabilizationDelay) {
                    //first one may not have been received, in a registration race 
                    resizable.emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, need4);
                    emitCount.incrementAndGet();
                }
                assertEquals(resizable.getCurrentSize(), (Integer)4);
            }});
        
        long resizeDelay = System.currentTimeMillis() - emitTime;
        assertTrue(resizeDelay >= (resizeUpStabilizationDelay-EARLY_RETURN_MS), "resizeDelay="+resizeDelay);
    }

    @Test(groups="Integration")
    public void testResizeDownStabilizationDelayIgnoresBlip() throws Exception {
        long resizeStabilizationDelay = 1000L;
        Duration minPeriodBetweenExecs = Duration.ZERO;
        resizable.removePolicy(policy);
        
        policy = AutoScalerPolicy.builder()
                .resizeDownStabilizationDelay(Duration.of(resizeStabilizationDelay, TimeUnit.MILLISECONDS)) 
                .minPeriodBetweenExecs(minPeriodBetweenExecs)
                .build();
        resizable.addPolicy(policy);
        resizable.resize(2);
        
        // Ignores temporary blip
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(2, 1L, 2*10L, 2*20L)); // would shrink to 1
        Thread.sleep(resizeStabilizationDelay-OVERHEAD_DURATION_MS);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_OK_SENSOR, message(2, 20L, 1*10L, 1*20L)); // but 2 is still adequate
        
        assertEquals(resizable.getCurrentSize(), (Integer)2);
        Asserts.succeedsContinually(MutableMap.of("duration", 2000L), new Runnable() {
                public void run() {
                    assertEquals(resizable.sizes, ImmutableList.of(2));
                }});
    }

    // FIXME Acceptance -- see comment against testRepeatedResizeUpStabilizationDelayTakesMaxSustainedDesired
    @Test(groups={"Integration", "Acceptance"}, invocationCount=MANY_TIMES_INVOCATION_COUNT)
    public void testRepeatedResizeDownStabilizationDelayTakesMinSustainedDesired() throws Throwable {
        try {
            testResizeDownStabilizationDelayTakesMinSustainedDesired();
        } catch (Throwable t) {
            dumpThreadsEtc();
            throw t;
        }
    }
    
    @Test(groups="Integration")
    public void testResizeDownStabilizationDelayTakesMinSustainedDesired() throws Exception {
        long resizeDownStabilizationDelay = 1100L;
        Duration minPeriodBetweenExecs = Duration.ZERO;
        policy.suspend();
        resizable.removePolicy(policy);
        
        policy = AutoScalerPolicy.builder()
                .resizeDownStabilizationDelay(Duration.of(resizeDownStabilizationDelay, TimeUnit.MILLISECONDS))
                .minPeriodBetweenExecs(minPeriodBetweenExecs)
                .build();
        resizable.addPolicy(policy);
        resizable.resize(3);
        
        // Will shrink to only the min sustained in this time window
        // (i.e. to 2 within the first $resizeUpStabilizationDelay milliseconds)
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(3, 1L, 3*10L, 3*20L)); // would shrink to 1
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(3, 20L, 3*10L, 3*20L)); // would shrink to 2
        Thread.sleep(resizeDownStabilizationDelay-OVERHEAD_DURATION_MS);
        
        long postSleepTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, message(3, 1L, 3*10L, 3*20L)); // would shrink to 1

        // Wait for it to reach size 2, and confirm take expected time
        // TODO This is time sensitive, and sometimes fails in CI with size=1 if we wait for currentSize==2 (presumably GC kicking in?)
        //      Therefore do strong assertion of currentSize==2 later, so can write out times if it goes wrong.
        Asserts.succeedsEventually(MutableMap.of("period", 1, "timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    assertTrue(resizable.getCurrentSize() <= 2, "currentSize="+resizable.getCurrentSize());
                }});
        assertEquals(resizable.getCurrentSize(), (Integer)2, 
                stopwatch.elapsed(TimeUnit.MILLISECONDS)+"ms after first emission; "+(stopwatch.elapsed(TimeUnit.MILLISECONDS)-postSleepTime)+"ms after last");
        
        long timeToResizeTo2 = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        assertTrue(timeToResizeTo2 >= resizeDownStabilizationDelay-EARLY_RETURN_MS &&
                timeToResizeTo2 <= resizeDownStabilizationDelay+OVERHEAD_DURATION_MS,
                "Resizing to 2: time="+timeToResizeTo2+"; resizeDownStabilizationDelay="+resizeDownStabilizationDelay);

        // Will then shrink to 1 $resizeUpStabilizationDelay milliseconds after that emission
        Asserts.succeedsEventually(MutableMap.of("period", 1, "timeout", TIMEOUT_MS), 
                currentSizeAsserter(resizable, 1));
        long timeToResizeTo1 = stopwatch.elapsed(TimeUnit.MILLISECONDS) - postSleepTime;
        
        assertTrue(timeToResizeTo1 >= resizeDownStabilizationDelay-EARLY_RETURN_MS &&
                timeToResizeTo1 <= resizeDownStabilizationDelay+OVERHEAD_DURATION_MS,
                "Resizing to 1: timeToResizeTo1="+timeToResizeTo1+"; timeToResizeTo2="+timeToResizeTo2+"; resizeDownStabilizationDelay="+resizeDownStabilizationDelay);
    }

    @Test(groups="Integration")
    public void testResizeDownStabilizationDelayResizesAfterDelay() throws Exception {
        final long resizeDownStabilizationDelay = 1000L;
        Duration minPeriodBetweenExecs = Duration.ZERO;
        resizable.removePolicy(policy);
        
        policy = AutoScalerPolicy.builder()
                .resizeDownStabilizationDelay(Duration.of(resizeDownStabilizationDelay, TimeUnit.MILLISECONDS))
                .minPeriodBetweenExecs(minPeriodBetweenExecs)
                .build();
        resizable.addPolicy(policy);
        resizable.resize(2);
        
        // After suitable delay, grows to desired
        final long emitTime = System.currentTimeMillis();
        final Map<String, Object> needJust1 = message(2, 1L, 2*10L, 2*20L);
        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, needJust1); // would shrink to 1
        final AtomicInteger emitCount = new AtomicInteger(0);
        
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    if (System.currentTimeMillis() - emitTime > (2+emitCount.get())*resizeDownStabilizationDelay) {
                        //first one may not have been received, in a registration race
                        resizable.emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, needJust1); // would shrink to 1
                        emitCount.incrementAndGet();
                    }
                    assertEquals(resizable.getCurrentSize(), (Integer)1);
                }});

        long resizeDelay = System.currentTimeMillis() - emitTime;
        assertTrue(resizeDelay >= (resizeDownStabilizationDelay-EARLY_RETURN_MS), "resizeDelay="+resizeDelay);
    }

    Map<String, Object> message(double currentWorkrate, double lowThreshold, double highThreshold) {
        return message(resizable.getCurrentSize(), currentWorkrate, lowThreshold, highThreshold);
    }
    static Map<String, Object> message(int currentSize, double currentWorkrate, double lowThreshold, double highThreshold) {
        return ImmutableMap.<String,Object>of(
            AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, currentSize,
            AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, currentWorkrate,
            AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, lowThreshold,
            AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, highThreshold);
    }
    
    public static Runnable currentSizeAsserter(final Resizable resizable, final Integer desired) {
        return new Runnable() {
            public void run() {
                assertEquals(resizable.getCurrentSize(), desired);
            }
        };
    }
    
    public static void dumpThreadsEtc() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadMXBean.dumpAllThreads(true, true);
        for (ThreadInfo thread : threads) {
            System.out.println(thread.getThreadName()+" ("+thread.getThreadState()+")");
            for (StackTraceElement stackTraceElement : thread.getStackTrace()) {
                System.out.println("\t"+stackTraceElement);
            }
        }
        
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        System.out.println("Memory:");
        System.out.println("\tHeap: used="+heapMemoryUsage.getUsed()+"; max="+heapMemoryUsage.getMax()+"; init="+heapMemoryUsage.getInit()+"; committed="+heapMemoryUsage.getCommitted());
        System.out.println("\tNon-heap: used="+nonHeapMemoryUsage.getUsed()+"; max="+nonHeapMemoryUsage.getMax()+"; init="+nonHeapMemoryUsage.getInit()+"; committed="+nonHeapMemoryUsage.getCommitted());

        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        System.out.println("OS:");
        System.out.println("\tsysLoadAvg="+operatingSystemMXBean.getSystemLoadAverage()+"; availableProcessors="+operatingSystemMXBean.getAvailableProcessors()+"; arch="+operatingSystemMXBean.getArch());
    }
}
