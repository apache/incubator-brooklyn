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

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.process.ProcessTool;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.base.Function;

public class SystemProcessTaskFactory<T extends SystemProcessTaskFactory<T,RET>,RET> extends AbstractProcessTaskFactory<T, RET> {

    private static final Logger log = LoggerFactory.getLogger(SystemProcessTaskFactory.class);
    
    // FIXME Plum this through?!
    private File directory;
    private Boolean loginShell;

    public SystemProcessTaskFactory(String ...commands) {
        super(commands);
    }
    
    public T directory(File directory) {
        markDirty();
        this.directory = directory;
        return self();
    }
    
    public T loginShell(boolean loginShell) {
        markDirty();
        this.loginShell = loginShell;
        return self();
    }
    
    @Override
    public T machine(SshMachineLocation machine) {
        log.warn("Not permitted to set machines on "+this+" (ignoring - "+machine+")");
        if (log.isDebugEnabled())
            log.debug("Source of attempt to set machines on "+this+" ("+machine+")",
                    new Throwable("Source of attempt to set machines on "+this+" ("+machine+")"));
        return self();
    }

    @Override
    public ProcessTaskWrapper<RET> newTask() {
        return new SystemProcessTaskWrapper();
    }

    protected class SystemProcessTaskWrapper extends ProcessTaskWrapper<RET> {
        protected final String taskTypeShortName;
        
        public SystemProcessTaskWrapper() {
            this("Process");
        }
        public SystemProcessTaskWrapper(String taskTypeShortName) {
            super(SystemProcessTaskFactory.this);
            this.taskTypeShortName = taskTypeShortName;
        }
        @Override
        protected ConfigBag getConfigForRunning() {
            ConfigBag result = super.getConfigForRunning();
            if (directory != null) config.put(ProcessTool.PROP_DIRECTORY, directory.getAbsolutePath());
            if (loginShell != null) config.put(ProcessTool.PROP_LOGIN_SHELL, loginShell);
            return result;
        }
        @Override
        protected void run(ConfigBag config) {
            if (Boolean.FALSE.equals(this.runAsScript)) {
                this.exitCode = newExecWithLoggingHelpers().execCommands(config.getAllConfig(), getSummary(), getCommands(), getShellEnvironment());
            } else { // runScript = null or TRUE
                this.exitCode = newExecWithLoggingHelpers().execScript(config.getAllConfig(), getSummary(), getCommands(), getShellEnvironment());
            }
        }
        @Override
        protected String taskTypeShortName() { return taskTypeShortName; }
    }
    
    protected ExecWithLoggingHelpers newExecWithLoggingHelpers() {
        return new ExecWithLoggingHelpers("Process") {
            @Override
            protected <U> U execWithTool(MutableMap<String, Object> props, Function<ShellTool, U> task) {
                // properties typically passed to both
                if (log.isDebugEnabled() && props!=null && !props.isEmpty())
                    log.debug("Ignoring flags "+props+" when running "+this);
                return task.apply(new ProcessTool());
            }
            @Override
            protected void preExecChecks() {}
            @Override
            protected String constructDefaultLoggingPrefix(ConfigBag execFlags) {
                return "system.exec";
            }
            @Override
            protected String getTargetName() {
                return "local host";
            }
        }.logger(log);
    }

    /** concrete instance (for generics) */
    public static class ConcreteSystemProcessTaskFactory<RET> extends SystemProcessTaskFactory<ConcreteSystemProcessTaskFactory<RET>, RET> {
        public ConcreteSystemProcessTaskFactory(String ...commands) {
            super(commands);
        }
    }
    
}
