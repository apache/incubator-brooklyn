package brooklyn.entity.drivers;

import brooklyn.location.Location;

public interface EntityDriver {
    /**
     * The location the entity is running in.
     */
    Location getLocation();
}
