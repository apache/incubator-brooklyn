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
package brooklyn.location.basic;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.qa.performance.PerformanceTestUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.net.Networking;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Test the performance of different variants of invoking the sshj tool.
 * 
 * Intended for human-invocation and inspection, to see which parts are most expensive.
 */
public class SshMachineLocationPerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(SshMachineLocationPerformanceTest.class);
    
    private SshMachineLocation machine;
    private ListeningExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        machine = new SshMachineLocation(MutableMap.of("address", "localhost"));
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }
    
    @AfterMethod(alwaysRun=true)
    public void afterMethod() throws Exception {
        if (executor != null) executor.shutdownNow();
        Streams.closeQuietly(machine);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveSmallCommands() throws Exception {
        runExecManyCommands(ImmutableList.of("true"), "small-cmd", 10);
    }

    // Mimics SshSensorAdapter's polling
    @Test(groups = {"Integration"})
    public void testConsecutiveSmallCommandsWithCustomStdoutAndErr() throws Exception {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        
        Runnable task = new Runnable() {
            @Override public void run() {
                machine.execScript(ImmutableMap.of("out", stdout, "err", stderr), "test", ImmutableList.of("true"));
            }};
        runMany(task, "small-cmd-custom-stdout", 1, 10);
    }

    @Test(groups = {"Integration"})
    public void testConcurrentSmallCommands() throws Exception {
        runExecManyCommands(ImmutableList.of("true"), "small-cmd", 10, 10);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveBigStdoutCommands() throws Exception {
        runExecManyCommands(ImmutableList.of("head -c 100000 /dev/urandom"), "big-stdout", 10);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveBigStdinCommands() throws Exception {
        String bigstr = Identifiers.makeRandomId(100000);
        runExecManyCommands(ImmutableList.of("echo "+bigstr+" | wc -c"), "big-stdin", 10);
    }

    @Test(groups = {"Integration"})
    public void testConsecutiveSmallCommandsWithDifferentProperties() throws Exception {
        final Map<String, ?> emptyProperties = Collections.emptyMap();
        final Map<String, ?> customProperties = MutableMap.of(
                "address", Networking.getLocalHost(),
                SshTool.PROP_SESSION_TIMEOUT.getName(), 20000,
                SshTool.PROP_CONNECT_TIMEOUT.getName(), 50000,
                SshTool.PROP_SCRIPT_HEADER.getName(), "#!/bin/bash");

        Runnable task = new Runnable() {
            @Override public void run() {
                if (Math.random() < 0.5) {
                    machine.execScript(emptyProperties, "test", ImmutableList.of("true"));
                } else {
                    machine.execScript(customProperties, "test", ImmutableList.of("true"));
                }
            }};
        runMany(task, "small-cmd-custom-ssh-properties", 1, 10);
    }

    private void runExecManyCommands(final List<String> cmds, String context, int iterations) throws Exception {
        runExecManyCommands(cmds, context, 1, iterations);
    }
    
    private void runExecManyCommands(final List<String> cmds, String context, int concurrentRuns, int iterations) throws Exception {
        Runnable task = new Runnable() {
                @Override public void run() {
                    execScript(cmds);
                }};
        runMany(task, context, concurrentRuns, iterations);
    }
    
    private void runMany(final Runnable task, final String context, int concurrentRuns, int iterations) throws Exception {
        long preCpuTime = PerformanceTestUtils.getProcessCpuTime();
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (int i = 0; i < iterations; i++) {
            List<ListenableFuture<?>> futures = Lists.newArrayList();
            for (int j = 0; j < concurrentRuns; j++) {
                futures.add(executor.submit(new Runnable() {
                    public void run() {
                        try {
                            task.run();
                        } catch (Exception e) {
                            LOG.error("Error for "+context+", executing "+task, e);
                            throw Throwables.propagate(e);
                        }
                    }}));
            }
            Futures.allAsList(futures).get();
            
            long postCpuTime = PerformanceTestUtils.getProcessCpuTime();
            long elapsedTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            double fractionCpu = (elapsedTime > 0) ? ((double)postCpuTime-preCpuTime) / TimeUnit.MILLISECONDS.toNanos(elapsedTime) : -1;
            LOG.info("Executing {}; completed {}; took {}; fraction cpu {}",
                    new Object[] {context, (i+1), Time.makeTimeStringRounded(elapsedTime), fractionCpu});
        }
    }

    private int execScript(List<String> cmds) {
        return machine.execScript("mysummary", cmds);
    }
}
