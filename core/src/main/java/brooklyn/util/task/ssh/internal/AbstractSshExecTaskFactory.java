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

import com.google.common.base.Preconditions;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.task.system.internal.AbstractProcessTaskFactory;

// cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
public abstract class AbstractSshExecTaskFactory<T extends AbstractProcessTaskFactory<T,RET>,RET> extends AbstractProcessTaskFactory<T,RET> implements ProcessTaskFactory<RET> {
    
    /** constructor where machine will be added later */
    public AbstractSshExecTaskFactory(String ...commands) {
        super(commands);
    }

    /** convenience constructor to supply machine immediately */
    public AbstractSshExecTaskFactory(SshMachineLocation machine, String ...commands) {
        this(commands);
        machine(machine);
    }
    
    @Override
    public ProcessTaskWrapper<RET> newTask() {
        dirty = false;
        return new ProcessTaskWrapper<RET>(this) {
            protected void run(ConfigBag config) {
                Preconditions.checkNotNull(getMachine(), "machine");
                if (Boolean.FALSE.equals(this.runAsScript)) {
                    this.exitCode = getMachine().execCommands(config.getAllConfig(), getSummary(), commands, shellEnvironment);
                } else { // runScript = null or TRUE
                    this.exitCode = getMachine().execScript(config.getAllConfig(), getSummary(), commands, shellEnvironment);
                }
            }
            protected String taskTypeShortName() { return "SSH"; }
        };
    }
}