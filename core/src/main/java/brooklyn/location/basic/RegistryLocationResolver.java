package brooklyn.location.basic;

import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver;

/** extension to LocationResolver which can take a registry */
public interface RegistryLocationResolver extends LocationResolver {

    /** returns a Location instance, e.g. a JcloudsLocation instance configured to provision in AWS eu-west-1;
     * the properties map (typically a BrooklynProperties instance) may contain credentials etc,
     * as defined in .brooklyn/brooklyn.properties */ 
    Location newLocationFromString(String spec, LocationRegistry registry, Map locationFlags);
    
}
