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
package brooklyn.location;

import java.util.Map;

import com.google.common.annotations.Beta;

import brooklyn.management.ManagementContext;

/**
 * Provides a way of creating location instances of a particular type.
 */
public interface LocationResolver {

    void init(ManagementContext managementContext);
    
    /** the prefix that this resolver will attend to */
    String getPrefix();
    
    /** whether the spec is something which should be passed to this resolver */
    boolean accepts(String spec, brooklyn.location.LocationRegistry registry);

    /**
     * Similar to {@link #newLocationFromString(Map, String)} 
     * but passing in a reference to the registry itself (from which the base properties are discovered)
     * and including flags (e.g. user, key, cloud credential) which are known to be for this location.
     * <p>
     * introduced to support locations which refer to other locations, e.g. NamedLocationResolver  
     **/ 
    @SuppressWarnings("rawtypes")
    Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry);

    /** @since 0.7.0 exploring this as a mechanism to disable locations */
    @Beta
    public interface EnableableLocationResolver extends LocationResolver {
        /** whether the location is enabled */
        boolean isEnabled();
    }
    
}
