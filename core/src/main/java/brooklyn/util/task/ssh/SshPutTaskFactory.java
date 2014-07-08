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

import java.io.InputStream;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskFactory;
import brooklyn.util.stream.KnownSizeInputStream;
import brooklyn.util.stream.ReaderInputStream;

import com.google.common.base.Suppliers;

// cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
public class SshPutTaskFactory extends SshPutTaskStub implements TaskFactory<SshPutTaskWrapper> {
    
    private static final Logger log = LoggerFactory.getLogger(SshPutTaskFactory.class);
    
    private boolean dirty = false;

    /** constructor where machine will be added later */
    public SshPutTaskFactory(String remoteFile) {
        remoteFile(remoteFile);
    }

    /** convenience constructor to supply machine immediately */
    public SshPutTaskFactory(SshMachineLocation machine, String remoteFile) {
        machine(machine);
        remoteFile(remoteFile);
    }

    protected SshPutTaskFactory self() { return this; }

    protected void markDirty() {
        dirty = true;
    }
    
    public SshPutTaskFactory machine(SshMachineLocation machine) {
        markDirty();
        this.machine = machine;
        return self();
    }
        
    public SshPutTaskFactory remoteFile(String remoteFile) {
        this.remoteFile = remoteFile;
        return self();
    }

    public SshPutTaskFactory summary(String summary) {
        markDirty();
        this.summary = summary;
        return self();
    }

    public SshPutTaskFactory contents(String contents) {
        markDirty();
        this.contents = Suppliers.ofInstance(KnownSizeInputStream.of(contents));  
        return self();
    }

    public SshPutTaskFactory contents(byte[] contents) {
        markDirty();
        this.contents = Suppliers.ofInstance(KnownSizeInputStream.of(contents));  
        return self();
    }

    public SshPutTaskFactory contents(InputStream stream) {
        markDirty();
        this.contents = Suppliers.ofInstance(stream);  
        return self();
    }

    public SshPutTaskFactory contents(Reader reader) {
        markDirty();
        this.contents = Suppliers.ofInstance(new ReaderInputStream(reader));  
        return self();
    }

    public SshPutTaskFactory allowFailure() {
        markDirty();
        allowFailure = true;
        return self();
    }
    
    public SshPutTaskFactory createDirectory() {
        markDirty();
        createDirectory = true;
        return self();
    }
    
    public SshPutTaskWrapper newTask() {
        dirty = false;
        return new SshPutTaskWrapper(this);
    }

    @Override
    protected void finalize() throws Throwable {
        // help let people know of API usage error
        if (dirty)
            log.warn("Task "+this+" was modified but modification was never used");
        super.finalize();
    }
}