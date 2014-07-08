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
package brooklyn.util.task.ssh.internal;

import java.util.List;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.base.Function;

/** the "Plain" class exists purely so we can massage return types for callers' convenience */
public class PlainSshExecTaskFactory<RET> extends AbstractSshExecTaskFactory<PlainSshExecTaskFactory<RET>,RET> {
    /** constructor where machine will be added later */
    public PlainSshExecTaskFactory(String ...commands) {
        super(commands);
    }

    /** convenience constructor to supply machine immediately */
    public PlainSshExecTaskFactory(SshMachineLocation machine, String ...commands) {
        this(commands);
        machine(machine);
    }

    /** Constructor where machine will be added later */
    public PlainSshExecTaskFactory(List<String> commands) {
        this(commands.toArray(new String[commands.size()]));
    }

    /** Convenience constructor to supply machine immediately */
    public PlainSshExecTaskFactory(SshMachineLocation machine, List<String> commands) {
        this(machine, commands.toArray(new String[commands.size()]));
    }

    @Override
    public <T2> PlainSshExecTaskFactory<T2> returning(ScriptReturnType type) {
        return (PlainSshExecTaskFactory<T2>) super.<T2>returning(type);
    }

    @Override
    public <RET2> PlainSshExecTaskFactory<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
        return (PlainSshExecTaskFactory<RET2>) super.returning(resultTransformation);
    }
    
    @Override
    public PlainSshExecTaskFactory<Boolean> returningIsExitCodeZero() {
        return (PlainSshExecTaskFactory<Boolean>) super.returningIsExitCodeZero();
    }
    
    @Override
    public PlainSshExecTaskFactory<String> requiringZeroAndReturningStdout() {
        return (PlainSshExecTaskFactory<String>) super.requiringZeroAndReturningStdout();
    }
    
}