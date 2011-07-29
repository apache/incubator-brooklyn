package brooklyn.location;

import java.io.Serializable;
import java.util.Collection;

/**
 *  Location.
 */
public interface Location extends Serializable {

    /**
     * A unique id for this location.
     */
    String getID();

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
     * Answers true if this location equals or is an ancestor of the given location.
     */
    boolean containsLocation(Location potentialDescendent);
    
    /**
     * TODO Return the ISO-3166 country code.
     * TODO A location could be in multiple iso-3166-2 locations.
     */
//    String getCountryCode();

    /**
     * Returns <code>true</code> iff this location contains a property with the specified <code>key</code>. The
     * property's value can be obtained by calling {@link #getLocationProperty}. This method only interrogates the
     * immediate properties; the parent hierarchy is NOT searched in the event that the property is not found locally.
     */
    boolean hasLocationProperty(String key);
    
    /**
     * Returns the value of the property identified by the specified <code>key</code>. This method only interrogates the
     * immediate properties; the parent hierarchy is NOT searched in the event that the property is not found locally.
     * 
     * NOTE: must not name this method 'getProperty' as this will clash with the 'magic' Groovy's method of the same
     *       name, at which point everything stops working!
     */
    Object getLocationProperty(String key);
    
    /**
     * Like {@link #getLocationProperty}, but if the property is not defined on this location, searches recursively up
     * the parent hierarchy until it is found, or the root is reached (when this method will return <code>null</code>).
     */
    Object findLocationProperty(String key);

}
