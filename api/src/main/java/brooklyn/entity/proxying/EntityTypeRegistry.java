package brooklyn.entity.proxying;

import brooklyn.entity.Entity;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.location.Location;

/**
 * A registry for entity implementations to use when an entity needs to be created
 * of a given type.
 */
public interface EntityTypeRegistry {

    /**
     * Returns the implementation to be used for the given entity type.
     *
     * @param entity the {@link DriverDependentEntity} to create the {@link EntityDriver} for.
     * @param location the {@link Location} where the {@link DriverDependentEntity} is running.
     * @param <D>
     * @return the creates EntityDriver.
     */
    <T extends Entity> Class<? extends T> getImplementedBy(Class<T> type);

    /**
     * Registers the implementation to use for a given entity type.
     * 
     * The implementation must be a non-abstract class implementing the given type, and must 
     * have a no-argument constructor.
     */
    <T extends Entity> EntityTypeRegistry registerImplementation(Class<T> type, Class<? extends T> implClazz);
}
