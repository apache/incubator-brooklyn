package brooklyn.entity.basic

import java.util.Collection
import java.util.Map

import brooklyn.enricher.basic.BaseAggregatingEnricher;
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Changeable

public abstract class AbstractGroup extends AbstractEntity implements Group, Changeable {
    final EntityCollectionReference<Entity> members = new EntityCollectionReference<Entity>(this);

    public AbstractGroup(Map props=[:], Entity owner=null) {
        super(props, owner)
        setAttribute(Changeable.GROUP_SIZE, 0)
    }

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    public Entity addMember(Entity member) {
        synchronized (members) {
	        member.addGroup(this)
	        if (members.add(member)) {
	            emit(MEMBER_ADDED, member)
	            setAttribute(Changeable.GROUP_SIZE, currentSize)
                enrichers.each { if (it instanceof BaseAggregatingEnricher) ((BaseAggregatingEnricher)it).addProducer(member); }
	        }
	        member
	    }
    }
 
    /**
     * Returns <code>true</code> if the group was changed as a result of the call.
     */
    public boolean removeMember(Entity member) {
        synchronized (members) {
            boolean changed = (member != null && members.remove(member))
            if (changed) {
	            emit(MEMBER_REMOVED, member)
	            setAttribute(Changeable.GROUP_SIZE, currentSize)
                enrichers.each { if (it instanceof BaseAggregatingEnricher) ((BaseAggregatingEnricher)it).removeProducer(member); }
	        }
	        changed
        }
    }
 
    // Declared so can be overridden (the default auto-generated getter is final!)
    public Collection<Entity> getMembers() {
        return members.get()
    }
 
    public Integer getCurrentSize() {
        return getMembers().size()
    }
}
