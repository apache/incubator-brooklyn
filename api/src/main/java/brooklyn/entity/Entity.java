package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.mementos.EntityMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

/**
 * The basic interface for a Brooklyn entity.
 * 
 * @see AbstractEntity
 */
public interface Entity extends Serializable, Rebindable<EntityMemento> {
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
    EntityType getEntityType();
    
    /*
     * Return the {@link Application} this entity is registered with.
     */
    Application getApplication();

    /**
     * Return the id of the {@link Application} this entity is registered with.
     */
    String getApplicationId();

    /**
     * The parent of this entity, null if no parent.
     *
     * The parent is normally the entity responsible for creating/destroying/managing this entity.
     *
     * @see #setParent(Entity)
     * @see #clearParent
     */
    Entity getParent();
    
    /** 
     * Return the entities that are children of (i.e. "owned by") this entity
     */
    Collection<Entity> getChildren();
    
    /**
     * Sets the parent (i.e. "owner") of this entity. Returns this entity, for convenience.
     *
     * @see #getParent
     * @see #clearParent
     */
    Entity setParent(Entity parent);
    
    /**
     * Clears the parent (i.e. "owner") of this entity. Also cleans up any references within its parent entity.
     *
     * @see #getParent
     * @see #setParent
     */
    void clearParent();
    
    /** 
     * Add a child {@link Entity}, and set this entity as its parent.
     */
    Entity addChild(Entity child);
    
    /** 
     * Removes the specified child {@link Entity}; its parent will be set to null.
     * 
     * @return True if the given entity was contained in the set of children
     */
    boolean removeChild(Entity child);
    
    /**
     * @deprecated see getParent()
     */
    @Deprecated
    Entity getOwner();

    /** 
     * @deprecated see getChildren()
     */
    @Deprecated
    Collection<Entity> getOwnedChildren();
    
    /**
     * @deprecated see setOwner(Entity)
     */
    @Deprecated
    Entity setOwner(Entity group);
    
    /**
     * @deprecated see clearParent()
     */
    @Deprecated
    void clearOwner();
    
    /** 
     * @deprecated see addChild(Entity)
     */
    @Deprecated
    Entity addOwnedChild(Entity child);
    
    /** 
     * @deprecated see removeChild(Entity)
     */
    @Deprecated
    boolean removeOwnedChild(Entity child);
    
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
     * its parent.
     */
    <T> T getConfig(ConfigKey<T> key);
    
    /**
     * Invokes the given effector, with the given parameters to that effector.
     */
    <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters);
}
