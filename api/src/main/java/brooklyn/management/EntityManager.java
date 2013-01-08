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
     * Manage an entity.
     */
    void manage(Entity e);
    
    /**
     * Unmanage an entity.
     */
    void unmanage(Entity e);
}
