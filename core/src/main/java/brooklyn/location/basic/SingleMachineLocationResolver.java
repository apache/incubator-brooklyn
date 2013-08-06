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
        if (!managementContext.getLocationRegistry().canResolve(args)) {
            throw new IllegalArgumentException("Invalid target location '" + args + "'; must be resolvable location");
        }
        return new SingleMachineProvisioningLocation<MachineLocation>(args);
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
