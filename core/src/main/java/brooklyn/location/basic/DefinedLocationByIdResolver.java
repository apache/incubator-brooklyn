package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.management.ManagementContext;

/**
 * looks up based on ID in DefinedLocations map
 */
public class DefinedLocationByIdResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(DefinedLocationByIdResolver.class);

    public static final String ID = "id";
    
    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public Location newLocationFromString(Map properties, String spec) {
        throw new UnsupportedOperationException("This class must have the RegistryLocationResolver.newLocationFromString method invoked");
    }
    
    @SuppressWarnings({ "rawtypes" })
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        String id = spec;
        if (spec.toLowerCase().startsWith(ID+":")) {
            id = spec.substring( (ID+":").length() );
        }
        LocationDefinition ld = registry.getDefinedLocationById(id);
        ld.getSpec();
        return ((BasicLocationRegistry)registry).resolveLocationDefinition(ld, locationFlags, null);
    }

    @Override
    public String getPrefix() {
        return ID;
    }
    
    /** accepts anything starting  id:xxx  or just   xxx where xxx is a defined location ID */
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        if (BasicLocationRegistry.isResolverPrefixForSpec(this, spec, false)) return true;
        if (registry.getDefinedLocationById(spec)!=null) return true;
        return false;
    }

}
