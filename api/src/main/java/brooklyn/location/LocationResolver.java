package brooklyn.location;

import java.util.Map;

public interface LocationResolver {

    /** the prefix that this resolver will attend to */
    String getPrefix();
    
    /** returns a Location instance, e.g. a JcloudsLocation instance configured to provision in AWS eu-west-1;
     * the properties map (typically a BrooklynProperties instance) may contain credentials etc,
     * as defined in .brooklyn/brooklyn.properties */ 
    Location newLocationFromString(Map properties, String spec);
    
}
