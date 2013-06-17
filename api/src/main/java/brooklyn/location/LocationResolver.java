package brooklyn.location;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Provides a way of creating location instances of a particular type.
 */
public interface LocationResolver {

    /** the prefix that this resolver will attend to */
    String getPrefix();
    
    /** whether the spec is something which should be passed to this resolver */
    boolean accepts(String spec, brooklyn.location.LocationRegistry registry);
    
    /**
     * Returns a Location instance, e.g. a JcloudsLocation instance configured to provision in AWS eu-west-1;
     * the properties map may contain lots of info some of which may be relevant to this location
     * (eg containing credentials for many clouds, and resolver picks out the ones applicable here) --
     * commonly it is a BrooklynProperties instance, read from .brooklyn/brooklyn.properties
     * <p>
     * @throws NoSuchElementException if not found
     * 
     * @deprecated since 0.6; use {@link #newLocationFromString(Map, String, LocationRegistry)}
     */ 
    @Deprecated
    Location newLocationFromString(@SuppressWarnings("rawtypes") Map properties, String spec);

    /**
     * Similar to {@link #newLocationFromString(Map, String)} 
     * but passing in a reference to the registry itself (from which the base properties are discovered)
     * and including flags (e.g. user, key, cloud credential) which are known to be for this location.
     * <p>
     * introduced to support locations which refer to other locations, e.g. NamedLocationResolver  
     **/ 
    @SuppressWarnings("rawtypes")
    Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry);
}
