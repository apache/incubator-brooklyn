/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.KeyValueParser;
import brooklyn.util.text.WildcardGlobs;
import brooklyn.util.text.WildcardGlobs.PhraseTreatment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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

    private static final Pattern PATTERN = Pattern.compile("(?i)" + BYON + "(?::\\((.*)\\))?$");

    private static final Set<String> ACCEPTABLE_ARGS = ImmutableSet.of("hosts", "name", "user");

    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
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
        
        String argsPart = matcher.group(1);
        Map<String, String> argsMap = KeyValueParser.parseMap(argsPart);
        
        // prefer args map over location flags
        
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());

        String name = argsMap.containsKey("name") ? argsMap.get("name") : (String)locationFlags.get("name");
        
        Object hosts = argsMap.containsKey("hosts") ? argsMap.get("hosts") : locationFlags.get("hosts");
        
        String user = argsMap.containsKey("user") ? argsMap.get("user") : (String)locationFlags.get("user");
        
        if (!ACCEPTABLE_ARGS.containsAll(argsMap.keySet())) {
            Set<String> illegalArgs = Sets.difference(argsMap.keySet(), ACCEPTABLE_ARGS);
            throw new IllegalArgumentException("Invalid location '"+spec+"'; illegal args "+illegalArgs+"; acceptable args are "+ACCEPTABLE_ARGS);
        }
        if (hosts == null || hosts.toString().isEmpty()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; at least one host must be defined");
        }
        if (argsMap.containsKey("name") && (name == null || name.isEmpty())) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }
        
        List<String> hostAddresses;
        
        if (hosts instanceof String) {
            hostAddresses = WildcardGlobs.getGlobsAfterBraceExpansion("{"+hosts+"}",
                    true /* numeric */, /* no quote support though */ PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
        } else if (hosts instanceof List) {
            hostAddresses = ImmutableList.copyOf((List)hosts);
        } else {
            throw new IllegalStateException("Illegal parameter for 'hosts'; must be a string or map (but got " + hosts + ")");
        }
        
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
                InetAddress.getByName(hostHere.trim());
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
        ConfigBag flags = ConfigBag.newInstance(locationFlags).putIfAbsent(filteredProperties);

        flags.putStringKey("machines", machines);
        flags.putIfNotNull(LocationConfigKeys.USER, user);
        flags.putStringKeyIfNotNull("name", name);
        
        if (registry != null) 
            LocationPropertiesFromBrooklynProperties.setLocalTempDir(registry.getProperties(), flags);

        log.debug("Created BYON location "+name+": "+machines);

        return managementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure(flags.getAllConfigRaw())
                .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, properties, namedLocation)));
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
