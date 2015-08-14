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
package org.apache.brooklyn.location.basic;

import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.location.LocationSpec;

import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.guava.Maybe.Absent;

public class SingleMachineLocationResolver extends AbstractLocationResolver {
    
    private static final String SINGLE = "single";
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
        ConfigBag config = extractConfig(locationFlags, spec, registry);
        Map globalProperties = registry.getProperties();
        String namedLocation = (String) locationFlags.get(LocationInternal.NAMED_SPEC_NAME.getName());
        
        if (registry != null) {
            LocationPropertiesFromBrooklynProperties.setLocalTempDir(globalProperties, config);
        }

        if (config.getStringKey("target") == null) {
            throw new IllegalArgumentException("target must be specified in single-machine spec");
        }
        String target = config.getStringKey("target").toString();
        config.remove("target");
        Maybe<Location> testResolve = managementContext.getLocationRegistry().resolve(target, false, null);
        if (!testResolve.isPresent()) {
            throw new IllegalArgumentException("Invalid target location '" + target + "' for location '"+SINGLE+"': "+
                Exceptions.collapseText( ((Absent<?>)testResolve).getException() ));
        }
        
        return managementContext.getLocationManager().createLocation(LocationSpec.create(SingleMachineProvisioningLocation.class)
                .configure("location", target)
                .configure("locationFlags", config.getAllConfig())
                .configure(LocationConfigUtils.finalAndOriginalSpecs(spec, locationFlags, globalProperties, namedLocation)));
    }
    
    @Override
    public String getPrefix() {
        return SINGLE;
    }
    
    @Override
    protected Class<? extends Location> getLocationType() {
        return SingleMachineProvisioningLocation.class;
    }

    @Override
    protected SpecParser getSpecParser() {
        return new SpecParser(getPrefix()).setExampleUsage("\"single(target=jclouds:aws-ec2:us-east-1)\"");
    }
}
