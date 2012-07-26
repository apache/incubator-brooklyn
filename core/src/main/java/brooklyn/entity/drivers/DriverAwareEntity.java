package brooklyn.entity.drivers;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.lifecycle.StartStopDriver;

public interface DriverAwareEntity<D extends StartStopDriver> extends Entity {

    Class<D> getDriverInterface();
}
