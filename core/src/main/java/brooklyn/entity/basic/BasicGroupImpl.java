package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.util.MutableMap;

public class BasicGroupImpl extends AbstractGroup implements BasicGroup {
    
    public BasicGroupImpl() {
        super();
    }

    public BasicGroupImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }

    public BasicGroupImpl(Map flags) {
        this(flags, null);
    }
    
    public BasicGroupImpl(Map flags, Entity parent) {
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
