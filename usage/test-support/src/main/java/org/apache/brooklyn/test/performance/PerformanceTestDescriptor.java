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
package org.apache.brooklyn.test.performance;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import org.apache.brooklyn.util.time.Duration;
import org.apache.commons.io.FileUtils;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;

/**
 * For building up a description of what to measure.
 * <p>
 * Users are strongly encouraged to call the setter methods, rather than accessing the fields 
 * directly. The fields may be made protected in a future release.
 * <p>
 * The following fields are compulsory:
 * <ul>
 *   <li>{@link #job(Runnable)}
 *   <li>Exactly one of {@link #duration(Duration)} or {@link #iterations(int)} 
 * </ul>
 * 
 * See {@link PerformanceTestUtils#run(PerformanceTestDescriptor)}.
 */
@Beta
public class PerformanceTestDescriptor {
    public String summary;
    public Duration warmup;
    public Integer warmupIterations;
    public Duration duration;
    public Integer iterations;
    public Runnable job;
    public CountDownLatch completionLatch;
    public Duration completionLatchTimeout = Duration.FIVE_MINUTES;
    public Double minAcceptablePerSecond;
    public Duration sampleCpuInterval = Duration.ONE_SECOND;
    public Duration logInterval = Duration.FIVE_SECONDS;
    public boolean histogram = true;
    public MeasurementResultPersister persister = new FilePersister(new File(FileUtils.getUserDirectory(), "brooklyn-performance"));
    public boolean sealed;
    
    public static PerformanceTestDescriptor create() {
        return new PerformanceTestDescriptor();
    }
    
    public static PerformanceTestDescriptor create(String summary) {
        return create().summary(summary);
    }
    
    public PerformanceTestDescriptor summary(String val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.summary = val; return this;
    }
    
    /**
     * The length of time to repeatedly execute the job for, before doing the proper performance 
     * test. At most one of {@link #warmup(Duration)} or {@link #warmupIterations(int)} should be
     * set - if neither is set, the warmup defaults to one tenth of the test duration.
     */
    public PerformanceTestDescriptor warmup(Duration val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.warmup = val; return this;
    }
    
    /**
     * See {@link #warmup(Duration)}.
     */
    public PerformanceTestDescriptor warmupIterations(int val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.warmupIterations = val; return this;
    }
    
    /**
     * The length of time to repeatedly execute the job for, when measuring the performance.
     * Exactly one of {@link #duration(Duration)} or {@link #iterations(int)} should be
     * set.
     */
    public PerformanceTestDescriptor duration(Duration val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.duration = val; return this;
    }
    
    /**
     * See {@link #duration(Duration)}.
     */
    public PerformanceTestDescriptor iterations(int val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.iterations = val; return this;
    }
    
    /**
     * The job to be repeatedly executed.
     */
    public PerformanceTestDescriptor job(Runnable val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.job = val; return this;
    }
    
    /**
     * If non-null, the performance test will wait for this latch before stopping the timer.
     * This is useful for asynchronous work. For example, 1000 iterations of the job might
     * be executed that each submits work asynchronously, and then the latch signals when all
     * of those 1000 tasks have completed.
     */
    public PerformanceTestDescriptor completionLatch(CountDownLatch val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.completionLatch = val; return this;
    }
    
    /**
     * The maximum length of time to wait for the {@link #completionLatch(CountDownLatch)}, after 
     * executing the designated number of jobs. If the latch has not completed within this time, 
     * then the test will fail.
     */
    public PerformanceTestDescriptor completionLatchTimeout(Duration val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.completionLatchTimeout = val; return this;
    }

    /**
     * If non-null, the measured jobs-per-second will be compared against this number. If the 
     * jobs-per-second is not high enough, then the test wil fail.
     */
    public PerformanceTestDescriptor minAcceptablePerSecond(Double val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.minAcceptablePerSecond = val; return this;
    }
    
    /**
     * Whether to collect a histogram of the individual job times. This histogram stores the count
     * in buckets (e.g. number of jobs that took 1-2ms, number that took 2-4ms, number that took
     * 4-8ms, etc).
     */
    public PerformanceTestDescriptor histogram(boolean val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.histogram = val; return this;
    }
    
    /**
     * How often to log progress (e.g. number of iterations completed so far). If null, then no 
     * progress will be logged.
     */
    public PerformanceTestDescriptor logInterval(Duration val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.logInterval = val; return this;
    }
    
    /**
     * How often to calculate + record the fraction of CPU being used. If null, then CPU usage 
     * will not be recorded. 
     */
    public PerformanceTestDescriptor sampleCpuInterval(Duration val) {
        if (sealed) throw new IllegalStateException("Should not modify after sealed (e.g. after use)");
        this.sampleCpuInterval = val; return this;
    }
    
    public void seal() {
        sealed = true;
        assertNotNull(job, "Job must be supplied: "+toString());
        assertTrue(duration != null ^ iterations != null, "Exactly one of duration or iterations must be set: "+toString());
        assertFalse(warmup != null && warmupIterations != null, "At most one of duration and iterations must be set: "+toString());
        if (warmup == null && warmupIterations == null) {
            if (duration != null) warmup = Duration.millis(duration.toMilliseconds() / 10);
            if (iterations != null) warmupIterations = iterations / 10;
        }
        if (summary == null) {
            summary = job.toString();
        }
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .add("summary", summary)
                .add("duration", duration)
                .add("warmup", warmup)
                .add("iterations", iterations)
                .add("warmupIterations", warmupIterations)
                .add("job", job)
                .add("completionLatch", completionLatch)
                .add("minAcceptablePerSecond", minAcceptablePerSecond)
                .toString();
    }
}