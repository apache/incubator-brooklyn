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

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineLocation;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.util.guava.Maybe;

import com.google.common.collect.ImmutableList;

public class Locations {

    private static final Logger log = LoggerFactory.getLogger(Locations.class);

    public static final LocationsFilter USE_FIRST_LOCATION = new LocationsFilter() {
        private static final long serialVersionUID = 3100091615409115890L;

        @Override
        public List<Location> filterForContext(List<Location> locations, Object context) {
            if (locations.size()<=1) return locations;
            return ImmutableList.of(locations.get(0));
        }
    };

    public interface LocationsFilter extends Serializable {
        public List<Location> filterForContext(List<Location> locations, Object context);
    }
    
    /** as {@link Machines#findUniqueMachineLocation(Iterable)} */
    public static Maybe<MachineLocation> findUniqueMachineLocation(Iterable<? extends Location> locations) {
        return Machines.findUniqueMachineLocation(locations);
    }
    
    /** as {@link Machines#findUniqueSshMachineLocation(Iterable)} */
    public static Maybe<SshMachineLocation> findUniqueSshMachineLocation(Iterable<? extends Location> locations) {
        return Machines.findUniqueSshMachineLocation(locations);
    }

    /** if no locations are supplied, returns locations on the entity, or in the ancestors, until it finds a non-empty set,
     * or ultimately the empty set if no locations are anywhere */ 
    public static Collection<? extends Location> getLocationsCheckingAncestors(Collection<? extends Location> locations, Entity entity) {
        // look in ancestors if location not set here
        Entity ancestor = entity;
        while ((locations==null || locations.isEmpty()) && ancestor!=null) {
            locations = ancestor.getLocations();
            ancestor = ancestor.getParent();
        }
        return locations;
    }
    
    public static boolean isManaged(Location loc) {
        ManagementContext mgmt = ((LocationInternal)loc).getManagementContext();
        return (mgmt != null) && mgmt.isRunning() && mgmt.getLocationManager().isManaged(loc);
    }

    public static void unmanage(Location loc) {
        if (isManaged(loc)) {
            ManagementContext mgmt = ((LocationInternal)loc).getManagementContext();
            mgmt.getLocationManager().unmanage(loc);
        }
    }
    
    /**
     * Registers the given location (and all its children) with the management context. 
     * @throws IllegalStateException if the parent location is not already managed
     * 
     * @since 0.6.0 (added only for backwards compatibility, where locations are being created directly; previously in {@link Entities}).
     * @deprecated in 0.6.0; use {@link LocationManager#createLocation(LocationSpec)} instead.
     */
    public static void manage(Location loc, ManagementContext managementContext) {
        if (!managementContext.getLocationManager().isManaged(loc)) {
            log.warn("Deprecated use of unmanaged location ("+loc+"); will be managed automatically now but not supported in future versions");
            // FIXME this occurs MOST OF THE TIME e.g. including BrooklynLauncher.location(locationString)
            // not sure what is the recommend way to convert from locationString to locationSpec, or the API we want to expose;
            // deprecating some of the LocationRegistry methods seems sensible?
            log.debug("Stack trace for location of: Deprecated use of unmanaged location; will be managed automatically now but not supported in future versions", new Exception("TRACE for: Deprecated use of unmanaged location"));
            managementContext.getLocationManager().manage(loc);
        }
    }
}
