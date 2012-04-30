package brooklyn.entity.basic

import groovy.transform.InheritConstructors

import java.util.Collection
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.AbstractAggregatingEnricher
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Changeable


/**
 * Represents a group of entities - sub-classes can support dynamically changing membership, 
 * ad hoc groupings, etc.
 * <p> 
 * Synchronization model. When changing and reading the group membership, this class uses internal 
 * synchronization to ensure atomic operations and the "happens-before" relationship for reads/updates
 * from different threads. Sub-classes should not use this same synchronization mutex when doing 
 * expensive operations - e.g. if resizing a cluster, don't block everyone else from asking for the
 * current number of members.
 */
@InheritConstructors
public abstract class AbstractGroup extends AbstractEntity implements Group, Changeable {
    private static final Logger log = LoggerFactory.getLogger(AbstractGroup.class);
    private final EntityCollectionReference<Entity> _members = new EntityCollectionReference<Entity>(this);

    public AbstractGroup(Map props=[:], Entity owner=null) {
        super(props, owner)
        setAttribute(Changeable.GROUP_SIZE, 0)
    }

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child
     */
    public void addMember(Entity member) {
        synchronized (_members) {
	        member.addGroup(this)
	        if (_members.add(member)) {
                log.debug("Group $this got new member $member");
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
                log.debug("Group $this lost member $member");
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
