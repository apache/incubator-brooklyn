package brooklyn.location.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;

/**
 * looks up based on ID in DefinedLocations map
 */
public class DefinedLocationByIdResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(DefinedLocationByIdResolver.class);

    public static final String ID = "id";
    
    @SuppressWarnings("rawtypes")
    @Override
    public Location newLocationFromString(Map properties, String spec) {
        throw new UnsupportedOperationException("This class must have the RegistryLocationResolver.newLocationFromString method invoked");
    }
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        String id = spec;
        if (spec.toLowerCase().startsWith(ID+":")) {
            id = spec.substring( (ID+":").length() );
        }
        LocationDefinition ld = registry.getDefinedLocation(id);
        return ((BasicLocationRegistry)registry).resolveLocationDefinition(ld, locationFlags, id);
    }

    @Override
    public String getPrefix() {
        return ID;
    }
    
    /** accepts anything starting  id:xxx  or just   xxx where xxx is a defined location ID */
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        if (BasicLocationRegistry.isResolverPrefixForSpec(this, spec, false)) return true;
        if (registry.getDefinedLocation(spec)!=null) return true;
        return false;
    }

}
