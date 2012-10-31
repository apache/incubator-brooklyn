package brooklyn.entity;

import java.util.Collection;

/**
 * An {@link Entity} that groups together other entities.
 * 
 * The grouping can be for any purpose, such as allowing easy management/monitoring of
 * a group of entities. The grouping could be static (i.e. a fixed set of entities)
 * or dynamic (i.e. contains all entities that match some filter).
 */
public interface Group extends Entity {
    /**
     * Return the entities that are members of this group.
     */
    Collection<Entity> getMembers();

    /**
     * @return True if it is a member of this group.
     */
    boolean hasMember(Entity member);

    /**
     * Adds the given member, returning true if this modifies the set of members (i.e. it was not already a member).
     */
    boolean addMember(Entity member);
 
    /**
     * Removes the given member, returning true if this modifies the set of members (i.e. it was a member).
     */
    boolean removeMember(Entity member);
    
    /**
     * @return The number of members in this group.
     */
    Integer getCurrentSize();
}
