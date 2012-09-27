package brooklyn.location.basic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.util.MutableMap;
import brooklyn.util.text.QuotedStringTokenizer;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

public class LocationRegistry {

    public static final Logger log = LoggerFactory.getLogger(LocationRegistry.class);
    
    private final Map properties;
    
    public LocationRegistry() {
        this(BrooklynProperties.Factory.newDefault());
    }
    public LocationRegistry(Map properties) {
        this.properties = properties;
        findServices();
    }
    
    protected Map<String,LocationResolver> resolvers = new LinkedHashMap<String, LocationResolver>();
    
    protected void findServices() {
        ServiceLoader<LocationResolver> loader = ServiceLoader.load(LocationResolver.class);
        for (LocationResolver r: loader)
            resolvers.put(r.getPrefix(), r);
    }

    public Location resolve(String spec) {
        return resolve(spec, new MutableMap());
    }
    public Location resolve(String spec, Map locationFlags) {
        int colon = spec.indexOf(':');
        String prefix = colon>=0 ? spec.substring(0, colon) : spec;
        LocationResolver resolver = resolvers.get(prefix);
        
        if (resolver == null) {
            //default to jclouds; could do a lookup to ensure provider supported by jclouds
            resolver = resolvers.get("jclouds");
        }
        
        if (resolver instanceof RegistryLocationResolver) {
            return ((RegistryLocationResolver)resolver).newLocationFromString(spec, this, locationFlags);
        } else if (resolver != null) {
            if (!locationFlags.isEmpty())
                log.warn("Ignoring location flags "+locationFlags+" when instantiating "+spec);
            return resolver.newLocationFromString(properties, spec);
        }
        
        throw new NoSuchElementException("No resolver found for '"+prefix+"' when trying to find location "+spec);
    }
    
    /**
     * Expects a collection of strings being the spec for locations, returns a list of locations.
     * Also allows single elements which are comma-separated lists of locations.
     * <p>
     * For legacy compatibility this also accepts nested lists, but that is deprecated
     * (and triggers a warning).
     */
    public List<Location> getLocationsById(Iterable<?> ids) {
        List<Location> result = new ArrayList<Location>();
        for (Object id : ids) {
            if (id instanceof String) {
                // if it as comma-separated list -- TODO with no comma in the brackets
                List<String> l = expandCommaSeparateLocationList((String)id);
                if (l.size()>1) id = l;
            } else if (id instanceof Iterable) {
                log.warn("LocationRegistry got list of list of location strings, "+ids+"; flattening");
            }
            if (id instanceof String) {
                result.add(resolve((String) id));
            } else if (id instanceof Iterable) {
                result.addAll(getLocationsById((Iterable<?>) id));
            } else if (id instanceof Location) {
                result.add((Location) id);
            } else {
                throw new IllegalArgumentException("Cannot resolve '"+id+"' to a location; unsupported type "+
                        (id == null ? "null" : id.getClass().getName())); 
            }
        }
        return result;
    }

    private List<String> expandCommaSeparateLocationList(String id) {
        return WildcardGlobs.getGlobsAfterBraceExpansion("{"+id+"}", false, PhraseTreatment.INTERIOR_NOT_EXPANDABLE, PhraseTreatment.INTERIOR_NOT_EXPANDABLE);
        // don't do this, it tries to expand commas inside parentheses which is not good!
//        QuotedStringTokenizer.builder().addDelimiterChars(",").tokenizeAll((String)id);
    }
    
    public Map getProperties() {
        return properties;
    }
}
