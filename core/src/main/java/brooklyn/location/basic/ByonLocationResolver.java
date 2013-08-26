package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.KeyValueParser;
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
public class ByonLocationResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(ByonLocationResolver.class);
    
    public static final String BYON = "byon";

    private static final Pattern PATTERN = Pattern.compile("("+BYON+"|"+BYON.toUpperCase()+")" + ":" + "\\((.*)\\)$");

    private static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("hosts", "name", "user");

    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    public FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(String spec) {
        return newLocationFromString(Maps.newLinkedHashMap(), spec);
    }

    @Override
    public FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(Map properties, String spec) {
        return newLocationFromString(spec, null, properties, new MutableMap());
    }
    
    @Override
    public FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
    }
    
    protected FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(String spec, brooklyn.location.LocationRegistry registry, Map properties, Map locationFlags) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like byon(hosts=\"addr1,addr2\")");
        }
        
        String argsPart = matcher.group(2);
        Map<String, String> argsMap = KeyValueParser.parseMap(argsPart);
        
        // prefer args map over location flags
        
        String namedLocation = (String) locationFlags.get("named");

        String name = argsMap.containsKey("name") ? argsMap.get("name") : (String)locationFlags.get("name");
        
        String hosts = argsMap.get("hosts");
        
        String user = (String)locationFlags.get("user");
        if (argsMap.containsKey("user")) user = argsMap.get("user");
        
        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (hosts == null || hosts.isEmpty()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; at least one host must be defined");
        }
        if (argsMap.containsKey("name") && (name == null || name.isEmpty())) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }
        
        List<String> hostAddresses = WildcardGlobs.getGlobsAfterBraceExpansion("{"+hosts+"}",
                true /* numeric */, /* no quote support though */ PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
        List<SshMachineLocation> machines = Lists.newArrayList();
        for (String host : hostAddresses) {
            SshMachineLocation machine;
            String userHere = user;
            String hostHere = host;
            if (host.contains("@")) {
                userHere = host.substring(0, host.indexOf("@"));
                hostHere = host.substring(host.indexOf("@")+1);
            }
            try {
                InetAddress.getByName(hostHere);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid host '"+hostHere+"' specified in '"+spec+"': "+e);
            }
            if (JavaGroovyEquivalents.groovyTruth(userHere)) {
                machine = managementContext.getLocationManager().createLocation(MutableMap.of("user", userHere.trim(), "address", hostHere.trim()), SshMachineLocation.class);    
            } else {
                machine = managementContext.getLocationManager().createLocation(MutableMap.of("address", hostHere.trim()), SshMachineLocation.class);
            }
            machines.add(machine);
        }
        
        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties().getLocationProperties("byon", namedLocation, properties);
        MutableMap<String, Object> flags = MutableMap.<String, Object>builder().putAll(filteredProperties).putAll(locationFlags).build();

        flags.put("machines", machines);
        if (user != null) flags.put("user", user);
        if (name != null) flags.put("name", name);
        if (registry != null) {
            String brooklynDataDir = (String) registry.getProperties().get(ConfigKeys.BROOKLYN_DATA_DIR.getName());
            if (brooklynDataDir != null && brooklynDataDir.length() > 0) {
                flags.put("localTempDir", new File(brooklynDataDir));
            }
        }

        log.debug("Created BYON location "+name+": "+machines);

        return managementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure(flags));
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
