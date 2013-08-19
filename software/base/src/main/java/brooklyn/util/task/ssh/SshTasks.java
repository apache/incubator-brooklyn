package brooklyn.util.task.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;

public class SshTasks {

    private static final Logger log = LoggerFactory.getLogger(SshTasks.class);
        
    public static SshTaskFactory<Integer> newTaskFactory(SshMachineLocation machine, String ...commands) {
        return new PlainSshTaskFactory<Integer>(machine, commands);
    }

    public static SshTaskFactory<Boolean> dontRequireTtyForSudo(SshMachineLocation machine, final boolean requireSuccess) {
        return newTaskFactory(machine, BashCommands.dontRequireTtyForSudo())
            .summary("setting up sudo")
            .configure(SshTool.PROP_ALLOCATE_PTY, true)
            .returning(new Function<SshTaskWrapper<?>,Boolean>() { public Boolean apply(SshTaskWrapper<?> task) {
                if (task.getExitCode()==0) return true;
                log.warn("Error setting up sudo for "+task.getMachine().getUser()+"@"+task.getMachine().getAddress().getHostName()+": "+
                        Strings.trim(Strings.isNonBlank(task.getStderr()) ? task.getStderr() : task.getStdout()) + 
                        " (exit code "+task.getExitCode()+")");
                if (requireSuccess) {
                    throw new IllegalStateException("Passwordless sudo is required for "+task.getMachine().getUser()+"@"+task.getMachine().getAddress().getHostName());
                }
                return true; 
            } });
    }

}
