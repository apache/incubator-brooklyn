package brooklyn.entity.basic

import java.util.Collection;
import java.util.Map
import java.util.concurrent.CopyOnWriteArraySet

import brooklyn.entity.Entity
import brooklyn.entity.Group

public abstract class AbstractGroup extends AbstractEntity implements Group {
    public AbstractGroup(Map props=[:], Entity owner=null) {
        super(props, owner)
    }

    final Collection<Entity> members = new CopyOnWriteArraySet<Entity>();

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    public Entity addMember(Entity member) {
        member.addGroup(this)
        members.add(member)
        member
    }
 
    public boolean removeMember(Entity child) {
        members.remove child
    }
    
    // Declared so can be overridden (the default auto-generated getter is final!)
    public Collection<Entity> getMembers() {
        return members;
    }
}
