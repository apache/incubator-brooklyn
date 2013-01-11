package brooklyn.entity.basic;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

// FIXME Don't want to extend EntityLocal, but tests call group.addPolicy(); how to deal with that elegantly?
@ImplementedBy(BasicGroupImpl.class)
public interface BasicGroup extends Entity, Group, EntityLocal {
    
    @SetFromFlag("childrenAsMembers")
    public static final ConfigKey<Boolean> CHILDREN_AS_MEMBERS = new BasicConfigKey<Boolean>(
            Boolean.class, "brooklyn.BasicGroup.childrenAsMembers", 
            "Whether children are automatically added as group members", false);
}
