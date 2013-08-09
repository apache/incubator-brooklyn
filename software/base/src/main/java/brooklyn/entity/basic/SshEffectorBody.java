package brooklyn.entity.basic;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.task.Tasks;

import com.google.common.collect.Iterables;

/** like {@link EffectorBody} but providing conveniences when in a {@link SoftwareProcess}
 * (or other entity with a single machine location) */
public abstract class SshEffectorBody<T> extends EffectorBody<T> {
    
    /** convenience for accessing the machine */
    public SshMachineLocation machine() {
        try {
            return (SshMachineLocation) Iterables.getOnlyElement( entity().getLocations() );
        } catch (Exception e) {
            throw new IllegalStateException("Effector "+this+" ("+Tasks.current()+" at "+entity()+") requires a single SshMachineLocation at the entity", e);
        }
    }
    
    /** convenience for generating an {@link SshTask} which can be further customised if desired, and then (it must be explicitly) queued */
    public SshTask ssh(String ...commands) {
        return new SshTask(machine(), commands);
    }

    // TODO scp, install, etc
}
