package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

public class BasicGroup extends AbstractGroup {
    
    @SetFromFlag
    private boolean childrenAsMembers;
    
    public BasicGroup() {
        super(MutableMap.of(), null);
    }

    public BasicGroup(Entity owner) {
        super(MutableMap.of(), owner);
    }

    public BasicGroup(Map flags) {
        this(flags, null);
    }
    
    public BasicGroup(Map flags, Entity owner) {
        super(flags, owner);
    }
    
    @Override
    public Entity addOwnedChild(Entity child) {
        Entity result = super.addOwnedChild(child);
        if (childrenAsMembers) {
            addMember(child);
        }
        return result;
    }
    
    @Override
    public boolean removeOwnedChild(Entity child) {
        boolean result = super.removeOwnedChild(child);
        if (childrenAsMembers) {
            removeMember(child);
        }
        return result;
    }
}
