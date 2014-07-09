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
package brooklyn.util.task.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskFactory;
import brooklyn.util.config.ConfigBag;

// cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
public class SshFetchTaskFactory implements TaskFactory<SshFetchTaskWrapper> {
    
    private static final Logger log = LoggerFactory.getLogger(SshFetchTaskFactory.class);
    
    private boolean dirty = false;
    
    protected SshMachineLocation machine;
    protected String remoteFile;
    protected final ConfigBag config = ConfigBag.newInstance();

    /** constructor where machine will be added later */
    public SshFetchTaskFactory(String remoteFile) {
        remoteFile(remoteFile);
    }

    /** convenience constructor to supply machine immediately */
    public SshFetchTaskFactory(SshMachineLocation machine, String remoteFile) {
        machine(machine);
        remoteFile(remoteFile);
    }

    protected SshFetchTaskFactory self() { return this; }

    protected void markDirty() {
        dirty = true;
    }
    
    public SshFetchTaskFactory machine(SshMachineLocation machine) {
        markDirty();
        this.machine = machine;
        return self();
    }
        
    public SshMachineLocation getMachine() {
        return machine;
    }
    
    public SshFetchTaskFactory remoteFile(String remoteFile) {
        this.remoteFile = remoteFile;
        return self();
    }

    public ConfigBag getConfig() {
        return config;
    }
    
    @Override
    public SshFetchTaskWrapper newTask() {
        dirty = false;
        return new SshFetchTaskWrapper(this);
    }

    @Override
    protected void finalize() throws Throwable {
        // help let people know of API usage error
        if (dirty)
            log.warn("Task "+this+" was modified but modification was never used");
        super.finalize();
    }

}