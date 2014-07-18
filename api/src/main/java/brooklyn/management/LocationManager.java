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
package brooklyn.management;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationSpec;

/**
 * For managing and querying entities.
 */
public interface LocationManager {

    /**
     * Creates a new location, which is tracked by the management context.
     * 
     * @param spec
     */
    <T extends Location> T createLocation(LocationSpec<T> spec);

    /**
     * Convenience (particularly for groovy code) to create a location.
     * Equivalent to {@code createLocation(LocationSpec.create(type).configure(config))}
     * 
     * @see createLocation(LocationSpec)
     */
    <T extends Location> T createLocation(Map<?,?> config, Class<T> type);

    /**
     * All locations under control of this management plane.
     * 
     * This returns a snapshot of the current locations; it will not reflect future changes in the locations.
     * If no locations are found, the collection will be empty (i.e. null is never returned).
     */
    Collection<Location> getLocations();

    /**
     * Returns the location under management (e.g. in use) with the given identifier 
     * (e.g. random string; and different to the LocationDefinition id).
     * May return a full instance, or a proxy to one which is remote.
     * If no location found with that id, returns null.
     */
    Location getLocation(String id);
    
    /** whether the location is under management by this management context */
    boolean isManaged(Location loc);

    /**
     * Begins management for the given location and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     * Manage a location.
     * 
     * @since 0.6.0 (added only for backwards compatibility, where locations are being created directly).
     * @deprecated in 0.6.0; use {@link #createLocation(LocationSpec)} instead.
     */
    Location manage(Location loc);
    
    /**
     * Causes the given location and its children, recursively, to be removed from the management plane
     * (for instance because the location is no longer relevant).
     * 
     * If the given location is not managed (e.g. it has already been unmanaged) then this is a no-op 
     * (though it may be logged so duplicate calls are best avoided).
     */
    void unmanage(Location loc);

}
