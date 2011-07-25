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

    final EntityCollectionReference<Entity> members = new EntityCollectionReference<Entity>(this);

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    public synchronized Entity addMember(Entity member) {
        member.addGroup(this)
        if (members.add(member)) {
            emit(MEMBER_ADDED, member)
            setAttribute(Changeable.GROUP_SIZE, currentSize)
        }
        member
    }
 
    public synchronized boolean removeMember(Entity member) {
        if (members.remove(member)) {
            emit(MEMBER_REMOVED, member)
            setAttribute(Changeable.GROUP_SIZE, currentSize)
        }
        member
    }
 
    // Declared so can be overridden (the default auto-generated getter is final!)
    public Collection<Entity> getMembers() {
        return members.get()
    }
 
    public int getCurrentSize() {
        return getMembers().size()
    }

}
