package brooklyn.entity.drivers;

import brooklyn.location.Location;

public interface EntityDriverFactory {

    <D extends EntityDriver> D build(DriverAwareEntity<D> entity, Location location);
}
