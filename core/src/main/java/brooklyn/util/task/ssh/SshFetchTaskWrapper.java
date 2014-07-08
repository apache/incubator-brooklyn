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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.management.TaskWrapper;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.os.Os;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * As {@link ProcessTaskWrapper}, but putting a file on the remote machine
 * 
 * @since 0.6.0
 */
@Beta
public class SshFetchTaskWrapper implements TaskWrapper<String> {

    private final Task<String> task;

    private final String remoteFile;
    private final SshMachineLocation machine;
    private File backingFile;
    private final ConfigBag config;
    
    
    // package private as only AbstractSshTaskFactory should invoke
    SshFetchTaskWrapper(SshFetchTaskFactory factory) {
        this.remoteFile = Preconditions.checkNotNull(factory.remoteFile, "remoteFile");
        this.machine = Preconditions.checkNotNull(factory.machine, "machine");
        TaskBuilder<String> tb = TaskBuilder.<String>builder().dynamic(false).name("ssh fetch "+factory.remoteFile);
        task = tb.body(new SshFetchJob()).build();
        config = factory.getConfig();
    }
    
    @Override
    public Task<String> asTask() {
        return getTask();
    }
    
    @Override
    public Task<String> getTask() {
        return task;
    }
    
    public String getRemoteFile() {
        return remoteFile;
    }
    
    public SshMachineLocation getMachine() {
        return machine;
    }
        
    private class SshFetchJob implements Callable<String> {
        @Override
        public String call() throws Exception {
            int result = -1;
            try {
                Preconditions.checkNotNull(getMachine(), "machine");
                backingFile = Os.newTempFile("brooklyn-ssh-fetch-", FilenameUtils.getName(remoteFile));
                backingFile.deleteOnExit();
                
                result = getMachine().copyFrom(config.getAllConfig(), remoteFile, backingFile.getPath());
            } catch (Exception e) {
                throw new IllegalStateException("SSH fetch "+getRemoteFile()+" from "+getMachine()+" returned threw exception, in "+Tasks.current()+": "+e, e);
            }
            if (result!=0) {
                throw new IllegalStateException("SSH fetch "+getRemoteFile()+" from "+getMachine()+" returned non-zero exit code  "+result+", in "+Tasks.current());
            }
            return FileUtils.readFileToString(backingFile);
        }
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+task+"]";
    }

    /** blocks, returns the fetched file as a string, throwing if there was an exception */
    public String get() {
        return getTask().getUnchecked();
    }
    
    /** blocks, returns the fetched file as bytes, throwing if there was an exception */
    public byte[] getBytes() {
        block();
        try {
            return FileUtils.readFileToByteArray(backingFile);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    /** blocks until the task completes; does not throw */
    public SshFetchTaskWrapper block() {
        getTask().blockUntilEnded();
        return this;
    }
 
    /** true iff the ssh job has completed (with or without failure) */
    public boolean isDone() {
        return getTask().isDone();
    }   

}