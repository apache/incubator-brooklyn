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
package org.apache.brooklyn.util.core.task.system;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskWrapper;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.internal.ssh.ShellTool;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.system.internal.AbstractProcessTaskFactory;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

/** Wraps a fully constructed process task, and allows callers to inspect status. 
 * Note that methods in here such as {@link #getStdout()} will return partially completed streams while the task is ongoing
 * (and exit code will be null). You can {@link #block()} or {@link #get()} as conveniences on the underlying {@link #getTask()}. */ 
public abstract class ProcessTaskWrapper<RET> extends ProcessTaskStub implements TaskWrapper<RET> {

    private static final Logger log = LoggerFactory.getLogger(ProcessTaskWrapper.class);
    
    private final Task<RET> task;

    // execution details
    protected ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    protected ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    protected Integer exitCode = null;
    
    @SuppressWarnings("unchecked")
    protected ProcessTaskWrapper(AbstractProcessTaskFactory<?,RET> constructor) {
        super(constructor);
        TaskBuilder<Object> tb = constructor.constructCustomizedTaskBuilder();
        if (stdout!=null) tb.tag(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDOUT, stdout));
        if (stderr!=null) tb.tag(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDERR, stderr));
        task = (Task<RET>) tb.body(new ProcessTaskInternalJob()).build();
    }
    
    @Override
    public Task<RET> asTask() {
        return getTask();
    }
    
    @Override
    public Task<RET> getTask() {
        return task;
    }
    
    public Integer getExitCode() {
        return exitCode;
    }
    
    public byte[] getStdoutBytes() {
        if (stdout==null) return null;
        return stdout.toByteArray();
    }
    
    public byte[] getStderrBytes() {
        if (stderr==null) return null;
        return stderr.toByteArray();
    }
    
    public String getStdout() {
        if (stdout==null) return null;
        return stdout.toString();
    }
    
    public String getStderr() {
        if (stderr==null) return null;
        return stderr.toString();
    }

    protected class ProcessTaskInternalJob implements Callable<Object> {
        @Override
        public Object call() throws Exception {
            run( getConfigForRunning() );
            
            for (Function<ProcessTaskWrapper<?>, Void> listener: completionListeners) {
                try {
                    listener.apply(ProcessTaskWrapper.this);
                } catch (Exception e) {
                    logWithDetailsAndThrow("Error in "+taskTypeShortName()+" task "+getSummary()+": "+e, e);                    
                }
            }
            
            if (exitCode!=0 && !Boolean.FALSE.equals(requireExitCodeZero)) {
                if (Boolean.TRUE.equals(requireExitCodeZero)) {
                    logWithDetailsAndThrow(taskTypeShortName()+" task ended with exit code "+exitCode+" when 0 was required, in "+Tasks.current()+": "+getSummary(), null);
                } else {
                    // warn, but allow, on non-zero not explicitly allowed
                    log.warn(taskTypeShortName()+" task ended with exit code "+exitCode+" when non-zero was not explicitly allowed (error may be thrown in future), in "
                            +Tasks.current()+": "+getSummary());
                }
            }
            switch (returnType) {
            case CUSTOM: return returnResultTransformation.apply(ProcessTaskWrapper.this);
            case STDOUT_STRING: return stdout.toString();
            case STDOUT_BYTES: return stdout.toByteArray();
            case STDERR_STRING: return stderr.toString();
            case STDERR_BYTES: return stderr.toByteArray();
            case EXIT_CODE: return exitCode;
            }

            throw new IllegalStateException("Unknown return type for "+taskTypeShortName()+" job "+getSummary()+": "+returnType);
        }

        protected void logWithDetailsAndThrow(String message, Throwable optionalCause) {
            message = (extraErrorMessage!=null ? extraErrorMessage+": " : "") + message;
            log.warn(message+" (throwing)");
            logProblemDetails("STDERR", stderr, 1024);
            logProblemDetails("STDOUT", stdout, 1024);
            logProblemDetails("STDIN", Streams.byteArrayOfString(Strings.join(commands,"\n")), 4096);
            if (optionalCause!=null) throw new IllegalStateException(message, optionalCause);
            throw new IllegalStateException(message);
        }
        
        protected void logProblemDetails(String streamName, ByteArrayOutputStream stream, int max) {
            Streams.logStreamTail(log, streamName+" for problem in "+Tasks.current(), stream, max);
        }

    }
    
    @Override
    public String toString() {
        return super.toString()+"["+task+"]";
    }

    /** blocks and gets the result, throwing if there was an exception */
    public RET get() {
        return getTask().getUnchecked();
    }
    
    /** blocks until the task completes; does not throw */
    public ProcessTaskWrapper<RET> block() {
        getTask().blockUntilEnded();
        return this;
    }
 
    /** true iff the process has completed (with or without failure) */
    public boolean isDone() {
        return getTask().isDone();
    }

    /** for overriding */
    protected ConfigBag getConfigForRunning() {
        ConfigBag config = ConfigBag.newInstanceCopying(ProcessTaskWrapper.this.config);
        if (stdout!=null) config.put(ShellTool.PROP_OUT_STREAM, stdout);
        if (stderr!=null) config.put(ShellTool.PROP_ERR_STREAM, stderr);
        
        if (!config.containsKey(ShellTool.PROP_NO_EXTRA_OUTPUT))
            // by default no extra output (so things like cat, etc work as expected)
            config.put(ShellTool.PROP_NO_EXTRA_OUTPUT, true);

        if (runAsRoot)
            config.put(ShellTool.PROP_RUN_AS_ROOT, true);
        return config;
    }

    protected abstract void run(ConfigBag config);
    
    protected abstract String taskTypeShortName();
    
}