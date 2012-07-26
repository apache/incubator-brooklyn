package brooklyn.entity.drivers;

import brooklyn.location.Location;

public interface DriverFactory {

    public final static DriverFactory INSTANCE = new BasicDriverFactory();

    Object build(DriverAwareEntity entity, Location location);
}
