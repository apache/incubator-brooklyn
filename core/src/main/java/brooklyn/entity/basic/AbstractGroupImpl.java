package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityReferences.EntityCollectionReference;
import brooklyn.entity.trait.Changeable;

import com.google.common.base.Predicate;


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
public abstract class AbstractGroupImpl extends AbstractEntity implements AbstractGroup {
    private static final Logger log = LoggerFactory.getLogger(AbstractGroup.class);
    private final EntityCollectionReference<Entity> _members = new EntityCollectionReference<Entity>(this);

    public AbstractGroupImpl() {
    }
    
    @Override
    public void init() {
        super.init();
        setAttribute(Changeable.GROUP_SIZE, 0);
    }
    
    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child
     */
    @Override
    public boolean addMember(Entity member) {
        synchronized (_members) {
	        member.addGroup(this);
	        boolean changed = _members.add(member);
	        if (changed) {
                log.debug("Group {} got new member {}", this, member);
	            emit(MEMBER_ADDED, member);
	            setAttribute(Changeable.GROUP_SIZE, getCurrentSize());
	            
	            getManagementSupport().getEntityChangeListener().onMembersChanged();
	        }
	        return changed;
	    }
    }
 
    /**
     * Returns {@code true} if the group was changed as a result of the call.
     */
    @Override
    public boolean removeMember(Entity member) {
        synchronized (_members) {
            boolean changed = (member != null && _members.remove(member));
            if (changed) {
                log.debug("Group {} lost member {}", this, member);
	            emit(MEMBER_REMOVED, member);
	            setAttribute(Changeable.GROUP_SIZE, getCurrentSize());
	            
	            getManagementSupport().getEntityChangeListener().onMembersChanged();
	        }
            
	        return changed;
        }
    }
    
    public void setMembers(Collection<Entity> m) {
        setMembers(m, null);
    }
    public void setMembers(Collection<Entity> mm, Predicate<Entity> filter) {
        synchronized (_members) {
            log.debug("Group {} members set explicitly to {} (of which some possibly filtered)", this, _members);
            List<Entity> mmo = new ArrayList<Entity>(getMembers());
            for (Entity m: mmo) {
                if (!(mm.contains(m) && (filter==null || filter.apply(m))))
                    // remove, unless already present, being set, and not filtered out
                    removeMember(m); 
            }
            for (Entity m: mm) {
                if ((!mmo.contains(m)) && (filter==null || filter.apply(m))) {
                    // add if not alrady contained, and not filtered out
                    addMember(m);
                }
            }
            
            getManagementSupport().getEntityChangeListener().onMembersChanged();
        }
    }
 
    // Declared so can be overridden (the default auto-generated getter is final!)
    @Override
    public Collection<Entity> getMembers() {
        synchronized (_members) {
            return _members.get();
        }
    }

    @Override
    public boolean hasMember(Entity e) {
        synchronized (_members) {
            return _members.contains(e);
        }
    }

    @Override
    public Integer getCurrentSize() {
        synchronized (_members) {
            return _members.size();
        }
    }
}
