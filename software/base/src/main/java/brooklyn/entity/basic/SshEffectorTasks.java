package brooklyn.entity.basic;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EffectorTasks.EffectorTaskFactory;
import brooklyn.entity.basic.SshTasks.AbstractSshTask;
import brooklyn.entity.basic.SshTasks.SshTask;
import brooklyn.entity.basic.SshTasks.SshTaskDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/** convenience classes and methods for working with ssh */
@Beta // added in 0.6.0
public class SshEffectorTasks {

    private static final Logger log = LoggerFactory.getLogger(SshEffectorTasks.class);
    
    /** like {@link EffectorBody} but providing conveniences when in a {@link SoftwareProcess}
     * (or other entity with a single machine location) */
    public abstract static class SshEffectorBody<T> extends EffectorBody<T> {
        
        /** convenience for accessing the machine */
        public SshMachineLocation machine() {
            return getMachineOfEntity(entity());
        }

        /** convenience for generating an {@link SshTask} which can be further customised if desired, and then (it must be explicitly) queued */
        public SshTask<Integer> ssh(String ...commands) {
            return new SshTask<Integer>(machine(), commands);
        }

        // TODO scp, install, etc
    }

    /** variant of {@link SshTask} which fulfills the {@link EffectorTaskFactory} signature so can be used directly as an impl for an effector,
     * also injects the machine automatically; can also be used outwith effector contexts, and machine is still injected if it is
     * run from inside a task at an entity with a single SshMachineLocation */
    public static class SshEffectorTask<RET> extends AbstractSshTask<SshEffectorTask<RET>,RET> implements EffectorTaskFactory<RET> {

        public SshEffectorTask(String ...commands) {
            super(commands);
        }

        @Override
        public Task<RET> newTask(Entity entity, Effector<RET> effector, ConfigBag parameters) {
            // NB this can only be used once to generate a task
            checkStillMutable();
            machine(getMachineOfEntity(entity));
            return getTask();
        }
        
        @Override
        protected TaskBuilder<Object> constructCustomizedTaskBuilder() {
            if (machine==null) {
                if (log.isDebugEnabled())
                    log.debug("Using an SshEffectorTask not in an effector without any machine; will attempt to infer the machine: "+this);
                Entity entity = BrooklynTasks.getTargetOrContextEntity(Tasks.current());
                if (entity!=null)
                    machine(getMachineOfEntity(entity));
            }
            return super.constructCustomizedTaskBuilder();
        }
        
        @SuppressWarnings("unchecked")
        public SshEffectorTask<String> requiringZeroAndReturningStdout() {
            return (SshEffectorTask<String>) super.requiringZeroAndReturningStdout();
        }
        
        @SuppressWarnings("unchecked")
        public <RET2> SshEffectorTask<RET2> returning(Function<SshTaskDetails, RET2> resultTransformation) {
            return (SshEffectorTask<RET2>) super.returning(resultTransformation);
        }
    }
    
    public static SshEffectorTask<Integer> ssh(String ...commands) {
        return new SshEffectorTask<Integer>(commands);
    }

    public static SshMachineLocation getMachineOfEntity(Entity entity) {
        try {
            return (SshMachineLocation) Iterables.getOnlyElement( entity.getLocations() );
        } catch (Exception e) {
            throw new IllegalStateException("Entity "+entity+" (in "+Tasks.current()+") requires a single SshMachineLocation, but has "+entity.getLocations(), e);
        }
    }

    /** task which returns 0 if pid is running */
    public static SshEffectorTask<Integer> codePidRunning(Integer pid) {
        return ssh("ps -p "+pid).summary("check PID "+pid);
    }
    
    /** task which fails if the given PID is not running */
    public static SshEffectorTask<?> requirePidRunning(Integer pid) {
        return codePidRunning(pid).summary("require PID "+pid).requiringExitCodeZero("Process with PID "+pid+" is required to be running");
    }

    /** as {@link #codePidRunning(String)} but returning boolean */
    public static SshEffectorTask<Boolean> isPidRunning(Integer pid) {
        return codePidRunning(pid).returning(new Function<SshTasks.SshTaskDetails, Boolean>() {
            public Boolean apply(@Nullable SshTaskDetails input) { return ((Integer)0).equals(input.getExitCode()); }
        });
    }


    /** task which returns 0 if pid in the given file is running;
     * method accepts wildcards so long as they match a single file on the remote end
     * <p>
     * returns 1 if no matching file, 
     * 1 if matching file but no matching process,
     * and 2 if 2+ matching files */
    public static SshEffectorTask<Integer> codePidFromFileRunning(String pidFile) {
        return ssh(CommonCommands.chain(
                CommonCommands.requireTest("-f "+pidFile, "The PID file "+pidFile+" does not exist."),
                "ps -p `cat "+pidFile+"`")).summary("check PID in file "+pidFile);
    }
    
    /** task which fails if the pid in the given file is not running (or if there is no such PID file);
     * method accepts wildcards so long as they match a single file on the remote end (fails if 0 or 2+ matching files) */
    public static SshEffectorTask<?> requirePidFromFileRunning(String pidFile) {
        return codePidFromFileRunning(pidFile)
                .summary("require PID in file "+pidFile+" to be running")
                .requiringExitCodeZero("Process with PID from file "+pidFile+" is required to be running");
    }

    /** as {@link #codePidFromFileRunning(String)} but returning boolean */
    public static SshEffectorTask<Boolean> isPidFromFileRunning(String pidFile) {
        return codePidFromFileRunning(pidFile).returning(new Function<SshTasks.SshTaskDetails, Boolean>() {
            public Boolean apply(@Nullable SshTaskDetails input) { return ((Integer)0).equals(input.getExitCode()); }
        });
    }
}
