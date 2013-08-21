package brooklyn.util.task.ssh;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.management.TaskWrapper;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * As {@link SshTaskWrapper}, but putting a file on the remote machine
 * 
 * @since 0.6.0
 */
@Beta
public class SshFetchTaskWrapper implements TaskWrapper<String> {

    private final Task<String> task;

    private final String remoteFile;
    private final SshMachineLocation machine;
    private File backingFile;
    
    // package private as only AbstractSshTaskFactory should invoke
    SshFetchTaskWrapper(SshFetchTaskFactory factory) {
        this.remoteFile = Preconditions.checkNotNull(factory.remoteFile, "remoteFile");
        this.machine = Preconditions.checkNotNull(factory.machine, "machine");
        TaskBuilder<String> tb = TaskBuilder.<String>builder().dynamic(false).name("ssh fetch "+factory.remoteFile);
        task = tb.body(new SshFetchJob()).build();
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
                backingFile = File.createTempFile("brooklyn-ssh-fetch-", FilenameUtils.getName(remoteFile));
                backingFile.deleteOnExit();
                
                result = getMachine().copyFrom(remoteFile, backingFile.getPath());
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