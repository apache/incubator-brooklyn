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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;

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
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
    }
    
    protected FixedListMachineProvisioningLocation<SshMachineLocation> newLocationFromString(String spec, brooklyn.location.LocationRegistry registry, Map properties, Map locationFlags) {
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '" + spec + "'; must specify something like host:(\"1.1.1.1\")");
        }
        
        String namedLocation = (String) locationFlags.get("named");

        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties().getLocationProperties(null, namedLocation, properties);
        MutableMap<String, Object> flags = MutableMap.<String, Object>builder().putAll(filteredProperties).putAll(locationFlags).removeAll("named").build();

        String args = matcher.group(2);
        String target = "byon:(hosts=" + args + ")";
        if (!managementContext.getLocationRegistry().canMaybeResolve(target)) {
            throw new IllegalArgumentException("Invalid target location '" + target + "'; must be resolvable location");
        }
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(SingleMachineProvisioningLocation.class)
                .configure("location", target)
                .configure("locationFlags", flags)
                .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, properties, namedLocation)));
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
