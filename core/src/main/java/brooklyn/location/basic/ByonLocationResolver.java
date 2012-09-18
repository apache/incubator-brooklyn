package brooklyn.location.basic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.KeyValueParser;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>byon:(hosts=myhost)
 *     <li>byon:(hosts=myhost,myhost2)
 *     <li>byon:(hosts="myhost, myhost2")
 *     <li>byon:(hosts=myhost,myhost2, name=abc)
 *     <li>byon:(hosts="myhost, myhost2", name="my location name")
 *   </ul>
 * 
 * @author aled
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class ByonLocationResolver implements RegistryLocationResolver {

    public static final Logger log = LoggerFactory.getLogger(ByonLocationResolver.class);
    
    public static final String BYON = "byon";

    private static final Pattern PATTERN = Pattern.compile("("+BYON+"|"+BYON.toUpperCase()+")" + ":" + "\\((.*)\\)$");

    private static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("hosts", "name");

    public FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(String spec) {
        return newLocationFromString(Maps.newLinkedHashMap(), spec);
    }

    @Override
    public FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(Map properties, String spec) {
        return newLocationFromString(spec, null, properties, new MutableMap());
    }
    
    @Override
    public FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(String spec, LocationRegistry registry, Map locationFlags) {
        return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
    }
    
    protected FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(String spec, LocationRegistry registry, Map properties, Map locationFlags) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like byon(hosts=\"addr1,addr2\")");
        }
        
        String argsPart = matcher.group(2);
        Map<String, String> argsMap = KeyValueParser.parseMap(argsPart);
        String hostsPart = argsMap.get("hosts");
        String namePart = argsMap.get("name");
        
        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (hostsPart == null || hostsPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; at least one host must be defined");
        }
        if (argsMap.containsKey("name") && (namePart == null || namePart.isEmpty())) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }
        
        List<SshMachineLocation> machines = Lists.newArrayList();
        for (String host : hostsPart.split(",")) {
            machines.add(new SshMachineLocation(MutableMap.of("address", host.trim())));
        }
        
        Map<String,Object> flags = Maps.newLinkedHashMap();
        flags.putAll(locationFlags);
        flags.put("machines", machines);
        if (namePart != null) {
            flags.put("name", namePart);
        }
        
        return new FixedListMachineProvisioningLocation<SshMachineLocation>(flags);
    }
    
    @Override
    public String getPrefix() {
        return BYON;
    }
}
@SuppressWarnings("unchecked")