package brooklyn.location;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.mementos.LocationMemento;

/**
 *  Location.
 */
public interface Location extends Serializable, Rebindable<LocationMemento> {

    /**
     * A unique id for this location.
     */
    String getId();

    /**
     * Get the name assigned to this location.
     *
     * @return the name assigned to the location.
     */
    String getName();

    /**
     * Get the 'parent' of this location. Locations are organized into a tree hierarchy, and this method will return a reference
     * to the parent of this location, or {@code null} if this location is the tree root.
     *
     * @return a reference to the parent of this location, or {@code null} if this location is the tree root.
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
     * the other location first. It is valid to pass in {@code null} to indicate that the location should be disconnected
     * from its parent.
     * 
     * Adds this location as a child of the new parent (see {@code getChildLocations()}).
     *
     * @param newParent the new parent location object, or {@code null} to clear the parent reference.
     */
    void setParentLocation(Location newParent);

    /**
     * Answers true if this location equals or is an ancestor of the given location.
     */
    boolean containsLocation(Location potentialDescendent);

    /** Returns configuration set at this location or inherited */
    <T> T getConfig(ConfigKey<T> key);
    
    /** True iff the indication config key is set _at_ this location (not parents) */
    boolean hasConfig(ConfigKey<?> key);

    /** Returns all config set _at_ this location (not inherited) */
    Map<String,Object> getAllConfig();

    /**
     * Returns {@code true} iff this location contains a property with the specified {@code key}. The
     * property's value can be obtained by calling {@link #getLocationProperty}. This method only interrogates the
     * immediate properties; the parent hierarchy is NOT searched in the event that the property is not found locally.
     * @deprecated since 0.5.0, use hasConfig
     */
    @Deprecated
    boolean hasLocationProperty(String key);
    
    /**
     * Returns the value of the property identified by the specified {@code key}. This method only interrogates the
     * immediate properties; the parent hierarchy is NOT searched in the event that the property is not found locally.
     * 
     * NOTE: must not name this method 'getProperty' as this will clash with the 'magic' Groovy's method of the same
     *       name, at which point everything stops working!
     * @deprecated since 0.5.0, use `if (hasConfig) { getConfig }` if you really need to preserve 
     * "don't look at parents" behaviour
     */
    @Deprecated
    Object getLocationProperty(String key);
    
//    /**
//     * Returns the location properties of this immediate location (i.e. not including those from the parent hierarchy).
//     * @deprecated since 0.5.0, use getAllConfig
//     */
//    @Deprecated
//    Map<String,?> getLocationProperties();
    
    /**
     * Like {@link #getLocationProperty}, but if the property is not defined on this location, searches recursively up
     * the parent hierarchy until it is found, or the root is reached (when this method will return {@code null}).
     * @deprecated since 0.5.0, use getConfig
     */
    @Deprecated
    Object findLocationProperty(String key);
}
