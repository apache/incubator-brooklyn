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
package org.apache.brooklyn.entity.stock;

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.Locations;

import com.google.common.collect.ImmutableList;

/**
 * Provides a pass-through Startable entity used for keeping hierarchies tidy. 
 */
@ImplementedBy(BasicStartableImpl.class)
public interface BasicStartable extends Entity, Startable {

    /** @deprecated since 0.7.0; use {@link Locations#LocationFilter} */
    @Deprecated
    public interface LocationsFilter extends Locations.LocationsFilter {
        /** @deprecated since 0.7.0; use {@link Locations#USE_FIRST_LOCATION} */
        public static final LocationsFilter USE_FIRST_LOCATION = new LocationsFilter() {
            private static final long serialVersionUID = 3100091615409115890L;

            @Override
            public List<Location> filterForContext(List<Location> locations, Object context) {
                if (locations.size()<=1) return locations;
                return ImmutableList.of(locations.get(0));
            }
        };
    }

    public static final ConfigKey<Locations.LocationsFilter> LOCATIONS_FILTER = ConfigKeys.newConfigKey(Locations.LocationsFilter.class,
            "brooklyn.locationsFilter", "Provides a hook for customizing locations to be used for a given context");
}
