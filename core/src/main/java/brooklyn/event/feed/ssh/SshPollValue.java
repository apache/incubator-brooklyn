package brooklyn.event.feed.ssh;

import javax.annotation.Nullable;

import brooklyn.location.basic.SshMachineLocation;

public class SshPollValue {

    private final SshMachineLocation machine;
    private final int exitStatus;
    private final String stdout;
    private final String stderr;

    public SshPollValue(SshMachineLocation machine, int exitStatus, String stdout, String stderr) {
        this.machine = machine;
        this.exitStatus = exitStatus;
        this.stdout = stdout;
        this.stderr = stderr;
    }
    
    /** The machine the command will run on. */
    public SshMachineLocation getMachine() {
        return machine;
    }

    /** Command exit status, or -1 if error is set. */
    public int getExitStatus() {
        return exitStatus;
    }

    /** Command standard output; may be null if no content available. */
    @Nullable
    public String getStdout() {
        return stdout;
    }

    /** Command standard error; may be null if no content available. */
    @Nullable
    public String getStderr() {
        return stderr;
    }
}
