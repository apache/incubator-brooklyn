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
package brooklyn.util.task.system.internal;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskStub;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

public abstract class AbstractProcessTaskFactory<T extends AbstractProcessTaskFactory<T,RET>,RET> extends ProcessTaskStub implements ProcessTaskFactory<RET> {
    
    private static final Logger log = LoggerFactory.getLogger(AbstractProcessTaskFactory.class);
    
    protected boolean dirty = false;
    
    public AbstractProcessTaskFactory(String ...commands) {
        this.commands.addAll(Arrays.asList(commands));
    }

    @SuppressWarnings("unchecked")
    protected T self() { return (T)this; }
    
    protected void markDirty() {
        dirty = true;
    }
    
    @Override
    public T add(String ...commandsToAdd) {
        markDirty();
        for (String commandToAdd: commandsToAdd) this.commands.add(commandToAdd);
        return self();
    }

    @Override
    public T add(Iterable<String> commandsToAdd) {
        Iterables.addAll(this.commands, commandsToAdd);
        return self();
    }
    
    @Override
    public T machine(SshMachineLocation machine) {
        markDirty();
        this.machine = machine;
        return self();
    }

    @Override
    public T requiringExitCodeZero() {
        markDirty();
        requireExitCodeZero = true;
        return self();
    }
    
    @Override
    public T requiringExitCodeZero(String extraErrorMessage) {
        markDirty();
        requireExitCodeZero = true;
        this.extraErrorMessage = extraErrorMessage;
        return self();
    }
    
    @Override
    public T allowingNonZeroExitCode() {
        markDirty();
        requireExitCodeZero = false;
        return self();
    }

    @Override
    public ProcessTaskFactory<Boolean> returningIsExitCodeZero() {
        if (requireExitCodeZero==null) allowingNonZeroExitCode();
        return returning(new Function<ProcessTaskWrapper<?>,Boolean>() {
            public Boolean apply(ProcessTaskWrapper<?> input) {
                return input.getExitCode()==0;
            }
        });
    }

    @Override
    public ProcessTaskFactory<String> requiringZeroAndReturningStdout() {
        requiringExitCodeZero();
        return this.<String>returning(ScriptReturnType.STDOUT_STRING);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <RET2> ProcessTaskFactory<RET2> returning(ScriptReturnType type) {
        markDirty();
        returnType = Preconditions.checkNotNull(type);
        return (ProcessTaskFactory<RET2>) self();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <RET2> ProcessTaskFactory<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
        markDirty();
        returnType = ScriptReturnType.CUSTOM;
        this.returnResultTransformation = resultTransformation;
        return (ProcessTaskFactory<RET2>) self();
    }
    
    @Override
    public T runAsCommand() {
        markDirty();
        runAsScript = false;
        return self();
    }

    @Override
    public T runAsScript() {
        markDirty();
        runAsScript = true;
        return self();
    }

    @Override
    public T runAsRoot() {
        markDirty();
        runAsRoot = true;
        return self();
    }
    
    @Override
    public T environmentVariable(String key, String val) {
        markDirty();
        shellEnvironment.put(key, val);
        return self();
    }

    @Override
    public T environmentVariables(Map<String,String> vars) {
        if (vars!=null) {
            markDirty();
            shellEnvironment.putAll(vars);
        }
        return self();
    }

    /** creates the TaskBuilder which can be further customized; typically invoked by the initial {@link #newTask()} */
    public TaskBuilder<Object> constructCustomizedTaskBuilder() {
        TaskBuilder<Object> tb = TaskBuilder.builder().dynamic(false).name("ssh: "+getSummary());
        
        tb.tag(BrooklynTaskTags.tagForStream(BrooklynTaskTags.STREAM_STDIN, 
                Streams.byteArrayOfString(Strings.join(commands, "\n"))));
        tb.tag(BrooklynTaskTags.tagForEnvStream(BrooklynTaskTags.STREAM_ENV, shellEnvironment));
        
        return tb;
    }
    
    @Override
    public T summary(String summary) {
        markDirty();
        this.summary = summary;
        return self();
    }

    @Override
    public <V> T configure(ConfigKey<V> key, V value) {
        config.configure(key, value);
        return self();
    }
    
    @Override
    public T configure(Map<?, ?> flags) {
        if (flags!=null)
            config.putAll(flags);
        return self();
    }
 
    @Override
    public T addCompletionListener(Function<ProcessTaskWrapper<?>, Void> listener) {
        completionListeners.add(listener);
        return self();
    }

    @Override
    protected void finalize() throws Throwable {
        // help let people know of API usage error
        if (dirty)
            log.warn("Task "+this+" was modified but modification was never used");
        super.finalize();
    }
}
