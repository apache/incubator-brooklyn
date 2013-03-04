package brooklyn.entity.drivers;

import brooklyn.location.Location;

/**
 * Allows customizing the drivers to be used be entities in given locations.
 * 
 * The idea is that an entity should not be tightly coupled to a specific driver implementation, 
 * so that there is flexibility for driver changes, without changing the entity itself. The 
 * advantage is that drivers can easily be reconfigured, replaced or new drivers for different 
 * environments can be added, without needing to modify Brooklyn.
 * 
 * To obtain an instance of a driver, use {@link #build(DriverDependentEntity, Location)}.
 * This will use the registered driver types, or if one is not registered will fallback to the default 
 * strategy.
 */
public interface EntityDriverRegistry extends EntityDriverFactory {

    public <D extends EntityDriver> void registerDriver(Class<D> driverInterface, Class<? extends Location> locationClazz, Class<? extends D> driverClazz);
}
