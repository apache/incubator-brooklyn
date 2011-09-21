package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

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

    /**
     * The owner of this entity, null if no owner.
     *
     * The owner is normally the entity responsible for creating/destroying this entity.
     *
     * @see #setOwner(Group)
     * @see #clearOwner
     */
    Entity getOwner();

    /** 
     * Return the entities that are owned by this entity
     */
    Collection<Entity> getOwnedChildren();
    
    /**
     * Sets the owner of this entity.
     *
     * @see #getOwner
     * @see #clearOwner
     */
    void setOwner(Entity group);
    
    /**
     * Clears the owner of this entity. Also cleans up any references within its parent entity.
     *
     * @see #getOwner
     * @see #setOwner
     */
    void clearOwner();
    
    /** 
     * Add a child {@link Entity}, and set this entity as its owner.
     */
    Entity addOwnedChild(Entity child);
    
    /** 
     * Removes the specified child {@link Entity}; its owner will be set to null.
     */
    void removeOwnedChild(Entity child);
    
    /**
     * @return an immutable thread-safe view of the policies.
     */
    Collection<Policy> getPolicies();
    
    /**
     * @return an immutable thread-safe view of the policies.
     */
    Collection<Enricher> getEnrichers();
    
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

    /**
     * Gets the value of the given attribute on this entity, or null if has not been set.
     *
     * Attributes can be things like workrate and status information, as well as
     * configuration (e.g. url/jmxHost/jmxPort), etc.
     */
    <T> T getAttribute(AttributeSensor<T> sensor);

    /**
     * Gets the given configuration value for this entity, which may be inherited from
     * its owner.
     */
    <T> T getConfig(ConfigKey<T> key);
    
    /**
     * Invokes the given effector, with the given parameters to that effector.
     */
    <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters);
}
