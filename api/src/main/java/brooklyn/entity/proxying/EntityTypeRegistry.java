package brooklyn.entity.proxying;

import brooklyn.entity.Entity;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.location.Location;

/**
 * A registry for entity implementations to use when an entity needs to be created
 * of a given type.
 * 
 * A given implementation can only be associated with one entity type interface.
 */
public interface EntityTypeRegistry {

    /**
     * Returns the implementation to be used for the given entity type.
     *
     * @param entity the {@link DriverDependentEntity} to create the {@link EntityDriver} for.
     * @param location the {@link Location} where the {@link DriverDependentEntity} is running.
     * @param <D>
     * @return the creates EntityDriver.
     * @throws IllegalArgumentException If no implementation registered, and the given interface is not annotated with {@link ImplementedBy}
     * @throws IllegalStateException If the given type is not an interface, or if the implementation class is not a concrete class implementing it
     */
    <T extends Entity> Class<? extends T> getImplementedBy(Class<T> type);

    /**
     * Returns the interface of this entity implementation.
     * E.g. for use as the fully qualified name in {@code entity.getEntityType().getName()}.
     * 
     * @throws IllegalArgumentException If no interface is registered against this implementation, and no super-type of the class is annotated with {@link ImplementedBy} to point at the given class
     */
    <T extends Entity> Class<? super T> getEntityTypeOf(Class<T> type);

    /**
     * Registers the implementation to use for a given entity type.
     * 
     * The implementation must be a non-abstract class implementing the given type, and must 
     * have a no-argument constructor.
     * 
     * @throws IllegalArgumentException If this implementation has already been registered for a different type
     * @throws IllegalStateException If the implClazz is not a concrete class, or does not implement type
     */
    <T extends Entity> EntityTypeRegistry registerImplementation(Class<T> type, Class<? extends T> implClazz);
}
