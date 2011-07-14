package brooklyn.entity.basic

import groovy.util.ObservableList.ElementAddedEvent
import groovy.util.ObservableList.ElementRemovedEvent

import java.beans.PropertyChangeListener
import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Changeable

public abstract class AbstractGroup extends AbstractEntity implements Group, Changeable {
    public AbstractGroup(Map props=[:], Entity owner=null) {
        super(props, owner)
    }

    final EntityCollectionReference<Group> members = new EntityCollectionReference<Group>(this);

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    public Entity addMember(Entity member) {
        member.addGroup(this)
        members.add(member)
        listeners.each { it.propertyChange(new ElementAddedEvent(this, member, -1)) }
        member
    }
 
    public boolean removeMember(Entity child) {
        members.remove child
        listeners.each { it.propertyChange(new ElementRemovedEvent(this, child, -1)) }
    }
    
    // Declared so can be overridden (the default auto-generated getter is final!)
    public Collection<Entity> getMembers() {
        return members.get()
    }

    //FIXME: use sensors; and also needed for owned children
    Set<PropertyChangeListener> listeners = new LinkedHashSet<PropertyChangeListener>();
    public void addEntityChangeListener(PropertyChangeListener listener) {
        listeners << listener
    }
}
