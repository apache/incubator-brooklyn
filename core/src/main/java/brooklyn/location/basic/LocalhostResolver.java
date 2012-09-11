package brooklyn.location.basic;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.util.KeyValueParser;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>localhost
 *     <li>localhost()
 *     <li>localhost(name=abc)
 *     <li>localhost(name="abc")
 *   </ul>
 * 
 * @author alex, aled
 */
public class LocalhostResolver implements LocationResolver {
    
    public static final String LOCALHOST = "localhost";
    
    private static final Pattern PATTERN = Pattern.compile("("+LOCALHOST+"|"+LOCALHOST.toUpperCase()+")" + "(\\((.*)\\))?$");

    private static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("name");

    @Override
    public Location newLocationFromString(Map properties, String spec) {
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

        Map<String,Object> flags = Maps.newLinkedHashMap();
        if (namePart != null) {
            flags.put("name", namePart);
        }
        
        return new LocalhostMachineProvisioningLocation(flags);
    }
    
    @Override
    public String getPrefix() {
        return LOCALHOST;
    }
}
