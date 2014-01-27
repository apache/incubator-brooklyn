package brooklyn.entity;

import java.util.Collection;

import brooklyn.entity.proxying.EntitySpec;

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
    
    /** as {@link #addChild(EntitySpec)} followed by {@link #addMember(Entity)} */
    public <T extends Entity> T addMemberChild(EntitySpec<T> spec);
    
    /** as {@link #addChild(Entity)} followed by {@link #addMember(Entity)} */
    public <T extends Entity> T addMemberChild(T child);
    
    @Override
    /** as in super, but note this does NOT by default add it as a member; see {@link #addMemberChild(EntitySpec)} */
    public <T extends Entity> T addChild(EntitySpec<T> spec);
    
    @Override
    /** as in super, but note this does NOT by default add it as a member; see {@link #addMemberChild(Entity)} */
    public <T extends Entity> T addChild(T child);

}
