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
package org.apache.brooklyn.entity.software.base.lifecycle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestApplicationImpl;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.internal.ssh.cli.SshCliTool;
import org.apache.brooklyn.util.core.internal.ssh.sshj.SshjTool;
import org.apache.brooklyn.util.stream.StreamGobbler;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class StartStopSshDriverTest {

    public class BasicStartStopSshDriver extends AbstractSoftwareProcessSshDriver {
        public BasicStartStopSshDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        public boolean isRunning() { return true; }
        public void stop() {}
        public void kill() {}
        public void install() {}
        public void customize() {}
        public void launch() {}
    }

    private static class ThreadIdTransformer implements Function<ThreadInfo, Long> {
        @Override
        public Long apply(ThreadInfo t) {
            return t.getThreadId();
        }
    }

    private TestApplication app;
    private TestEntity entity;
    private SshMachineLocationWithSshTool sshMachineLocation;
    private AbstractSoftwareProcessSshDriver driver;

    @SuppressWarnings("rawtypes")
    protected static class SshMachineLocationWithSshTool extends SshMachineLocation {
        private static final long serialVersionUID = 1L;

        SshTool lastTool;
        public SshMachineLocationWithSshTool(Map flags) { super(flags); }

        @Override
        public SshTool connectSsh(Map args) {
            SshTool result = super.connectSsh(args);
            lastTool = result;
            return result;
        }
    }
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        app = new TestApplicationImpl();
        entity = new TestEntityImpl(app);
        Entities.startManagement(app);
        sshMachineLocation = new SshMachineLocationWithSshTool(ImmutableMap.of("address", "localhost"));
        driver = new BasicStartStopSshDriver(entity, sshMachineLocation);
    }
    
    @Test(groups="Integration")
    public void testExecuteDoesNotLeaveRunningStreamGobblerThread() {
        List<ThreadInfo> existingThreads = getThreadsCalling(StreamGobbler.class);
        final List<Long> existingThreadIds = getThreadId(existingThreads);
        
        List<String> script = Arrays.asList("echo hello");
        driver.execute(script, "mytest");
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", 10*1000), new Runnable() {
            @Override
            public void run() {
              List<ThreadInfo> currentThreads = getThreadsCalling(StreamGobbler.class);
              Set<Long> currentThreadIds = MutableSet.copyOf(getThreadId(currentThreads));

              currentThreadIds.removeAll(existingThreadIds);
              assertEquals(currentThreadIds, ImmutableSet.<Long>of());
            }
        });
    }

    @Test(groups="Integration")
    public void testSshScriptHeaderUsedWhenSpecified() {
        entity.config().set(BrooklynConfigKeys.SSH_CONFIG_SCRIPT_HEADER, "#!/bin/bash -e\necho hello world");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        driver.execute(ImmutableMap.of("out", out), Arrays.asList("echo goodbye"), "test");
        String s = out.toString();
        assertTrue(s.contains("goodbye"), "should have said goodbye: "+s);
        assertTrue(s.contains("hello world"), "should have said hello: "+s);
        assertTrue(sshMachineLocation.lastTool instanceof SshjTool, "expect sshj tool, got "+
            (sshMachineLocation.lastTool!=null ? ""+sshMachineLocation.lastTool.getClass()+":" : "") + sshMachineLocation.lastTool);
    }

    @Test(groups="Integration")
    public void testSshCliPickedUpWhenSpecified() {
        entity.config().set(BrooklynConfigKeys.SSH_TOOL_CLASS, SshCliTool.class.getName());
        driver.execute(Arrays.asList("echo hi"), "test");
        assertTrue(sshMachineLocation.lastTool instanceof SshCliTool, "expect CLI tool, got "+
                        (sshMachineLocation.lastTool!=null ? ""+sshMachineLocation.lastTool.getClass()+":" : "") + sshMachineLocation.lastTool);
    }
    
    private List<ThreadInfo> getThreadsCalling(Class<?> clazz) {
        String clazzName = clazz.getCanonicalName();
        List<ThreadInfo> result = MutableList.of();
        ThreadMXBean threadMxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads = threadMxbean.dumpAllThreads(false, false);
        
        for (ThreadInfo thread : threads) {
            StackTraceElement[] stackTrace = thread.getStackTrace();
            for (StackTraceElement stackTraceElement : stackTrace) {
                if (clazzName == stackTraceElement.getClassName()) {
                    result.add(thread);
                    break;
                }
            }
        }
        return result;
    }

    private ImmutableList<Long> getThreadId(List<ThreadInfo> existingThreads) {
        return FluentIterable.from(existingThreads).transform(new ThreadIdTransformer()).toList();
    }

}
