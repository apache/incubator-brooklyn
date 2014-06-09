package brooklyn.entity.group;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(QuarantineGroupImpl.class)
public interface QuarantineGroup extends AbstractGroup {

    @Effector(description="Removes all members of the quarantined group, unmanaging them")
    void expungeMembers(
            @EffectorParam(name="firstStop", description="Whether to first call stop() on those members that are stoppable") boolean stopFirst);
}
