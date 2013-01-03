package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

public class BasicGroup extends AbstractGroup {
    
    @SetFromFlag("childrenAsMembers")
    public static final ConfigKey<Boolean> CHILDREN_AS_MEMBERS = new BasicConfigKey<Boolean>(
            Boolean.class, "brooklyn.BasicGroup.childrenAsMembers", 
            "Whether children are automatically added as group members", false);

    public BasicGroup() {
        super(MutableMap.of(), null);
    }

    public BasicGroup(Entity parent) {
        super(MutableMap.of(), parent);
    }

    public BasicGroup(Map flags) {
        this(flags, null);
    }
    
    public BasicGroup(Map flags, Entity parent) {
        super(flags, parent);
    }
    
    @Override
    public Entity addChild(Entity child) {
        Entity result = super.addChild(child);
        if (getConfig(CHILDREN_AS_MEMBERS)) {
            addMember(child);
        }
        return result;
    }
    
    @Override
    public boolean removeChild(Entity child) {
        boolean result = super.removeChild(child);
        if (getConfig(CHILDREN_AS_MEMBERS)) {
            removeMember(child);
        }
        return result;
    }
}
