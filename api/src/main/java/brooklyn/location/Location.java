package brooklyn.location;

import java.io.Serializable;
import java.util.Collection;

/**
 *  Location.
 */
public interface Location extends Serializable {
    /**
     * Get the name assigned to this location.
     *
     * @return the name assigned to the location.
     */
    String getName();

    /**
     * Get the 'parent' of this location. Locations are organized into a tree hierarchy, and this method will return a reference
     * to the parent of this location, or <code>null</code> if this location is the tree root.
     *
     * @return a reference to the parent of this location, or <code>null</code> if this location is the tree root.
     */
    Location getParentLocation();

    /**
     * Get the 'children' of this location. Locations are organized into a tree hierarchy, and this method will return a
     * collection containing the children of this location. This collection is an unmodifiable view of the data.
     *
     * @return a collection containing the children of this location.
     */
    Collection<Location> getChildLocations();

    /**
     * Set the 'parent' of this location. If this location was previously a child of a different location, it is removed from
     * the other location first. It is valid to pass in <code>null</code> to indicate that the location should be disconnected
     * from its parent.
     *
     * @param newParent the new parent location object, or <code>null</code> to clear the parent reference.
     */
    void setParentLocation(Location newParent);

    /**
     * TODO Return the ISO-3166 country code.
     * TODO A location could be in multiple iso-3166-2 locations.
     */
//    String getCountryCode();
}
