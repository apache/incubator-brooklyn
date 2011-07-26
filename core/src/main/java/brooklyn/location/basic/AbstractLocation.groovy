package brooklyn.location.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.location.Location

import com.google.common.base.Preconditions

/**
 * A basic implementation of the {@link Location} interface.
 *
 * This provides an implementation which works according to the requirements of
 * the interface documentation, and is ready to be extended to make more specialized locations.
 */
public abstract class AbstractLocation implements Location {
    public static final Logger LOG = LoggerFactory.getLogger(Location.class)
 
    private Location parentLocation
    private final Collection<Location> childLocations = []
    private final Collection<Location> childLocationsReadOnly = Collections.unmodifiableCollection(childLocations)
    protected Map leftoverProperties
    protected String name

    /**
     * Construct a new instance of an AbstractLocation.
     *
     * The properties map recognizes the following keys:
     * <ul>
     * <li>name - a name for the location
     * <li>parentLocation - the parent {@link Location}
     * </ul>
     * 
     * Other common properties (retrieved via get/findLocationProperty) include:
     * <ul>
     * <li>latitude
     * <li>longitude
     * <li>displayName
     * <li>iso3166 - list of iso3166-2 code strings
     * <li>timeZone
     * <li>abbreviatedName
     * </ul>
     * 
     * @param properties
     */
    public AbstractLocation(Map properties = [:]) {
        if (properties.name) {
            Preconditions.checkArgument properties.name == null || properties.name instanceof String,
                "'name' property should be a string"
            name = properties.remove("name")
        }
        if (properties.parentLocation) {
            Preconditions.checkArgument properties.parentLocation == null || properties.parentLocation instanceof Location,
                "'parentLocation' property should be a Location instance"
            setParentLocation(properties.remove("parentLocation"))
        }
        leftoverProperties = properties
    }

    public String getName() { return name; }
    public Location getParentLocation() { return parentLocation; }
    public Collection<Location> getChildLocations() { return childLocationsReadOnly; }
    protected void addChildLocation(Location child) { childLocations.add(child); }
    protected boolean removeChildLocation(Location child) { return childLocations.remove(child); }

    public void setParentLocation(Location parent) {
        if (parentLocation != null) {
            parentLocation.removeChildLocation(this);
            parentLocation = null;
        }
        if (parent != null) {
            parentLocation = parent;
            parentLocation.addChildLocation(this);
        }
    }
    
    public boolean hasLocationProperty(String key) { return leftoverProperties.containsKey(key); }
    public Object getLocationProperty(String key) { return leftoverProperties.get(key); }
    public Object findLocationProperty(String key) {
        if (hasLocationProperty(key)) return getLocationProperty(key);
        if (parentLocation != null) return parentLocation.findLocationProperty(key);
        return null;
    }
}
