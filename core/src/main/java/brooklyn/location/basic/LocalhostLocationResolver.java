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

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationResolver.EnableableLocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.KeyValueParser;

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
public class LocalhostLocationResolver implements LocationResolver, EnableableLocationResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalhostLocationResolver.class);
    
    public static final String LOCALHOST = "localhost";
    
    private static final Pattern PATTERN = Pattern.compile("("+LOCALHOST+"|"+LOCALHOST.toUpperCase()+")" + "(:\\((.*)\\))?$");
    
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
    public boolean isEnabled() {
        return LocationConfigUtils.isEnabled(managementContext, "brooklyn.location.localhost");
    }
    
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        Map globalProperties = registry.getProperties();
    
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());
        
        Matcher matcher = PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like localhost or localhost(name=abc)");
        }
        
        String argsPart = matcher.group(3);
        Map<String, String> argsMap = (argsPart != null) ? KeyValueParser.parseMap(argsPart) : Collections.<String,String>emptyMap();
        
        // If someone tries "localhost:(),localhost:()" as a single spec, we get weird key-values!
        for (String key : argsMap.keySet()) {
            if (key.contains(":") || key.contains("{") || key.contains("}") || key.contains("(") || key.contains(")")) {
                throw new IllegalArgumentException("Invalid localhost spec: "+spec);
            }
        }
        
        String namePart = argsMap.get("name");
        if (argsMap.containsKey("name") && (namePart == null || namePart.isEmpty())) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; if name supplied then value must be non-empty");
        }

        Map<String, Object> filteredProperties = new LocalhostPropertiesFromBrooklynProperties().getLocationProperties("localhost", namedLocation, globalProperties);
        // TODO filteredProperties stuff above should not be needed as named location items will already be passed in
        ConfigBag flags = ConfigBag.newInstance(argsMap).putIfAbsent(locationFlags).putIfAbsent(filteredProperties);
        
        flags.putStringKey("name", Maybe.fromNullable(namePart).or("localhost"));
        
        if (registry != null) 
            LocationPropertiesFromBrooklynProperties.setLocalTempDir(registry.getProperties(), flags);
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
            .configure(flags.getAllConfig())
            .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }

}
