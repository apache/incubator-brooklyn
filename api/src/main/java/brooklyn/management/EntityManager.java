package brooklyn.management;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntityTypeRegistry;

/**
 * For managing and querying entities.
 */
public interface EntityManager {

    /**
     * Returns the type registry, used to identify the entity implementation when instantiating an
     * entity of a given type.
     * 
     * @see EntityManager.createEntity(EntitySpec)
     */
    EntityTypeRegistry getEntityTypeRegistry();
    
    /**
     * Creates a new (unmanaged) entity.
     * 
     * @param spec
     * @return A proxy to the created entity (rather than the actual entity itself).
     */
    <T extends Entity> T createEntity(EntitySpec<T> spec);
    
    /**
     * Convenience (particularly for groovy code) to create an entity.
     * Equivalent to {@code createEntity(EntitySpec.create(type).configure(config))}
     * 
     * @see createEntity(EntitySpec)
     */
    <T extends Entity> T createEntity(Map<?,?> config, Class<T> type);

    /**
     * All entities under control of this management plane
     */
    Collection<Entity> getEntities();

    /**
     * Returns the entity with the given identifier (may be a full instance, or a proxy to one which is remote),
     * or null.
     */
    @Nullable Entity getEntity(String id);
    
    /** whether the entity is under management by this management context */
    boolean isManaged(Entity entity);

    /**
     * Begins management for the given entity and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     * Manage an entity.
     */
    void manage(Entity e);
    
    /**
     * Causes the given entity and its children, recursively, to be removed from the management plane
     * (for instance because the entity is no longer relevant)
     */
    void unmanage(Entity e);
}
