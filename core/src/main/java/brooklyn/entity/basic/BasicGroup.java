package brooklyn.entity.basic;

import brooklyn.config.ConfigKey;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(BasicGroupImpl.class)
public interface BasicGroup extends AbstractGroup {

    @SetFromFlag("childrenAsMembers")
    /** @deprecated since 0.7.0 use {@link Group#addMemberChild} */
    @Deprecated
    ConfigKey<Boolean> CHILDREN_AS_MEMBERS = new BasicConfigKey<Boolean>(
            Boolean.class, "brooklyn.BasicGroup.childrenAsMembers", 
            "Whether children are automatically added as group members", false);

}
