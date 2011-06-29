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
    
//    public Entity addMember(Entity child);
//    public boolean removeMember(Entity child);
}
