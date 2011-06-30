package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.location.Location;

/**
 * The basic interface for a Brooklyn entity.
 * 
 * @see AbstractEntity
 */
public interface Entity extends Serializable {
    /**
     * The unique identifier for this entity.
     */
    String getId();
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();
    
    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    EntityClass getEntityClass();
    
    /*
     * Return the {@link Application} this entity is registered with.
     */
    Application getApplication();

    /**
     * Return the id of the {@link Application} this entity is registered with.
     */
    String getApplicationId();
    
    @Deprecated
    Map<String, Object> getAttributes();

    /**
     * The owner of this entity, null if no owner.
     *
     * The owner is normally the entity responsible for creating/destroying this entity.
     */
    Entity getOwner();

    /** 
     * @return the entities that are owned by this entity
     */
    Collection<Entity> getOwnedChildren();
    
    /**
     * The {@link Collection} of {@link Group}s that this entity is a member of.
     *
     * Groupings can be used to allow easy management/monitoring of a group of entities.
     */
    Collection<Group> getGroups();

    /**
     * Add this entity as a member of the given {@link Group}.
     */
    void addGroup(Group group);

    /**
     * Return all the {@link Location}s this entity is deployed to.
     */
    Collection<Location> getLocations();
}
