package brooklyn.location.jclouds;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.LocationRegistry;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.RegistryLocationResolver;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.KeyValueParser;
import brooklyn.util.text.Strings;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

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
public class JcloudsByonLocationResolver implements RegistryLocationResolver {

    public static final Logger log = LoggerFactory.getLogger(JcloudsByonLocationResolver.class);
    
    public static final String BYON = "jcloudsByon";

    private static final Pattern PATTERN = Pattern.compile("("+BYON+"|"+BYON.toUpperCase()+")" + ":" + "\\((.*)\\)$");

    private static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("provider", "region", "hosts", "name", "user");

    public FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> newLocationFromString(String spec) {
        return newLocationFromString(Maps.newLinkedHashMap(), spec);
    }

    @Override
    public FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> newLocationFromString(Map properties, String spec) {
        return newLocationFromString(spec, null, properties, new MutableMap());
    }
    
    @Override
    public FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
    }
    
    protected FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> newLocationFromString(String spec, brooklyn.location.LocationRegistry registry, Map properties, Map locationFlags) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like jcloudsByon(provider=\"aws-ec2\",region=\"us-east-1\",hosts=\"i-f2014593,i-d1234567\")");
        }
        
        String argsPart = matcher.group(2);
        Map<String, String> argsMap = KeyValueParser.parseMap(argsPart);
        
        // prefer args map over location flags
        
        String provider = argsMap.containsKey("provider") ? argsMap.get("provider") : (String)locationFlags.get("provider");

        String region = argsMap.containsKey("region") ? argsMap.get("region") : (String)locationFlags.get("region");
        
        String name = argsMap.containsKey("name") ? argsMap.get("name") : (String)locationFlags.get("name");

        String user = argsMap.containsKey("user") ? argsMap.get("user") : (String)locationFlags.get("user");

        String hosts = argsMap.get("hosts");
        
        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (Strings.isEmpty(provider)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; provider must be defined");
        }
        if (hosts == null || hosts.isEmpty()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; at least one host must be defined");
        }
        if (argsMap.containsKey("name") && (Strings.isEmpty(name))) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }

        JcloudsLocation jcloudsLocation = (JcloudsLocation) registry.resolve("jclouds:"+provider+(region != null ? ":"+region : ""), locationFlags);

        List<String> hostIdentifiers = WildcardGlobs.getGlobsAfterBraceExpansion("{"+hosts+"}",
                true /* numeric */, /* no quote support though */ PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
        List<JcloudsSshMachineLocation> machines = Lists.newArrayList();
        
        for (String hostIdentifier : hostIdentifiers) {
            
            Map<?, ?> machineFlags = MutableMap.builder()
                    .put("id", hostIdentifier) 
                    //.put("hostname", "162.13.8.61") 
                    .putIfNotNull("user", user)
                    .put(JcloudsLocation.PUBLIC_KEY_FILE.getName(), "/Users/aled/.ssh/id_rsa")
                    .build();
            try {
                JcloudsSshMachineLocation machine = jcloudsLocation.rebindMachine(jcloudsLocation.getConfigBag().putAll(machineFlags));
                machine.setParentLocation(null);
                machines.add(machine);
            } catch (NoMachinesAvailableException e) {
                log.warn("Error rebinding to jclouds machine "+hostIdentifier+" in "+jcloudsLocation, e);
                Exceptions.propagate(e);
            }
        }
        
        Map<String,Object> flags = Maps.newLinkedHashMap();
        flags.putAll(locationFlags);
        flags.put("machines", machines);
        if (user != null) flags.put("user", user);
        if (name != null) flags.put("name", name);
        if (registry != null) {
            String brooklynDataDir = (String) registry.getProperties().get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
            if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
                flags.put("localTempDir", new File(brooklynDataDir));
            }
        }

        log.debug("Created Jclouds BYON location "+name+": "+machines);
        
        return new FixedListMachineProvisioningLocation<JcloudsSshMachineLocation>(flags);
    }
    
    @Override
    public String getPrefix() {
        return BYON;
    }
    
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }
}
