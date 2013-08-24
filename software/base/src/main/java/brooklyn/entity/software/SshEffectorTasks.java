package brooklyn.entity.software;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.ssh.SshFetchTaskFactory;
import brooklyn.util.task.ssh.SshFetchTaskWrapper;
import brooklyn.util.task.ssh.SshPutTaskFactory;
import brooklyn.util.task.ssh.SshPutTaskWrapper;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.ssh.internal.AbstractSshExecTaskFactory;
import brooklyn.util.task.ssh.internal.PlainSshExecTaskFactory;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/** convenience classes and methods for working with {@link SshTasks}, where the
 * {@link SshMachineLocation} is inferred either from the effector generation or
 * the context task 
 * 
 * @since 0.6.0 */
@Beta
public class SshEffectorTasks {

    private static final Logger log = LoggerFactory.getLogger(SshEffectorTasks.class);
    
    /** like {@link EffectorBody} but providing conveniences when in a {@link SoftwareProcess}
     * (or other entity with a single machine location) */
    public abstract static class SshEffectorBody<T> extends EffectorBody<T> {
        
        /** convenience for accessing the machine */
        public SshMachineLocation machine() {
            return getMachineOfEntity(entity());
        }

        /** convenience for generating an {@link PlainSshExecTaskFactory} which can be further customised if desired, and then (it must be explicitly) queued */
        public ProcessTaskFactory<Integer> ssh(String ...commands) {
            return SshTasks.newSshExecTaskFactory(machine(), commands);
        }

        // TODO scp, install, etc
    }

    /** variant of {@link PlainSshExecTaskFactory} which fulfills the {@link EffectorTaskFactory} signature so can be used directly as an impl for an effector,
     * also injects the machine automatically; can also be used outwith effector contexts, and machine is still injected if it is
     * run from inside a task at an entity with a single SshMachineLocation */
    public static class SshEffectorTask<RET> extends AbstractSshExecTaskFactory<SshEffectorTask<RET>,RET> implements EffectorTaskFactory<RET> {

        public SshEffectorTask(String ...commands) {
            super(commands);
        }

        @Override
        public ProcessTaskWrapper<RET> newTask(Entity entity, Effector<RET> effector, ConfigBag parameters) {
            markDirty();
            if (summary==null) summary(effector.getName()+" (ssh)");
            machine(getMachineOfEntity(entity));
            return newTask();
        }
        
        @Override
        public synchronized ProcessTaskWrapper<RET> newTask() {
            if (machine==null) {
                if (log.isDebugEnabled())
                    log.debug("Using an SshEffectorTask not in an effector without any machine; will attempt to infer the machine: "+this);
                Entity entity = BrooklynTasks.getTargetOrContextEntity(Tasks.current());
                if (entity!=null)
                    machine(getMachineOfEntity(entity));
            }
            return super.newTask();
        }
        
        @SuppressWarnings("unchecked")
        public SshEffectorTask<String> requiringZeroAndReturningStdout() {
            return (SshEffectorTask<String>) super.requiringZeroAndReturningStdout();
        }
        
        @SuppressWarnings("unchecked")
        public <RET2> SshEffectorTask<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
            return (SshEffectorTask<RET2>) super.returning(resultTransformation);
        }
    }
    
    public static class SshPutEffectorTaskFactory extends SshPutTaskFactory implements EffectorTaskFactory<Void> {
        public SshPutEffectorTaskFactory(String remoteFile) {
            super(remoteFile);
        }
        @Override
        public SshPutTaskWrapper newTask(Entity entity, Effector<Void> effector, ConfigBag parameters) {
            machine(getMachineOfEntity(entity));
            return super.newTask();
        }
        @Override
        public SshPutTaskWrapper newTask() {
            Entity entity = BrooklynTasks.getTargetOrContextEntity(Tasks.current());
            if (entity!=null)
                machine(getMachineOfEntity(entity));
            return super.newTask();
        }
    }

    public static class SshFetchEffectorTaskFactory extends SshFetchTaskFactory implements EffectorTaskFactory<String> {
        public SshFetchEffectorTaskFactory(String remoteFile) {
            super(remoteFile);
        }
        @Override
        public SshFetchTaskWrapper newTask(Entity entity, Effector<String> effector, ConfigBag parameters) {
            machine(getMachineOfEntity(entity));
            return super.newTask();
        }
        @Override
        public SshFetchTaskWrapper newTask() {
            Entity entity = BrooklynTasks.getTargetOrContextEntity(Tasks.current());
            if (entity!=null)
                machine(getMachineOfEntity(entity));
            return super.newTask();
        }
    }

    public static SshEffectorTask<Integer> ssh(String ...commands) {
        return new SshEffectorTask<Integer>(commands);
    }

    public static SshPutTaskFactory put(String remoteFile) {
        return new SshPutEffectorTaskFactory(remoteFile);
    }

    public static SshFetchEffectorTaskFactory fetch(String remoteFile) {
        return new SshFetchEffectorTaskFactory(remoteFile);
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
        return ssh("ps -p "+pid).summary("PID "+pid+" is-running check (exit code)").allowingNonZeroExitCode();
    }
    
    /** task which fails if the given PID is not running */
    public static SshEffectorTask<?> requirePidRunning(Integer pid) {
        return codePidRunning(pid).summary("PID "+pid+" is-running check (required)").requiringExitCodeZero("Process with PID "+pid+" is required to be running");
    }

    /** as {@link #codePidRunning(String)} but returning boolean */
    public static SshEffectorTask<Boolean> isPidRunning(Integer pid) {
        return codePidRunning(pid).summary("PID "+pid+" is-running check (boolean)").returning(new Function<ProcessTaskWrapper<?>, Boolean>() {
            public Boolean apply(@Nullable ProcessTaskWrapper<?> input) { return ((Integer)0).equals(input.getExitCode()); }
        });
    }


    /** task which returns 0 if pid in the given file is running;
     * method accepts wildcards so long as they match a single file on the remote end
     * <p>
     * returns 1 if no matching file, 
     * 1 if matching file but no matching process,
     * and 2 if 2+ matching files */
    public static SshEffectorTask<Integer> codePidFromFileRunning(final String pidFile) {
        return ssh(BashCommands.chain(
                // this fails, but isn't an error
                BashCommands.requireTest("-f "+pidFile, "The PID file "+pidFile+" does not exist."),
                // this fails and logs an error picked up later
                BashCommands.requireTest("`ls "+pidFile+" | wc -w` -eq 1", "ERROR: there are multiple matching PID files"),
                // this fails and logs an error picked up later
                BashCommands.require("cat "+pidFile, "ERROR: the PID file "+pidFile+" cannot be read (permissions?)."),
                // finally check the process
                "ps -p `cat "+pidFile+"`")).summary("PID file "+pidFile+" is-running check (exit code)")
                .allowingNonZeroExitCode()
                .addCompletionListener(new Function<ProcessTaskWrapper<?>,Void>() {
                    public Void apply(ProcessTaskWrapper<?> input) {
                        if (input.getStderr().contains("ERROR:"))
                            throw new IllegalStateException("Invalid or inaccessible PID filespec: "+pidFile);
                        return null;
                    }
                });
    }
    
    /** task which fails if the pid in the given file is not running (or if there is no such PID file);
     * method accepts wildcards so long as they match a single file on the remote end (fails if 0 or 2+ matching files) */
    public static SshEffectorTask<?> requirePidFromFileRunning(String pidFile) {
        return codePidFromFileRunning(pidFile)
                .summary("PID file "+pidFile+" is-running check (required)")
                .requiringExitCodeZero("Process with PID from file "+pidFile+" is required to be running");
    }

    /** as {@link #codePidFromFileRunning(String)} but returning boolean */
    public static SshEffectorTask<Boolean> isPidFromFileRunning(String pidFile) {
        return codePidFromFileRunning(pidFile).summary("PID file "+pidFile+" is-running check (boolean)").
                returning(new Function<ProcessTaskWrapper<?>, Boolean>() {
                    public Boolean apply(@Nullable ProcessTaskWrapper<?> input) { return ((Integer)0).equals(input.getExitCode()); }
                });
    }

}
