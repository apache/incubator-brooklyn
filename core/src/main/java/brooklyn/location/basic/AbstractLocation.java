package brooklyn.location.basic;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.BasicLocationRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.trait.Configurable;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.location.geo.HasHostGeoInfo;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;

/**
 * A basic implementation of the {@link Location} interface.
 *
 * This provides an implementation which works according to the requirements of
 * the interface documentation, and is ready to be extended to make more specialized locations.
 * 
 * Override {@link #configure(Map)} to add special initialization logic.
 */
public abstract class AbstractLocation implements Location, HasHostGeoInfo, Configurable {
    
    public static final Logger LOG = LoggerFactory.getLogger(AbstractLocation.class);

    public static final ConfigKey<Location> PARENT_LOCATION = new BasicConfigKey<Location>(Location.class, "parentLocation");
    
    @SetFromFlag
    String id;

    // _not_ set from flag; configured explicitly in configure, because we also need to update the parent's list of children
    private Location parentLocation;
    
    private final Collection<Location> childLocations = Lists.newArrayList();
    private final Collection<Location> childLocationsReadOnly = Collections.unmodifiableCollection(childLocations);
    
    @SetFromFlag
    protected String name;
    
    protected HostGeoInfo hostGeoInfo;

    final private ConfigBag configBag = new ConfigBag();


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
        boolean deferConstructionChecks = (properties.containsKey("deferConstructionChecks") && TypeCoercions.coerce(properties.get("deferConstructionChecks"), Boolean.class));
        if (!deferConstructionChecks) {
        	FlagUtils.checkRequiredFields(this);
        }
    }

    /** @deprecated in 0.5.0, not used or exposed; use configure(Map) */
    public final void configure() {
        configure(Maps.newLinkedHashMap());
    }
    
    /** will set fields from flags. The unused configuration can be found via the 
     * {@linkplain ConfigBag#getUnusedConfig()}.
     * This can be overridden for custom initialization but note the following. 
     * <p>
     * if you require fields to be initialized you must do that in this method,
     * with a guard (as in FixedListMachineProvisioningLocation).  you must *not*
     * rely on field initializers because they may not run until *after* this method
     * (this method is invoked by the constructor in this class, so initializers
     * in subclasses will not have run when this overridden method is invoked.) */ 
    protected void configure(Map properties) {
        boolean firstTime = (id==null);
        if (firstTime) {
            // pick a random ID if one not set
            id = properties.containsKey("id") ? (String)properties.get("id") : Identifiers.makeRandomId(8);
        }
        configBag.putAll(properties);
        
        if (properties.containsKey(PARENT_LOCATION.getName())) {
            // need to ensure parent's list of children is also updated
            setParent(configBag.get(PARENT_LOCATION));
            
            // don't include parentLocation in configBag, as breaks rebind
            configBag.remove(PARENT_LOCATION);
        }

        // NB: flag-setting done here must also be done in BasicLocationRebindSupport 
        FlagUtils.setFieldsFromFlagsWithBag(this, properties, configBag, firstTime);
        
        if (!truth(name) && truth(properties.get("displayName"))) {
            //'displayName' is a legacy way to refer to a location's name
            //FIXME could this be a GString?
            Preconditions.checkArgument(properties.get("displayName") instanceof String, "'displayName' property should be a string");
            name = (String) properties.remove("displayName");
        }
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public String getDisplayName() {
        return name;
    }
    
    @Override
    @Deprecated
    /** @since 0.6.0 (?) - use getDisplayName */
    public String getName() {
        return getDisplayName();
    }
    
    @Override
    public Location getParent() {
        return parentLocation;
    }
    
    @Override
    public Collection<Location> getChildren() {
        return childLocationsReadOnly;
    }

    @Override
    public void setParent(Location parent) {
        if (parent == this) {
            throw new IllegalArgumentException("Location cannot be its own parent: "+this);
        }
        if (parent == parentLocation) {
            return; // no-op; already have desired parent
        }
        if (parentLocation != null) {
            Location oldParent = parentLocation;
            parentLocation = null;
            ((AbstractLocation)oldParent).removeChild(this); // FIXME Nasty cast
        }
        if (parent != null) {
            parentLocation = parent;
            ((AbstractLocation)parentLocation).addChild(this); // FIXME Nasty cast
        }
    }

    @Override
    @Deprecated
    public Location getParentLocation() {
        return getParent();
    }
    
    @Override
    @Deprecated
    public Collection<Location> getChildLocations() {
        return getChildren();
    }

    @Override
    @Deprecated
    public void setParentLocation(Location parent) {
        setParent(parent);
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        if (hasConfig(key, false)) return getConfigBag().get(key);
        if (getParent()!=null) return getParent().getConfig(key);
        return key.getDefaultValue();
    }
    @Override
    @Deprecated
    public boolean hasConfig(ConfigKey<?> key) {
        return hasConfig(key, false);
    }
    @Override
    public boolean hasConfig(ConfigKey<?> key, boolean includeInherited) {
        boolean locally = getRawLocalConfigBag().containsKey(key);
        if (locally) return true;
        if (!includeInherited) return false;
        if (getParent()!=null) return getParent().hasConfig(key, true);
        return false;
    }
    
    @Override
    @Deprecated
    public Map<String,Object> getAllConfig() {
        return getAllConfig(false);
    }
    @Override
    public Map<String,Object> getAllConfig(boolean includeInherited) {
        Map<String,Object> result = null;
        if (includeInherited) {
            Location p = getParent();
            if (p!=null) result = getParent().getAllConfig(true);
        }
        if (result==null) {
            result = new LinkedHashMap<String, Object>();
        }
        result.putAll(getConfigBag().getAllConfig());
        return result;
    }
    
    /** @deprecated since 0.6.0 use {@link #getRawLocalConfigBag()} */
    public ConfigBag getConfigBag() {
        return configBag;
    }
    public ConfigBag getRawLocalConfigBag() {
        return configBag;
    }
    
    @Override
    public <T> T setConfig(ConfigKey<T> key, T value) {
        return configBag.put(key, value);
    }
    
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof Location)) {
            return false;
        }

        Location l = (Location) o;
		return getId().equals(l.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public boolean containsLocation(Location potentialDescendent) {
        Location loc = potentialDescendent;
        while (loc != null) {
            if (this == loc) return true;
            loc = loc.getParent();
        }
        return false;
    }
    
    /**
     * @deprecated since 0.6
     * @see addChild(Location)
     */
    @Deprecated
    public void addChildLocation(Location child) {
        addChild(child);
    }
    
    public void addChild(Location child) {
        // Previously, setParent delegated to addChildLocation and we sometimes ended up with
        // duplicate entries here. Instead this now uses a similar scheme to 
        // AbstractLocation.setParent/addChild (with any weaknesses for distribution that such a 
        // scheme might have...).
        // 
        // We continue to use a list to allow identical-looking locations, but they must be different 
        // instances.
        
        for (Location contender : childLocations) {
                if (contender == child) {
                        // don't re-add; no-op
                        return;
                }
        }
        
        childLocations.add(child);
        child.setParent(this);
    }
    
    /**
     * @deprecated since 0.6
     * @see removeChild(Location)
     */
    @Deprecated
    protected boolean removeChildLocation(Location child) {
        return removeChild(child);
    }
    
    protected boolean removeChild(Location child) {
        boolean removed = childLocations.remove(child);
        if (removed) {
            if (child instanceof Closeable) {
                Closeables.closeQuietly((Closeable)child);
            }
            child.setParent(null);
        }
        return removed;
    }

    @Override
    @Deprecated
    public boolean hasLocationProperty(String key) { return configBag.containsKey(key); }
    
    @Override
    @Deprecated
    public Object getLocationProperty(String key) { return configBag.getStringKey(key); }
    
    @Override
    @Deprecated
    public Object findLocationProperty(String key) {
        if (hasLocationProperty(key)) return getLocationProperty(key);
        if (parentLocation != null) return parentLocation.findLocationProperty(key);
        return null;
    }
    
//    @Override
//    public Map<String,?> getLocationProperties() {
//    	return Collections.<String,Object>unmodifiableMap(leftoverProperties);
//    }

    /** Default String representation is simplified name of class, together with selected fields. */
    @Override
    public String toString() {
        return string().toString();
    }
    
    @Override
    public String toVerboseString() {
        return toString();
    }

    /** override this, adding to the returned value, to supply additional fields to include in the toString */
    protected ToStringHelper string() {
        return Objects.toStringHelper(getClass()).add("id", id).add("name", name);
    }
    
    @Override
    public HostGeoInfo getHostGeoInfo() { return hostGeoInfo; }
    
    public void setHostGeoInfo(HostGeoInfo hostGeoInfo) {
        if (hostGeoInfo!=null) { 
            this.hostGeoInfo = hostGeoInfo;
            setConfig(LocationConfigKeys.LATITUDE, hostGeoInfo.latitude); 
            setConfig(LocationConfigKeys.LONGITUDE, hostGeoInfo.longitude); 
        } 
    }

    @Override
    public RebindSupport<LocationMemento> getRebindSupport() {
        return new BasicLocationRebindSupport(this);
    }
}
