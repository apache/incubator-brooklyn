package brooklyn.location.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.location.Location
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Preconditions

/**
 * A basic implementation of the {@link Location} interface.
 *
 * This provides an implementation which works according to the requirements of
 * the interface documentation, and is ready to be extended to make more specialized locations.
 * 
 * Override {@link #configure(Map)} to add special initialization logic.
 */
public abstract class AbstractLocation implements Location {
    public static final Logger LOG = LoggerFactory.getLogger(Location.class)

    @SetFromFlag
    String id
    
    private Location parentLocation
    private final Collection<Location> childLocations = []
    private final Collection<Location> childLocationsReadOnly = Collections.unmodifiableCollection(childLocations)
    protected Map leftoverProperties = [:];
    @SetFromFlag
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
     * <li>displayName
     * </ul>
     * 
     * @param properties
     */
    public AbstractLocation(Map properties = [:]) {
        configure(properties)
        FlagUtils.checkRequiredFields(this)
    }

    /** will set fields from flags, and put the remaining ones into the 'leftovers' map.
     * can be subclassed for custom initialization but note the following. 
     * <p>
     * if you require fields to be initialized you must do that in this method,
     * with a guard (as in FixedListMachineProvisioningLocation).  you must *not*
     * rely on field initializers because they may not run until *after* this method
     * (this method is invoked by the constructor in this class, so initializers
     * in subclasses will not have run when this overridden method is invoked.) */ 
    protected void configure(Map properties) {
        leftoverProperties << FlagUtils.setFieldsFromFlags(properties, this)
        //replace properties _contents_ with leftovers so subclasses see leftovers only
        properties.clear();
        properties.putAll(leftoverProperties)
        leftoverProperties = properties;
        
        if (id==null) id = LanguageUtils.newUid();
        
        if (!name && properties.displayName) {
            //'displayName' is a legacy way to refer to a location's name
            Preconditions.checkArgument properties.displayName instanceof String, "'displayName' property should be a string"
            name = properties.remove("displayName")
        }

        if (properties.parentLocation) {
            Preconditions.checkArgument properties.parentLocation == null || properties.parentLocation instanceof Location,
                "'parentLocation' property should be a Location instance"
            setParentLocation(properties.remove("parentLocation"))
        }
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public Location getParentLocation() { return parentLocation; }
    public Collection<Location> getChildLocations() { return childLocationsReadOnly; }

    public boolean equals(Object o) {
        if (! (o instanceof Location)) {
            return false;
        }

        Location l = (Location) o;
		return getId().equals(l.getId());
    }

    public int hashCode() {
        return getId().hashCode();
    }

    public boolean containsLocation(Location potentialDescendent) {
        Location loc = potentialDescendent
        while (loc != null) {
            if (this == loc) return true
            loc = loc.getParentLocation()
        }
        return false
    }
    
    protected void addChildLocation(Location child) {
        childLocations.add(child); 
    }
    
    protected boolean removeChildLocation(Location child) {
        return childLocations.remove(child);
    }

    public void setParentLocation(Location parent) {
        if (parent == this) {
            throw new IllegalArgumentException("Location cannot be its own parent: "+this)
        }
        if (parent == parentLocation) {
            return // no-op; already have desired parent
        }
        if (parentLocation != null) {
            Location oldParent = parentLocation;
            parentLocation = null;
            oldParent.removeChildLocation(this);
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
