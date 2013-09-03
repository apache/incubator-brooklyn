package brooklyn.entity;

import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.mementos.EntityMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

/**
 * The basic interface for a Brooklyn entity.
 * <p>
 * Implementors of entities are strongly encouraged to extend {@link brooklyn.entity.basic.AbstractEntity}.
 * <p>
 * To instantiate an entity, see {@code managementContext.getEntityManager().createEntity(entitySpec)}.
 * Also see {@link brooklyn.entity.basic.ApplicationBuilder}, 
 * {@link brooklyn.entity.basic.AbstractEntity#addChild(EntitySpec)}, and
 * {@link brooklyn.entity.proxying.EntitySpecs}.
 * <p>
 * 
 * @see brooklyn.entity.basic.AbstractEntity
 */
public interface Entity extends Rebindable {
    /**
     * The unique identifier for this entity.
     */
    String getId();
    
    /**
     * Returns the creation time for this entity, in UTC.
     */
    long getCreationTime();
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();
    
    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    EntityType getEntityType();
    
    /**
     * @return the {@link Application} this entity is registered with, or null if not registered.
     */
    Application getApplication();

    /**
     * @return the id of the {@link Application} this entity is registered with, or null if not registered.
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
     * Add a child {@link Entity}, and set this entity as its parent,
     * returning the added child.
     */
    <T extends Entity> T addChild(T child);
    
    /** 
     * Creates an {@link Entity} from the given spec and adds it, setting this entity as the parent,
     * returning the added child. */
    <T extends Entity> T addChild(EntitySpec<T> spec);
    
    /** 
     * Removes the specified child {@link Entity}; its parent will be set to null.
     * 
     * @return True if the given entity was contained in the set of children
     */
    boolean removeChild(Entity child);
    
    /**
     * @return an immutable thread-safe view of the policies.
     */
    Collection<Policy> getPolicies();
    
    /**
     * @return an immutable thread-safe view of the enrichers.
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
     * Gets the given configuration value for this entity, in the following order of preference:
     * <li> value (including null) explicitly set on the entity
     * <li> value (including null) explicitly set on an ancestor (inherited)
     * <li> a default value (including null) on the best equivalent static key of the same name declared on the entity
     *      (where best equivalence is defined as preferring a config key which extends another, 
     *      as computed in EntityDynamicType.getConfigKeys)
     * <li> a default value (including null) on the key itself
     * <li> null
     */
    <T> T getConfig(ConfigKey<T> key);
    <T> T getConfig(HasConfigKey<T> key);
    
    /**
     * Invokes the given effector, with the given parameters to that effector.
     */
    <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters);
    
    /**
     * Adds the given policy to this entity. Also calls policy.setEntity if available.
     */
    void addPolicy(Policy policy);
    
    /**
     * Removes the given policy from this entity. 
     * @return True if the policy existed at this entity; false otherwise
     */
    boolean removePolicy(Policy policy);
    
    /**
     * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
     */
    void addEnricher(Enricher enricher);
    
    /**
     * Removes the given enricher from this entity. 
     * @return True if the policy enricher at this entity; false otherwise
     */
    boolean removeEnricher(Enricher enricher);

    @Override
    RebindSupport<EntityMemento> getRebindSupport();
}
