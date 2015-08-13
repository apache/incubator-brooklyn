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
package org.apache.brooklyn.location.access;

import java.util.Map;

import org.apache.brooklyn.location.Location;
import org.apache.brooklyn.location.LocationRegistry;
import org.apache.brooklyn.location.LocationSpec;
import org.apache.brooklyn.location.basic.LocationConfigUtils;
import org.apache.brooklyn.location.basic.LocationInternal;
import org.apache.brooklyn.location.basic.LocationPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.location.basic.AbstractLocationResolver;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class PortForwardManagerLocationResolver extends AbstractLocationResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PortForwardManagerLocationResolver.class);

    public static final String PREFIX = "portForwardManager";

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        ConfigBag config = extractConfig(locationFlags, spec, registry);
        Map globalProperties = registry.getProperties();
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());
        String scope = config.get(PortForwardManager.SCOPE);

        Optional<Location> result = Iterables.tryFind(managementContext.getLocationManager().getLocations(), 
                Predicates.and(
                        Predicates.instanceOf(PortForwardManager.class), 
                        LocationPredicates.configEqualTo(PortForwardManager.SCOPE, scope)));
        
        if (result.isPresent()) {
            return result.get();
        } else {
            PortForwardManager loc = managementContext.getLocationManager().createLocation(LocationSpec.create(PortForwardManagerImpl.class)
                    .configure(config.getAllConfig())
                    .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));
            
            if (LOG.isDebugEnabled()) LOG.debug("Created "+loc+" for scope "+scope);
            return loc;
        }
    }

    @Override
    protected Class<? extends Location> getLocationType() {
        return PortForwardManager.class;
    }

    @Override
    protected SpecParser getSpecParser() {
        return new AbstractLocationResolver.SpecParser(getPrefix()).setExampleUsage("\"portForwardManager\" or \"portForwardManager(scope=global)\"");
    }
    
    @Override
    protected ConfigBag extractConfig(Map<?,?> locationFlags, String spec, LocationRegistry registry) {
        ConfigBag config = super.extractConfig(locationFlags, spec, registry);
        config.putAsStringKeyIfAbsent("name", "localhost");
        return config;
    }
}
