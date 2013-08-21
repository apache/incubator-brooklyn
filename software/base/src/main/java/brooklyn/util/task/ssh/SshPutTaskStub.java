package brooklyn.util.task.ssh;

import java.io.InputStream;

import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Supplier;

public class SshPutTaskStub {

    protected String remoteFile;
    protected SshMachineLocation machine;
    protected Supplier<? extends InputStream> contents;
    protected String summary;
    protected String permissions;
    protected boolean allowFailure = false;
    protected boolean createDirectory = false;

    protected SshPutTaskStub() {
    }
    
    protected SshPutTaskStub(SshPutTaskStub constructor) {
        this.remoteFile = constructor.remoteFile;
        this.machine = constructor.machine;
        this.contents = constructor.contents;
        this.summary = constructor.summary;
        this.allowFailure = constructor.allowFailure;
        this.createDirectory = constructor.createDirectory;
        this.permissions = constructor.permissions;
    }

    public String getRemoteFile() {
        return remoteFile;
    }
    
    public String getSummary() {
        if (summary!=null) return summary;
        return "scp put: "+remoteFile;
    }

    public SshMachineLocation getMachine() {
        return machine;
    }
}
