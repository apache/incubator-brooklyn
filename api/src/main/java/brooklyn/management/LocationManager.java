package brooklyn.management;

import java.util.Collection;
import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationSpec;

/**
 * For managing and querying entities.
 */
public interface LocationManager {

    /**
     * Creates a new location, which is tracked by the management context.
     * 
     * @param spec
     */
    <T extends Location> T createLocation(LocationSpec<T> spec);
    
    /**
     * Convenience (particularly for groovy code) to create a location.
     * Equivalent to {@code createLocation(LocationSpec.spec(type).configure(config))}
     * 
     * @see createLocation(LocationSpec)
     */
    <T extends Location> T createLocation(Map<?,?> config, Class<T> type);

    /**
     * All locations under control of this management plane
     */
    Collection<Location> getLocations();

    /**
     * Returns the location with the given identifier (may be a full instance, or a proxy to one which is remote)
     */
    Location getLocation(String id);
    
    /** whether the location is under management by this management context */
    boolean isManaged(Location location);

    /**
     * Begins management for the given location and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     * Manage a location.
     * 
     * @since 0.6.0 (added only for backwards compatibility, where locations are being created directly).
     * @deprecated in 0.6.0; use {@link #createLocation(LocationSpec)} instead.
     */
    void manage(Location e);
    
    /**
     * Causes the given location and its children, recursively, to be removed from the management plane
     * (for instance because the location is no longer relevant)
     */
    void unmanage(Location e);
}
