package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.MachineLocation;
import brooklyn.management.ManagementContext;

public class HostLocationResolver implements LocationResolver {
    
    private static final String HOST = "host";
    
    private static final Pattern PATTERN = Pattern.compile("(" + HOST + "|" + HOST.toUpperCase() + ")" + ":" + "\\((.+)\\)$");
    
    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Location newLocationFromString(Map properties, String spec) {
        return newLocationFromString(properties, spec, null);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '" + spec + "'; must specify something like host:(\"1.1.1.1\")");
        }
        String args = matcher.group(2);
        String target = "byon:(hosts=" + args + ")";
        String locationString = "target='" + target + "'";
        if (!managementContext.getLocationRegistry().canResolve(target)) {
            throw new IllegalArgumentException("Invalid target location '" + target + "'; must be resolvable location");
        }
        if (locationFlags == null) {
            locationFlags = Collections.EMPTY_MAP;
        }
        return new SingleMachineProvisioningLocation<MachineLocation>(locationString, locationFlags);
    }
    
    @Override
    public String getPrefix() {
        return HOST;
    }
    
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

}
