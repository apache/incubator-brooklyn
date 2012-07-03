package brooklyn.location.basic;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.geo.HasHostGeoInfo;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.LanguageUtils;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A basic implementation of the {@link Location} interface.
 *
 * This provides an implementation which works according to the requirements of
 * the interface documentation, and is ready to be extended to make more specialized locations.
 * 
 * Override {@link #configure(Map)} to add special initialization logic.
 */
public abstract class AbstractLocation implements Location, HasHostGeoInfo {
    public static final Logger LOG = LoggerFactory.getLogger(AbstractLocation.class);

    @SetFromFlag
    String id;
    
    private Location parentLocation;
    private final Collection<Location> childLocations = Lists.newArrayList();
    private final Collection<Location> childLocationsReadOnly = Collections.unmodifiableCollection(childLocations);
    protected Map leftoverProperties = Maps.newLinkedHashMap();
    @SetFromFlag
    protected String name;
    
    protected HostGeoInfo hostGeoInfo;

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
    public AbstractLocation() {
        this(Maps.newLinkedHashMap());
    }
    public AbstractLocation(Map properties) {
        configure(properties);
        FlagUtils.checkRequiredFields(this);
    }

    /** will set fields from flags, and put the remaining ones into the 'leftovers' map.
     * can be subclassed for custom initialization but note the following. 
     * <p>
     * if you require fields to be initialized you must do that in this method,
     * with a guard (as in FixedListMachineProvisioningLocation).  you must *not*
     * rely on field initializers because they may not run until *after* this method
     * (this method is invoked by the constructor in this class, so initializers
     * in subclasses will not have run when this overridden method is invoked.) */ 
    protected void configure() {
        configure(Maps.newLinkedHashMap());
    }
    protected void configure(Map properties) {
        leftoverProperties.putAll(FlagUtils.setFieldsFromFlags(properties, this));
        //replace properties _contents_ with leftovers so subclasses see leftovers only
        properties.clear();
        properties.putAll(leftoverProperties);
        leftoverProperties = properties;
        
        if (id==null) id = LanguageUtils.newUid();
        
        if (!truth(name) && truth(properties.get("displayName"))) {
            //'displayName' is a legacy way to refer to a location's name
            //FIXME could this be a GString?
            Preconditions.checkArgument(properties.get("displayName") instanceof String, "'displayName' property should be a string");
            name = (String) properties.remove("displayName");
        }

        if (truth(properties.get("parentLocation"))) {
            Preconditions.checkArgument(properties.get("parentLocation") == null || properties.get("parentLocation") instanceof Location,
                "'parentLocation' property should be a Location instance");
            setParentLocation((Location)properties.remove("parentLocation"));
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
        Location loc = potentialDescendent;
        while (loc != null) {
            if (this == loc) return true;
            loc = loc.getParentLocation();
        }
        return false;
    }
    
    protected void addChildLocation(Location child) {
        childLocations.add(child); 
    }
    
    protected boolean removeChildLocation(Location child) {
        return childLocations.remove(child);
    }

    public void setParentLocation(Location parent) {
        if (parent == this) {
            throw new IllegalArgumentException("Location cannot be its own parent: "+this);
        }
        if (parent == parentLocation) {
            return; // no-op; already have desired parent
        }
        if (parentLocation != null) {
            Location oldParent = parentLocation;
            parentLocation = null;
            ((AbstractLocation)oldParent).removeChildLocation(this); // FIXME Nasty cast
        }
        if (parent != null) {
            parentLocation = parent;
            ((AbstractLocation)parentLocation).addChildLocation(this); // FIXME Nasty cast
        }
    }
    
    public boolean hasLocationProperty(String key) { return leftoverProperties.containsKey(key); }
    public Object getLocationProperty(String key) { return leftoverProperties.get(key); }
    public Object findLocationProperty(String key) {
        if (hasLocationProperty(key)) return getLocationProperty(key);
        if (parentLocation != null) return parentLocation.findLocationProperty(key);
        return null;
    }
    
    /** Default String representation is simplified name of class, together with selected fields. */
    @Override
    public String toString() {
        return string().toString();
    }
    
    /** override this, adding to the returned value, to supply additional fields to include in the toString */
    protected ToStringHelper string() {
        return Objects.toStringHelper(AbstractLocation.class).add("id", id).add("name", name);
    }
    
    public HostGeoInfo getHostGeoInfo() { return hostGeoInfo; }    
    public void setHostGeoInfo(HostGeoInfo hostGeoInfo) {
        if (hostGeoInfo!=null) { 
            this.hostGeoInfo = hostGeoInfo;
            if (!truth(getLocationProperty("latitude"))) leftoverProperties.put("latitude", hostGeoInfo.latitude); 
            if (!truth(getLocationProperty("longitude"))) leftoverProperties.put("longitude", hostGeoInfo.longitude);
        } 
    }
       
}
