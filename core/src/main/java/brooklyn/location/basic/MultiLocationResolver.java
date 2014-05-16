package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.KeyValueParser;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.collect.Lists;

public class MultiLocationResolver implements LocationResolver {
    
    private static final String MULTI = "multi";
    
    private static final Pattern PATTERN = Pattern.compile("(" + MULTI + "|" + MULTI.toUpperCase() + ")" + ":" + "\\((.*)\\)$");
    
    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        // FIXME pass all flags into the location
        
        Map globalProperties = registry.getProperties();
        Map<String,?> locationArgs;
        if (spec.equalsIgnoreCase(MULTI)) {
            locationArgs = MutableMap.copyOf(locationFlags);
        } else {
            Matcher matcher = PATTERN.matcher(spec);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid location '" + spec + "'; must specify something like multi(targets=named:foo)");
            }
            String args = matcher.group(2);
            // TODO we are ignoring locationFlags after this (apart from named), looking only at these args
            locationArgs = KeyValueParser.parseMap(args);
        }
        String namedLocation = (String) locationFlags.get("named");

        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties().getLocationProperties(null, namedLocation, globalProperties);
        MutableMap<String, Object> flags = MutableMap.<String, Object>builder()
                .putAll(filteredProperties)
                .putAll(locationFlags)
                .removeAll("named")
                .putAll(locationArgs).build();
        
        if (locationArgs.get("targets") == null) {
            throw new IllegalArgumentException("target must be specified in single-machine spec");
        }
        
        // TODO do we need to pass location flags etc into the children to ensure they are inherited?
        List<Location> targets = Lists.newArrayList();
        Object targetSpecs = locationArgs.remove("targets");
        if (targetSpecs instanceof String) {
            for (String targetSpec : JavaStringEscapes.unwrapJsonishListIfPossible((String)targetSpecs)) {
                targets.add(managementContext.getLocationRegistry().resolve(targetSpec));
            }
        } else if (targetSpecs instanceof Iterable) {
            for (Object targetSpec: (Iterable<?>)targetSpecs) {
                if (targetSpec instanceof String) {
                    targets.add(managementContext.getLocationRegistry().resolve((String)targetSpec));
                } else {
                    Set<?> keys = ((Map<?,?>)targetSpec).keySet();
                    if (keys.size()!=1) 
                        throw new IllegalArgumentException("targets supplied to MultiLocation must be a list of single-entry maps (got map of size "+keys.size()+": "+targetSpec+")");
                    Object key = keys.iterator().next();
                    Object flagsS = ((Map<?,?>)targetSpec).get(key);
                    targets.add(managementContext.getLocationRegistry().resolve((String)key, (Map<?,?>)flagsS));
                }
            }
        } else throw new IllegalArgumentException("targets must be supplied to MultiLocation, either as string spec or list of single-entry maps each being a location spec");
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(MultiLocation.class)
                .configure(flags)
                .configure("subLocations", targets)
                .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));
    }

    @Override
    public String getPrefix() {
        return MULTI;
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }
    
}
