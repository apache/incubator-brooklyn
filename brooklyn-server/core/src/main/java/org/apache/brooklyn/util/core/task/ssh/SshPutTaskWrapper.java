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
package org.apache.brooklyn.util.core.task.ssh;

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskWrapper;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/** As {@link ProcessTaskWrapper}, but putting a file on the remote machine */
@Beta
public class SshPutTaskWrapper extends SshPutTaskStub implements TaskWrapper<Void> {

    private static final Logger log = LoggerFactory.getLogger(SshPutTaskWrapper.class);
    
    private final Task<Void> task;

    protected Integer exitCodeOfCopy = null;
    protected Exception exception = null;
    protected boolean successful = false;
    
    // package private as only AbstractSshTaskFactory should invoke
    SshPutTaskWrapper(SshPutTaskFactory constructor) {
        super(constructor);
        TaskBuilder<Void> tb = TaskBuilder.<Void>builder().dynamic(false).displayName(getSummary());
        task = tb.body(new SshPutJob()).build();
    }
    
    @Override
    public Task<Void> asTask() {
        return getTask();
    }
    
    @Override
    public Task<Void> getTask() {
        return task;
    }
        
    // TODO:
    //   verify
    //   copyAsRoot
    //   owner
    //   lastModificationDate - see {@link #PROP_LAST_MODIFICATION_DATE}; not supported by all SshTool implementations
    //   lastAccessDate - see {@link #PROP_LAST_ACCESS_DATE}; not supported by all SshTool implementations

    private class SshPutJob implements Callable<Void> {
        @Override
        public Void call() throws Exception {
            try {
                Preconditions.checkNotNull(getMachine(), "machine");
                
                String remoteFile = getRemoteFile();

                if (createDirectory) {
                    String remoteDir = remoteFile;
                    int exitCodeOfCreate = -1;
                    try {
                        int li = remoteDir.lastIndexOf("/");
                        if (li>=0) {
                            remoteDir = remoteDir.substring(0, li+1);
                            exitCodeOfCreate = getMachine().execCommands("creating directory for "+getSummary(), 
                                    Arrays.asList("mkdir -p "+remoteDir));
                        } else {
                            // nothing to create
                            exitCodeOfCreate = 0;
                        }
                    } catch (Exception e) {
                        if (log.isDebugEnabled())
                            log.debug("SSH put "+getRemoteFile()+" (create dir, in task "+getSummary()+") to "+getMachine()+" threw exception: "+e);
                        exception = e;
                    }
                    if (exception!=null || !((Integer)0).equals(exitCodeOfCreate)) {
                        if (!allowFailure) {
                            if (exception != null) {
                                throw new IllegalStateException(getSummary()+" (creating dir "+remoteDir+" for SSH put task) ended with exception, in "+Tasks.current()+": "+exception, exception);
                            }
                            if (exitCodeOfCreate!=0) {
                                exception = new IllegalStateException(getSummary()+" (creating dir "+remoteDir+" SSH put task) ended with exit code "+exitCodeOfCreate+", in "+Tasks.current());
                                throw exception;
                            }
                        }
                        // not successful, but allowed
                        return null;
                    }
                }
                
                ConfigBag config = ConfigBag.newInstanceCopying(getConfig());
                if (permissions!=null) config.put(SshTool.PROP_PERMISSIONS, permissions);
                
                exitCodeOfCopy = getMachine().copyTo(config.getAllConfig(), contents.get(), remoteFile);

                if (log.isDebugEnabled())
                    log.debug("SSH put "+getRemoteFile()+" (task "+getSummary()+") to "+getMachine()+" completed with exit code "+exitCodeOfCopy);
            } catch (Exception e) {
                if (log.isDebugEnabled())
                    log.debug("SSH put "+getRemoteFile()+" (task "+getSummary()+") to "+getMachine()+" threw exception: "+e);
                exception = e;
            }
            
            if (exception!=null || !((Integer)0).equals(exitCodeOfCopy)) {
                if (!allowFailure) {
                    if (exception != null) {
                        throw new IllegalStateException(getSummary()+" (SSH put task) ended with exception, in "+Tasks.current()+": "+exception, exception);
                    }
                    if (exitCodeOfCopy!=0) {
                        exception = new IllegalStateException(getSummary()+" (SSH put task) ended with exit code "+exitCodeOfCopy+", in "+Tasks.current());
                        throw exception;
                    }
                }
                // not successful, but allowed
                return null;
            }
            
            // TODO verify

            successful = (exception==null && ((Integer)0).equals(exitCodeOfCopy));
            return null;
        }
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+task+"]";
    }

    /** blocks, throwing if there was an exception */
    public Void get() {
        return getTask().getUnchecked();
    }
    
    /** returns the exit code from the copy, 0 on success; 
     * null if it has not completed or threw exception
     * (not sure if this is ever a non-zero integer or if it is meaningful)
     * <p>
     * most callers will want the simpler {@link #isSuccessful()} */
    public Integer getExitCode() {
        return exitCodeOfCopy;
    }
    
    /** returns any exception encountered in the operation */
    public Exception getException() {
        return exception;
    }
    
    /** blocks until the task completes; does not throw */
    public SshPutTaskWrapper block() {
        getTask().blockUntilEnded();
        return this;
    }
 
    /** true iff the ssh job has completed (with or without failure) */
    public boolean isDone() {
        return getTask().isDone();
    }

    /** true iff the scp has completed successfully; guaranteed to be set before {@link #isDone()} or {@link #block()} are satisfied */
    public boolean isSuccessful() {
        return successful;
    }
    

}