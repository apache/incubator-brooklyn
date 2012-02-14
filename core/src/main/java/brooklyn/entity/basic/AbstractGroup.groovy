package brooklyn.entity.basic

import groovy.transform.InheritConstructors

import java.util.Collection
import java.util.Map

import brooklyn.enricher.basic.AbstractAggregatingEnricher
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Changeable


@InheritConstructors
public abstract class AbstractGroup extends AbstractEntity implements Group, Changeable {
    final EntityCollectionReference<Entity> _members = new EntityCollectionReference<Entity>(this);

    public AbstractGroup(Map props=[:], Entity owner=null) {
        super(props, owner)
        setAttribute(Changeable.GROUP_SIZE, 0)
    }

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    public Entity addMember(Entity member) {
        synchronized (_members) {
	        member.addGroup(this)
	        if (_members.add(member)) {
	            emit(MEMBER_ADDED, member)
	            setAttribute(Changeable.GROUP_SIZE, currentSize)
                enrichers.each { if (it instanceof AbstractAggregatingEnricher) ((AbstractAggregatingEnricher)it).addProducer(member); }
	        }
	        member
	    }
    }
 
    /**
     * Returns <code>true</code> if the group was changed as a result of the call.
     */
    public boolean removeMember(Entity member) {
        synchronized (_members) {
            boolean changed = (member != null && _members.remove(member))
            if (changed) {
	            emit(MEMBER_REMOVED, member)
	            setAttribute(Changeable.GROUP_SIZE, currentSize)
                enrichers.each { 
                    if (it instanceof AbstractAggregatingEnricher) 
                        ((AbstractAggregatingEnricher)it).removeProducer(member); 
                }
	        }
	        changed
        }
    }
 
    // Declared so can be overridden (the default auto-generated getter is final!)
    public Collection<Entity> getMembers() {
        synchronized (_members) {
            return _members.get()
        }
    }

    public boolean hasMember(Entity e) {
        synchronized (_members) {
            return _members.contains(e)
        }
    }

    public Integer getCurrentSize() {
        synchronized (_members) {
            return _members.size()
        }
    }
}
