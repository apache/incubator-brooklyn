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

import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;

public class PerformanceTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceTestUtils.class);

    private static boolean hasLoggedProcessCpuTimeUnavailable;
    
    public static long getProcessCpuTime() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName osMBeanName = ObjectName.getInstance(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            return (Long) mbeanServer.getAttribute(osMBeanName, "ProcessCpuTime");
        } catch (Exception e) {
            if (!hasLoggedProcessCpuTimeUnavailable) {
                hasLoggedProcessCpuTimeUnavailable = true;
                LOG.warn("ProcessCPuTime not available in local JVM MXBean "+ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME+" (only available in sun JVM?)");
            }
            return -1;
        }
    }

    /**
     * Creates a background thread that will log.info the CPU fraction usage repeatedly, sampling at the given period.
     * Callers <em>must</em> cancel the returned future, e.g. {@code future.cancel(true)}, otherwise it will keep
     * logging until the JVM exits.
     */
    public static Future<?> sampleProcessCpuTime(final Duration period, final String loggingContext) {
        final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "brooklyn-sampleProcessCpuTime-"+loggingContext);
                    thread.setDaemon(true); // let the JVM exit
                    return thread;
                }});
        Future<?> future = executor.submit(new Runnable() {
                @Override public void run() {
                    try {
                        long prevCpuTime = getProcessCpuTime();
                        if (prevCpuTime == -1) {
                            LOG.warn("ProcessCPuTime not available; cannot sample; aborting");
                            return;
                        }
                        while (true) {
                            Stopwatch stopwatch = Stopwatch.createStarted();
                            Thread.sleep(period.toMilliseconds());
                            long currentCpuTime = getProcessCpuTime();
                            
                            long elapsedTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
                            double fractionCpu = (elapsedTime > 0) ? ((double)currentCpuTime-prevCpuTime) / TimeUnit.MILLISECONDS.toNanos(elapsedTime) : -1;
                            prevCpuTime = currentCpuTime;
                            
                            LOG.info("CPU fraction over last {} was {} ({})", new Object[] {
                                    Time.makeTimeStringRounded(elapsedTime), fractionCpu, loggingContext});
                        }
                    } catch (InterruptedException e) {
                        return; // graceful termination
                    } finally {
                        executor.shutdownNow();
                    }
                }});
        return future;
    }
}
