package brooklyn.location.basic;

import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver;

/** extension to LocationResolver which can take a registry */
public interface RegistryLocationResolver extends LocationResolver {

    /** similar to {@link #newLocationFromString(Map, String)} 
     * but passing in a reference to the registry itself (from which the base properties are discovered)
     * and including flags (e.g. user, key, cloud credential) which are known to be for this location.
     * <p>
     * introduced to support locations which refer to other locations, e.g. NamedLocationResolver  
     **/ 
    Location newLocationFromString(String spec, LocationRegistry registry, Map locationFlags);
    
}
