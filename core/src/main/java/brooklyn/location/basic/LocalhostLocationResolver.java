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

import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver.EnableableLocationResolver;
import brooklyn.util.config.ConfigBag;

/**
 * Examples of valid specs:
 *   <ul>
 *     <li>localhost
 *     <li>localhost()
 *     <li>localhost(name=abc)
 *     <li>localhost(name="abc")
 *   </ul>
 * 
 * @author alex, aled
 */
public class LocalhostLocationResolver extends AbstractLocationResolver implements EnableableLocationResolver {

    public static final String LOCALHOST = "localhost";

    @Override
    public String getPrefix() {
        return LOCALHOST;
    }

    @Override
    public boolean isEnabled() {
        return LocationConfigUtils.isEnabled(managementContext, "brooklyn.location.localhost");
    }

    @Override
    protected Class<? extends Location> getLocationType() {
        return LocalhostMachineProvisioningLocation.class;
    }

    @Override
    protected SpecParser getSpecParser() {
        return new AbstractLocationResolver.SpecParser(getPrefix()).setExampleUsage("\"localhost\" or \"localhost(displayName=abc)\"");
    }
    
    protected Map<String, Object> getFilteredLocationProperties(String provider, String namedLocation, Map<String, ?> globalProperties) {
        return new LocalhostPropertiesFromBrooklynProperties().getLocationProperties("localhost", namedLocation, globalProperties);
    }

    @Override
    protected ConfigBag extractConfig(Map<?,?> locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        ConfigBag config = super.extractConfig(locationFlags, spec, registry);
        config.putAsStringKeyIfAbsent("name", "localhost");
        return config;
    }
}
