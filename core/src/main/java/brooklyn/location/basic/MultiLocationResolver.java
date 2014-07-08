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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.KeyValueParser;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.collect.Lists;

public class MultiLocationResolver implements LocationResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MultiLocationResolver.class);

    private static final String MULTI = "multi";
    
    private static final Pattern PATTERN = Pattern.compile("(" + MULTI + "|" + MULTI.toUpperCase() + ")" + ":" + "\\((.*)\\)$");
    
    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }

    @Override
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        // FIXME pass all flags into the location
        
        Map globalProperties = registry.getProperties();
        Map<String,?> locationArgs;
        if (spec.equalsIgnoreCase(MULTI)) {
            locationArgs = MutableMap.copyOf(locationFlags);
        } else {
            Matcher matcher = PATTERN.matcher(spec);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid location '" + spec + "'; must specify something like multi(targets=named:foo)");
            }
            String args = matcher.group(2);
            // TODO we are ignoring locationFlags after this (apart from named), looking only at these args
            locationArgs = KeyValueParser.parseMap(args);
        }
        String namedLocation = (String) locationFlags.get("named");

        Map<String, Object> filteredProperties = new LocationPropertiesFromBrooklynProperties().getLocationProperties(null, namedLocation, globalProperties);
        MutableMap<String, Object> flags = MutableMap.<String, Object>builder()
                .putAll(filteredProperties)
                .putAll(locationFlags)
                .removeAll("named")
                .putAll(locationArgs).build();
        
        if (locationArgs.get("targets") == null) {
            throw new IllegalArgumentException("target must be specified in single-machine spec");
        }
        
        // TODO do we need to pass location flags etc into the children to ensure they are inherited?
        List<Location> targets = Lists.newArrayList();
        Object targetSpecs = locationArgs.remove("targets");
        try {
            if (targetSpecs instanceof String) {
                for (String targetSpec : JavaStringEscapes.unwrapJsonishListIfPossible((String)targetSpecs)) {
                    targets.add(managementContext.getLocationRegistry().resolve(targetSpec));
                }
            } else if (targetSpecs instanceof Iterable) {
                for (Object targetSpec: (Iterable<?>)targetSpecs) {
                    if (targetSpec instanceof String) {
                        targets.add(managementContext.getLocationRegistry().resolve((String)targetSpec));
                    } else {
                        Set<?> keys = ((Map<?,?>)targetSpec).keySet();
                        if (keys.size()!=1) 
                            throw new IllegalArgumentException("targets supplied to MultiLocation must be a list of single-entry maps (got map of size "+keys.size()+": "+targetSpec+")");
                        Object key = keys.iterator().next();
                        Object flagsS = ((Map<?,?>)targetSpec).get(key);
                        targets.add(managementContext.getLocationRegistry().resolve((String)key, (Map<?,?>)flagsS));
                    }
                }
            } else throw new IllegalArgumentException("targets must be supplied to MultiLocation, either as string spec or list of single-entry maps each being a location spec");
            
            MultiLocation result = managementContext.getLocationManager().createLocation(LocationSpec.create(MultiLocation.class)
                    .configure(flags)
                    .configure("subLocations", targets)
                    .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));

            // TODO Important workaround for BasicLocationRegistry.resolveForPeeking.
            // That creates a location (from the resolver) and immediately unmanages it.
            // The unmanage *must* remove all the targets created here; otherwise we have a leak.
            // Adding the targets as children achieves this.
            for (Location target : targets) {
                target.setParent(result);
            }
            return result;

        } catch (Exception e) {
            // Must clean up after ourselves: don't leak sub-locations on error
            if (LOG.isDebugEnabled()) LOG.debug("Problem resolving MultiLocation; cleaning up any sub-locations and rethrowing: "+e);
            for (Location target : targets) {
                Locations.unmanage(target);
            }
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public String getPrefix() {
        return MULTI;
    }

    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
    }
    
}
