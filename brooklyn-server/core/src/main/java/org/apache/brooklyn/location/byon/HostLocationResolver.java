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
package org.apache.brooklyn.location.byon;

import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.location.AbstractLocationResolver;
import org.apache.brooklyn.core.location.LocationConfigUtils;
import org.apache.brooklyn.core.location.LocationPropertiesFromBrooklynProperties;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.guava.Maybe.Absent;
import org.apache.brooklyn.util.text.KeyValueParser;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class HostLocationResolver extends AbstractLocationResolver {
    
    private static final String HOST = "host";
    
    @SuppressWarnings("rawtypes")
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        // Extract args from spec
        ParsedSpec parsedSpec = specParser.parse(spec);
        Map<String, String> argsMap = parsedSpec.argsMap;
        if (argsMap.isEmpty()) {
            throw new IllegalArgumentException("Invalid host spec (no host supplied): "+spec);
        } else if (argsMap.size() == 1 && Iterables.get(argsMap.values(), 0) == null) {
            // only given ip or hostname
            argsMap = ImmutableMap.of("hosts", Iterables.get(argsMap.keySet(), 0));
        } else if (!(argsMap.containsKey("host") || argsMap.containsKey("hosts"))) {
            throw new IllegalArgumentException("Invalid host spec (no host supplied): "+spec);
        }

        // Find generic applicable properties
        Map globalProperties = registry.getProperties();
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());
        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties().getLocationProperties(null, namedLocation, globalProperties);
        ConfigBag flags = ConfigBag.newInstance(locationFlags).putIfAbsent(filteredProperties);
        flags.remove(LocationInternal.NAMED_SPEC_NAME);

        // Generate target spec
        String target = "byon("+KeyValueParser.toLine(argsMap)+")";
        Maybe<Location> testResolve = managementContext.getLocationRegistry().resolve(target, false, null);
        if (!testResolve.isPresent()) {
            throw new IllegalArgumentException("Invalid target location '" + target + "' for location '"+HOST+"': "+
                Exceptions.collapseText( ((Absent<?>)testResolve).getException() ), ((Absent<?>)testResolve).getException());
        }
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(SingleMachineProvisioningLocation.class)
                .configure("location", target)
                .configure("locationFlags", flags.getAllConfig())
                .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));
    }
    
    @Override
    public String getPrefix() {
        return HOST;
    }
    
    @Override
    protected Class<? extends Location> getLocationType() {
        return SingleMachineProvisioningLocation.class;
    }

    @Override
    protected SpecParser getSpecParser() {
        return new SpecParser(getPrefix()).setExampleUsage("\"host(1.1.1.1)\" or \"host(host=1.1.1.1,name=myname)\"");
    }
}
