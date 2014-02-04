package brooklyn.management.internal;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.management.EntityManager;

public interface EntityManagerInternal extends EntityManager {

    /** gets all entities currently known to the application, including entities that are not yet managed */
    Iterable<Entity> getAllEntitiesInApplication(Application application);

}
