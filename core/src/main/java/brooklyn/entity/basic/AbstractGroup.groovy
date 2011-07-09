package brooklyn.entity.basic

import java.util.Collection;
import java.util.Map
import java.util.concurrent.CopyOnWriteArrayList;
import java.beans.PropertyChangeListener;

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Changeable
import brooklyn.util.internal.SerializableObservableList;


public abstract class AbstractGroup extends AbstractEntity implements Group, Changeable {
    public AbstractGroup(Map props=[:], Entity owner=null) {
        super(props, owner)
    }

    final ObservableList members = new SerializableObservableList(new CopyOnWriteArrayList<Entity>());

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
        return members
    }

    public void addEntityChangeListener(PropertyChangeListener listener) {
        members.addPropertyChangeListener(listener)
    }
}
