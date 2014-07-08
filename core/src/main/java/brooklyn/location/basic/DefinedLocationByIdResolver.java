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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.management.ManagementContext;

/**
 * looks up based on ID in DefinedLocations map
 */
public class DefinedLocationByIdResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(DefinedLocationByIdResolver.class);

    public static final String ID = "id";
    
    private volatile ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    @SuppressWarnings({ "rawtypes" })
    @Override
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        String id = spec;
        if (spec.toLowerCase().startsWith(ID+":")) {
            id = spec.substring( (ID+":").length() );
        }
        LocationDefinition ld = registry.getDefinedLocationById(id);
        ld.getSpec();
        return ((BasicLocationRegistry)registry).resolveLocationDefinition(ld, locationFlags, null);
    }

    @Override
    public String getPrefix() {
        return ID;
    }
    
    /** accepts anything starting  id:xxx  or just   xxx where xxx is a defined location ID */
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        if (BasicLocationRegistry.isResolverPrefixForSpec(this, spec, false)) return true;
        if (registry.getDefinedLocationById(spec)!=null) return true;
        return false;
    }

}
