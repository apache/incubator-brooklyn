package brooklyn.management;

import java.util.Collection;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.EntityDriverFactory;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.LocationRegistry;

/**
 * For managing and querying entities.
 */
public interface EntityManager {

    /**
     * All entities under control of this management plane
     */
    Collection<Entity> getEntities();

    /**
     * Returns the entity with the given identifier (may be a full instance, or a proxy to one which is remote)
     */
    Entity getEntity(String id);
    
    /** whether the entity is under management by this management context */
    boolean isManaged(Entity entity);

    /**
     * Begins management for the given entity and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     */
    void manage(Entity e);
    
    /**
     * Causes the given entity and its children, recursively, to be removed from the management plane
     * (for instance because the entity is no longer relevant)
     */
    void unmanage(Entity e);
}
