package brooklyn.util.task.ssh;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.ssh.internal.PlainSshExecTaskFactory;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;

/**
 * Conveniences for generating {@link Task} instances to perform SSH activities on an {@link SshMachineLocation}.
 * See also {@link SshEffectorTasks} for inferring the {@link SshMachineLocation} from context.
 *  
 * @since 0.6.0
 */
@Beta
public class SshTasks {

    private static final Logger log = LoggerFactory.getLogger(SshTasks.class);
        
    public static ProcessTaskFactory<Integer> newSshExecTaskFactory(SshMachineLocation machine, String ...commands) {
        return new PlainSshExecTaskFactory<Integer>(machine, commands);
    }

    public static SshPutTaskFactory newSshPutTaskFactory(SshMachineLocation machine, String remoteFile) {
        return new SshPutTaskFactory(machine, remoteFile);
    }

    public static SshFetchTaskFactory newSshFetchTaskFactory(SshMachineLocation machine, String remoteFile) {
        return new SshFetchTaskFactory(machine, remoteFile);
    }

    /** creates a task which returns modifies sudoers to ensure non-tty access is permitted;
     * also gives nice warnings if sudo is not permitted */
    public static ProcessTaskFactory<Boolean> dontRequireTtyForSudo(SshMachineLocation machine, final boolean requireSuccess) {
        return newSshExecTaskFactory(machine, 
                BashCommands.dontRequireTtyForSudo())
            .summary("setting up sudo")
            .configure(SshTool.PROP_ALLOCATE_PTY, true)
            .allowingNonZeroExitCode()
            .returning(new Function<ProcessTaskWrapper<?>,Boolean>() { public Boolean apply(ProcessTaskWrapper<?> task) {
                if (task.getExitCode()==0) return true;
                Entity entity = BrooklynTasks.getTargetOrContextEntity(Tasks.current());
                log.warn("Error setting up sudo for "+task.getMachine().getUser()+"@"+task.getMachine().getAddress().getHostName()+" "+
                        " (exit code "+task.getExitCode()+(entity!=null ? ", entity "+entity : "")+")");
                Streams.logStreamTail(log, "STDERR of sudo setup problem", Streams.byteArrayOfString(task.getStderr()), 1024);
                if (requireSuccess) {
                    throw new IllegalStateException("Passwordless sudo is required for "+task.getMachine().getUser()+"@"+task.getMachine().getAddress().getHostName()+
                            (entity!=null ? " ("+entity+")" : ""));
                }
                return true; 
            } });
    }

    /** Function for use in {@link ProcessTaskFactory#returning(Function)} which logs all information, optionally requires zero exit code, 
     * and then returns stdout */
    public static Function<ProcessTaskWrapper<?>, String> returningStdoutLoggingInfo(final Logger logger, final boolean requireZero) {
        return new Function<ProcessTaskWrapper<?>, String>() {
          public String apply(@Nullable ProcessTaskWrapper<?> input) {
            if (logger!=null) logger.info(input+" COMMANDS:\n"+Strings.join(input.getCommands(),"\n"));
            if (logger!=null) logger.info(input+" STDOUT:\n"+input.getStdout());
            if (logger!=null) logger.info(input+" STDERR:\n"+input.getStderr());
            if (requireZero && input.getExitCode()!=0) 
                throw new IllegalStateException("non-zero exit code in "+input.getSummary()+": see log for more details!");
            return input.getStdout();
          }
        };
    }

}
