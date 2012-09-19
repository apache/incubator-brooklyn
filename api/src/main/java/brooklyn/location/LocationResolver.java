package brooklyn.location;

import java.util.Map;

public interface LocationResolver {

    /** the prefix that this resolver will attend to */
    String getPrefix();
    
    /** returns a Location instance, e.g. a JcloudsLocation instance configured to provision in AWS eu-west-1;
     * the properties map may contain lots of info some of which may be relevant to this location
     * (eg containing credentials for many clouds, and resolver picks out the ones applicable here) --
     * commonly it is a BrooklynProperties instance, reading from .brooklyn/brooklyn.properties */ 
    Location newLocationFromString(@SuppressWarnings("rawtypes") Map properties, String spec);
    
}
