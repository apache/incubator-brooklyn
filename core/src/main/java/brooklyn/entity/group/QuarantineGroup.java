package brooklyn.entity.group;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(QuarantineGroupImpl.class)
public interface QuarantineGroup extends AbstractGroup {

    ConfigKey<Boolean> MEMBER_DELEGATE_CHILDREN = ConfigKeys.newConfigKeyWithDefault(AbstractGroup.MEMBER_DELEGATE_CHILDREN, Boolean.TRUE);

    @Effector(description="Removes all members of the quarantined group, unmanaging them")
    void expungeMembers(
            @EffectorParam(name="firstStop", description="Whether to first call stop() on those members that are stoppable") boolean stopFirst);
}
