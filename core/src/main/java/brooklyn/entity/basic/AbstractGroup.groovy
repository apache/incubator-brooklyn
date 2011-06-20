package brooklyn.entity.basic

import java.util.Map
import java.util.concurrent.CopyOnWriteArraySet

import brooklyn.entity.Entity
import brooklyn.entity.Group

public abstract class AbstractGroup extends AbstractEntity implements Group {
    public AbstractGroup(Map props=[:]) {
        super(props)
    }

    final Collection<Entity> members = new CopyOnWriteArraySet<Entity>();

    final Collection<Entity> ownedChildren = new CopyOnWriteArraySet<Entity>();
    
    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    public Entity addMember(Entity member) {
        member.addGroup(this)
        members.add(t)
        member
    }
 
    public boolean removeMember(Entity child) {
        members.remove child
    }
    
    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    public Entity addOwnedChild(Entity child) {
        child.setOwner(this)
        ownedChildren.add(child)
        child
    }
 
    public boolean removeOwnedChild(Entity child) {
        ownedChildren.remove child
    }
}
