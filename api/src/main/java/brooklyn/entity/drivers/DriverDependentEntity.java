package brooklyn.entity.drivers;

import brooklyn.entity.Entity;

/**
 * An Entity that needs to have a driver.
 *
 * @param <D>
 */
public interface DriverDependentEntity<D extends EntityDriver> extends Entity {

    Class<D> getDriverInterface();
}
