package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.KeyValueParser;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>localhost
 *     <li>localhost:()
 *     <li>localhost:(name=abc)
 *     <li>localhost:(name="abc")
 *   </ul>
 * 
 * @author alex, aled
 */
public class LocalhostResolver implements LocationResolver {
    
    public static final String LOCALHOST = "localhost";
    
    private static final Pattern PATTERN = Pattern.compile("("+LOCALHOST+"|"+LOCALHOST.toUpperCase()+")" + "(:\\((.*)\\))?$");

    private static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("name");

    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    @Override
    public String getPrefix() {
        return LOCALHOST;
    }

    @Override
    public Location newLocationFromString(Map properties, String spec) {
        return newLocationFromString(spec, null, properties, new MutableMap());
    }
    
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
    }
    
    protected Location newLocationFromString(String spec, brooklyn.location.LocationRegistry registry, Map properties, Map locationFlags) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like localhost or localhost(name=abc)");
        }
        
        String argsPart = matcher.group(3);
        Map<String, String> argsMap = (argsPart != null) ? KeyValueParser.parseMap(argsPart) : Collections.<String,String>emptyMap();
        String namePart = argsMap.get("name");
        
        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (argsMap.containsKey("name") && (namePart == null || namePart.isEmpty())) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }

        MutableMap<String,Object> flags = new MutableMap<String,Object>();
        // legacy syntax
        flags.addIfNotNull("privateKeyFile", properties.get("brooklyn.localhost.private-key-file"));
        flags.addIfNotNull("privateKeyPassphrase", properties.get("brooklyn.localhost.private-key-passphrase"));
        flags.addIfNotNull("publicKeyFile", properties.get("brooklyn.localhost.public-key-file"));
        // now prefer these names, for consistency (and sanity)
        flags.addIfNotNull("privateKeyFile", properties.get("brooklyn.localhost.privateKeyFile"));
        flags.addIfNotNull("privateKeyPassphrase", properties.get("brooklyn.localhost.privateKeyPassphrase"));
        flags.addIfNotNull("publicKeyFile", properties.get("brooklyn.localhost.publicKeyFile"));
        flags.add(locationFlags);
        if (namePart != null) {
            flags.put("name", namePart);
        }
        if (registry != null) {
            String brooklynDataDir = (String) registry.getProperties().get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
            if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
                flags.put("localTempDir", new File(brooklynDataDir));
            }
        }
        
        return managementContext.getLocationManager().createLocation(LocationSpec.spec(LocalhostMachineProvisioningLocation.class)
                .configure(flags));
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

}
