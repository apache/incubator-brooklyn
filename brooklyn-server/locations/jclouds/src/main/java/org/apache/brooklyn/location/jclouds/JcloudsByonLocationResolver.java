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
package org.apache.brooklyn.location.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationResolver;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.LocationConfigUtils;
import org.apache.brooklyn.core.location.LocationPropertiesFromBrooklynProperties;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.KeyValueParser;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.text.WildcardGlobs;
import org.apache.brooklyn.util.text.WildcardGlobs.PhraseTreatment;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>byon:(hosts=myhost)
 *     <li>byon:(hosts="myhost, myhost2")
 *     <li>byon:(hosts="myhost, myhost2", name="my location name")
 *   </ul>
 * 
 * @author aled
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class JcloudsByonLocationResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(JcloudsByonLocationResolver.class);
    
    public static final String BYON = "jcloudsByon";

    private static final Pattern PATTERN = Pattern.compile("("+BYON+"|"+BYON.toUpperCase()+")" + ":" + "\\((.*)\\)$");

    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    // TODO Remove some duplication from JcloudsResolver; needs more careful review
    @Override
    public FixedListMachineProvisioningLocation<JcloudsSshMachineLocation> newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        Map globalProperties = registry.getProperties();

        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like jcloudsByon(provider=\"aws-ec2\",region=\"us-east-1\",hosts=\"i-f2014593,i-d1234567\")");
        }
        
        String argsPart = matcher.group(2);
        Map<String, String> argsMap = KeyValueParser.parseMap(argsPart);
        
        // prefer args map over location flags
        
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());

        String providerOrApi = argsMap.containsKey("provider") ? argsMap.get("provider") : (String)locationFlags.get("provider");

        String regionName = argsMap.containsKey("region") ? argsMap.get("region") : (String)locationFlags.get("region");
        
        String endpoint = argsMap.containsKey("endpoint") ? argsMap.get("endpoint") : (String)locationFlags.get("endpoint");
        
        String name = argsMap.containsKey("name") ? argsMap.get("name") : (String)locationFlags.get("name");

        String user = argsMap.containsKey("user") ? argsMap.get("user") : (String)locationFlags.get("user");

        String privateKeyFile = argsMap.containsKey("privateKeyFile") ? argsMap.get("privateKeyFile") : (String)locationFlags.get("privateKeyFile");
        
        String hosts = argsMap.get("hosts");
        
        if (Strings.isEmpty(providerOrApi)) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; provider must be defined");
        }
        if (hosts == null || hosts.isEmpty()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; at least one host must be defined");
        }
        if (argsMap.containsKey("name") && (Strings.isEmpty(name))) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }

        // For everything in brooklyn.properties, only use things with correct prefix (and remove that prefix).
        // But for everything passed in via locationFlags, pass those as-is.
        // TODO Should revisit the locationFlags: where are these actually used? Reason accepting properties without
        //      full prefix is that the map's context is explicitly this location, rather than being generic properties.
        Map allProperties = getAllProperties(registry, globalProperties);
        Map jcloudsProperties = new JcloudsPropertiesFromBrooklynProperties().getJcloudsProperties(providerOrApi, regionName, namedLocation, allProperties);
        jcloudsProperties.putAll(locationFlags);
        jcloudsProperties.putAll(argsMap);
        
        String jcloudsSpec = "jclouds:"+providerOrApi + (regionName != null ? ":"+regionName : "") + (endpoint != null ? ":"+endpoint : "");
        JcloudsLocation jcloudsLocation = (JcloudsLocation) registry.resolve(jcloudsSpec, jcloudsProperties);

        List<String> hostIdentifiers = WildcardGlobs.getGlobsAfterBraceExpansion("{"+hosts+"}",
                true /* numeric */, /* no quote support though */ PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
        List<JcloudsSshMachineLocation> machines = Lists.newArrayList();
        
        for (String hostIdentifier : hostIdentifiers) {
            Map<?, ?> machineFlags = MutableMap.builder()
                    .put("id", hostIdentifier)
                    .putIfNotNull("user", user)
                    .putIfNotNull("privateKeyFile", privateKeyFile)
                    .build();
            try {
                JcloudsSshMachineLocation machine = jcloudsLocation.rebindMachine(jcloudsLocation.config().getBag().putAll(machineFlags));
                machines.add(machine);
            } catch (NoMachinesAvailableException e) {
                log.warn("Error rebinding to jclouds machine "+hostIdentifier+" in "+jcloudsLocation, e);
                Exceptions.propagate(e);
            }
        }
        
        ConfigBag flags = ConfigBag.newInstance(jcloudsProperties);

        flags.putStringKey("machines", machines);
        flags.putIfNotNull(LocationConfigKeys.USER, user);
        flags.putStringKeyIfNotNull("name", name);
        
        if (registry != null) 
            LocationPropertiesFromBrooklynProperties.setLocalTempDir(registry.getProperties(), flags);

        log.debug("Created Jclouds BYON location "+name+": "+machines);
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure(flags.getAllConfig())
                .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));
    }
    
    private Map getAllProperties(LocationRegistry registry, Map<?,?> properties) {
        Map<Object,Object> allProperties = Maps.newHashMap();
        if (registry!=null) allProperties.putAll(registry.getProperties());
        allProperties.putAll(properties);
        return allProperties;
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