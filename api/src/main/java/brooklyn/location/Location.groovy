package brooklyn.location

import java.io.Serializable;
import java.util.Collection;

/**
 *  Location.
 */
public interface Location extends Serializable {
}

/**
 *  Single location.
 */
public interface SingleLocation {
    Location getLocation();
}

/**
 *  Multiple locations.
 */
public interface MultiLocation {
    Collection<Location> getLocations();
}
