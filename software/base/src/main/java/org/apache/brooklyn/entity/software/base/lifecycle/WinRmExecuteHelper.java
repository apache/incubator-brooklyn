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

import static java.lang.String.format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskQueueingContext;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * <code>org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper</code> analog for WinRM
 */
public class WinRmExecuteHelper {
    public static final Logger LOG = LoggerFactory.getLogger(WinRmExecuteHelper.class);

    private Task<Integer> task;

    protected final NativeWindowsScriptRunner runner;
    public final String summary;

    private String command;
    private String psCommand;

    @SuppressWarnings("rawtypes")
    protected final Map flags = new LinkedHashMap();
    protected Predicate<? super Integer> resultCodeCheck = Predicates.alwaysTrue();
    protected ByteArrayOutputStream stdout, stderr, stdin;

    public WinRmExecuteHelper(NativeWindowsScriptRunner runner, String summary) {
        this.runner = runner;
        this.summary = summary;
    }

    public WinRmExecuteHelper setCommand(String command) {
        this.command = command;
        return this;
    }

    public WinRmExecuteHelper setPsCommand(String psCommand) {
        this.psCommand = psCommand;
        return this;
    }

    /** queues the task for execution if we are in a {@link TaskQueueingContext} (e.g. EffectorTaskFactory);
     * or if we aren't in a queueing context, it will submit the task (assuming there is an {@link ExecutionContext}
     * _and_ block until completion, throwing on error */
    @Beta
    public Task<Integer> queue() {
        return DynamicTasks.queueIfPossible(newTask()).orSubmitAndBlock().getTask();
    }

    /** creates a task which will execute this script; note this can only be run once per instance of this class */
    public synchronized Task<Integer> newTask() {
        if (task!=null) throw new IllegalStateException("task can only be generated once");
        TaskBuilder<Integer> tb = Tasks.<Integer>builder().displayName("winrm: "+summary).body(
                new Callable<Integer>() {
                    public Integer call() throws Exception {
                        return executeInternal();
                    }
                });

        try {
            ByteArrayOutputStream stdin = new ByteArrayOutputStream();
            if (command != null) {
                stdin.write(command.getBytes());
            } else if (psCommand != null) {
                stdin.write(psCommand.getBytes());
            }
            tb.tag(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDIN, stdin));
        } catch (IOException e) {
            LOG.warn("Error registering stream "+BrooklynTaskTags.STREAM_STDIN+" on "+tb+": "+e, e);
        }

        Map flags = getFlags();

        Map<?,?> env = (Map<?,?>) flags.get("env");
        if (env!=null) {
            // if not explicitly set, env will come from getShellEnv in AbstractSoftwareProcessSshDriver.execute,
            // which will also update this tag appropriately
            tb.tag(BrooklynTaskTags.tagForEnvStream(BrooklynTaskTags.STREAM_ENV, env));
        }

        if (gatherOutput) {
            stdout = new ByteArrayOutputStream();
            tb.tag(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDOUT, stdout));
            stderr = new ByteArrayOutputStream();
            tb.tag(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDERR, stderr));
        }
        task = tb.build();
        return task;
    }

    public int execute() {
        if (DynamicTasks.getTaskQueuingContext()!=null) {
            return queue().getUnchecked();
        } else {
            return executeInternal();
        }
    }

    public int executeInternal() {
        int result;
        if (gatherOutput) {
            if (stdout==null) stdout = new ByteArrayOutputStream();
            if (stderr==null) stderr = new ByteArrayOutputStream();
            flags.put("out", stdout);
            flags.put("err", stderr);
        }
        result = runner.executeNativeOrPsCommand(flags, command, psCommand, summary, false);
        if (!resultCodeCheck.apply(result)) {
            throw logWithDetailsAndThrow(format("Execution failed, invalid result %s for %s", result, summary), null);
        }
        return result;
    }

    public WinRmExecuteHelper failOnNonZeroResultCode() {
        return updateTaskAndFailOnNonZeroResultCode();
    }

    public WinRmExecuteHelper updateTaskAndFailOnNonZeroResultCode() {
        gatherOutput();
        // a failure listener would be a cleaner way

        resultCodeCheck = new Predicate<Integer>() {
            @Override
            public boolean apply(@Nullable Integer input) {
                if (input==0) return true;

                try {
                    String notes = "";
                    if (!getResultStderr().isEmpty())
                        notes += "STDERR\n" + getResultStderr()+"\n";
                    if (!getResultStdout().isEmpty())
                        notes += "\n" + "STDOUT\n" + getResultStdout()+"\n";
                    Tasks.setExtraStatusDetails(notes.trim());
                } catch (Exception e) {
                    LOG.warn("Unable to collect additional metadata on failure of "+summary+": "+e);
                }

                return false;
            }
        };

        return this;
    }

    protected boolean gatherOutput = false;

    public WinRmExecuteHelper gatherOutput() {
        return gatherOutput(true);
    }
    public WinRmExecuteHelper gatherOutput(boolean gather) {
        gatherOutput = gather;
        return this;
    }

    protected RuntimeException logWithDetailsAndThrow(String message, Throwable optionalCause) {
        LOG.warn(message + " (throwing)");
        int maxLength = 1024;
        LOG.warn(message + " (throwing)");
        Streams.logStreamTail(LOG, "STDERR of problem in "+Tasks.current(), stderr, maxLength);
        Streams.logStreamTail(LOG, "STDOUT of problem in "+Tasks.current(), stdout, maxLength);
        Streams.logStreamTail(LOG, "STDIN of problem in "+Tasks.current(), Streams.byteArrayOfString(command != null ? command : psCommand), 4096);
        if (optionalCause!=null) throw new IllegalStateException(message, optionalCause);
        throw new IllegalStateException(message);
    }

    @SuppressWarnings("rawtypes")
    public Map getFlags() {
        return flags;
    }

    public String getResultStdout() {
        if (stdout==null) throw new IllegalStateException("output not available on "+this+"; ensure gatherOutput(true) is set");
        return stdout.toString();
    }
    public String getResultStderr() {
        if (stderr==null) throw new IllegalStateException("output not available on "+this+"; ensure gatherOutput(true) is set");
        return stderr.toString();
    }
}
