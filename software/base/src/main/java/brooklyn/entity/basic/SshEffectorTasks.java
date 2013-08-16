package brooklyn.entity.basic;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EffectorTasks.EffectorTaskFactory;
import brooklyn.entity.basic.SshTasks.AbstractSshTask;
import brooklyn.entity.basic.SshTasks.SshTask;
import brooklyn.entity.basic.SshTasks.SshTaskDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/** convenience classes and methods for working with ssh */
@Beta // added in 0.6.0
public class SshEffectorTasks {

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
     * also injects the machine automatically */
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
        
        @SuppressWarnings("unchecked")
        public SshEffectorTask<String> requiringZeroAndReturningStdout() {
            return (SshEffectorTask<String>) super.requiringZeroAndReturningStdout();
        }
        
        @SuppressWarnings("unchecked")
        public <RET2> SshTask<RET2> returning(Function<SshTaskDetails, RET2> resultTransformation) {
            return (SshTask<RET2>) super.returning(resultTransformation);
        }
    }
    
    public static SshEffectorTask<?> ssh(String ...commands) {
        return new SshEffectorTask<Integer>(commands);
    }

    public static SshMachineLocation getMachineOfEntity(Entity entity) {
        try {
            return (SshMachineLocation) Iterables.getOnlyElement( entity.getLocations() );
        } catch (Exception e) {
            throw new IllegalStateException("Entity "+entity+" (in "+Tasks.current()+") requires a single SshMachineLocation, but has "+entity.getLocations(), e);
        }
    }

    
}
