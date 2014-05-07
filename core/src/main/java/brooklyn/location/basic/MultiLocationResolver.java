package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
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
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '" + spec + "'; must specify something like multi(targets=named:foo)");
        }
        
        String namedLocation = (String) locationFlags.get("named");
        String args = matcher.group(2);
        Map<String,?> locationArgs = KeyValueParser.parseMap(args);

        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties().getLocationProperties(null, namedLocation, globalProperties);
        MutableMap<String, Object> flags = MutableMap.<String, Object>builder()
                .putAll(filteredProperties)
                .putAll(locationFlags)
                .removeAll("named")
                .putAll(locationArgs).build();
        
        if (locationArgs.get("targets") == null) {
            throw new IllegalArgumentException("target must be specified in single-machine spec");
        }
        String targetSpecs = locationArgs.get("targets").toString();
        locationArgs.remove("targets");
        
        List<Location> targets = Lists.newArrayList();
        for (String targetSpec : JavaStringEscapes.unwrapJsonishListIfPossible(targetSpecs)) {
            targets.add(managementContext.getLocationRegistry().resolve(targetSpec));
        }
        
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
