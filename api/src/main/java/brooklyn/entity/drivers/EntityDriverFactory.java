package brooklyn.entity.drivers;

import brooklyn.location.Location;

/**
 * A Factory responsible for creating a driver for a given entity/location. The idea is that an entity should not
 * be tightly coupled to a specific driver implementation, so that there is flexibility for driver changes, without
 * changing the entity itself. The advantage is that drivers can easily be reconfigured, replaced or new drivers for
 * different environments can be added, without needing to modify Brooklyn.
 */
public interface EntityDriverFactory {

    /**
     * Builds a new {@link EntityDriver} for the given entity/location.
     *
     * @param entity the {@link DriverDependentEntity} to create the {@link EntityDriver} for.
     * @param location the {@link Location} where the {@link DriverDependentEntity} is running.
     * @param <D>
     * @return the creates EntityDriver.
     */
    <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location);
}
