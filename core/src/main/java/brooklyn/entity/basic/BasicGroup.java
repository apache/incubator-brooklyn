package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.RebindContext;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;

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
    
    @Override
    public EntityMemento getMemento() {
        return super.getMementoWithProperties(ImmutableMap.of("childrenAsMembers", childrenAsMembers));
    }
    
    @Override
    protected void doRebind(RebindContext rebindContext, EntityMemento memento) {
        childrenAsMembers = (Boolean) memento.getProperty("childrenAsMembers");
    }
}
