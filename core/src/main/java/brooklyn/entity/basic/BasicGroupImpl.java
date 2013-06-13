package brooklyn.entity.basic;

import brooklyn.entity.Entity;

public class BasicGroupImpl extends AbstractGroupImpl implements BasicGroup {
    
    public BasicGroupImpl() {
        super();
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
