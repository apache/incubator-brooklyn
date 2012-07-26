package brooklyn.entity.drivers;

import brooklyn.entity.Entity;

public interface DriverAwareEntity<D extends EntityDriver> extends Entity {

    Class<D> getDriverInterface();
}
