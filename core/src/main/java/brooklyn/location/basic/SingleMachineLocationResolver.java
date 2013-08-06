package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
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
    
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\[(.*)\\]$");
    
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^\\d*$");
    
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
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '" + spec + "'; must specify something like single(named:foo)");
        }
        String args = matcher.group(2);
        Map locationArgs = KeyValueParser.parseMap(args);
        String target = locationArgs.get("target").toString();
        locationArgs.remove("target");
        for (Object key : locationArgs.keySet()) {
            Object value = locationArgs.get(key); 
            if (value instanceof String) {
                Matcher listMatcher = LIST_PATTERN.matcher(value.toString());
                if (listMatcher.matches()) {
                    List<String> strings = KeyValueParser.parseList(listMatcher.group(1));
                    List<Integer> integers = new ArrayList<Integer>();
                    boolean intList = true;
                    for (String string : strings) {
                        if (INTEGER_PATTERN.matcher(string).matches()) {
                            integers.add(Integer.parseInt(string));
                        } else {
                            intList = false;
                            break;
                        }
                    }
                    locationArgs.put(key, intList ? integers : strings);
                }
            }
        }
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
