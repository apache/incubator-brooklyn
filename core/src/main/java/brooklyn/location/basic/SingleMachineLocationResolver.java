package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.MachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.util.text.KeyValueParser;

public class SingleMachineLocationResolver implements LocationResolver {
    
    private static final String SINGLE = "single";
    
    private static final Pattern PATTERN = Pattern.compile("(" + SINGLE + "|" + SINGLE.toUpperCase() + ")" + ":" + "\\((.*)\\)$");
    
    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Location newLocationFromString(Map properties, String spec) {
        return newLocationFromString(properties, spec, null);
    }

    @Override
    @SuppressWarnings({ "rawtypes" })
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '" + spec + "'; must specify something like single(named:foo)");
        }
        String args = matcher.group(2);
        Map locationArgs = KeyValueParser.parseMap(args);
        if (locationArgs == null || locationArgs.get("target") == null) {
            throw new IllegalArgumentException("target must be specified in spec");
        }
        String target = locationArgs.get("target").toString();
        locationArgs.remove("target");
        if (!managementContext.getLocationRegistry().canResolve(target)) {
            throw new IllegalArgumentException("Invalid target location '" + target + "'; must be resolvable location");
        }
        return new SingleMachineProvisioningLocation<MachineLocation>(target, locationArgs);
    }

    @Override
    public String getPrefix() {
        return SINGLE;
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }
    
}
