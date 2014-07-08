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
package brooklyn.util.task.system;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskFactory;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.task.system.ProcessTaskStub.ScriptReturnType;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;

public interface ProcessTaskFactory<T> extends TaskFactory<ProcessTaskWrapper<T>> {
    public ProcessTaskFactory<T> machine(SshMachineLocation machine);
    public ProcessTaskFactory<T> add(String ...commandsToAdd);
    public ProcessTaskFactory<T> add(Iterable<String> commandsToAdd);
    public ProcessTaskFactory<T> requiringExitCodeZero();
    public ProcessTaskFactory<T> requiringExitCodeZero(String extraErrorMessage);
    public ProcessTaskFactory<T> allowingNonZeroExitCode();
    public ProcessTaskFactory<String> requiringZeroAndReturningStdout();
    public ProcessTaskFactory<Boolean> returningIsExitCodeZero();
    public <RET2> ProcessTaskFactory<RET2> returning(ScriptReturnType type);
    public <RET2> ProcessTaskFactory<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation);
    public ProcessTaskFactory<T> runAsCommand();
    public ProcessTaskFactory<T> runAsScript();
    public ProcessTaskFactory<T> runAsRoot();
    public ProcessTaskFactory<T> environmentVariable(String key, String val);
    public ProcessTaskFactory<T> environmentVariables(Map<String,String> vars);
    public ProcessTaskFactory<T> summary(String summary);
    
    /** allows setting config-key based properties for specific underlying tools */
    @Beta
    public <V> ProcessTaskFactory<T> configure(ConfigKey<V> key, V value);

    /** allows setting config-key/flag based properties for specific underlying tools;
     * but note that if any are prefixed with {@link SshTool#BROOKLYN_CONFIG_KEY_PREFIX}
     * these should normally be filtered out */
    @Beta
    public ProcessTaskFactory<T> configure(Map<?,?> flags);

    /** adds a listener which will be notified of (otherwise) successful completion,
     * typically used to invalidate the result (ie throw exception, to promote a string in the output to an exception);
     * invoked even if return code is zero, so a better error can be thrown */
    public ProcessTaskFactory<T> addCompletionListener(Function<ProcessTaskWrapper<?>, Void> function);
}
