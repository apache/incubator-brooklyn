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
package org.apache.brooklyn.core.mgmt.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;

public class NonDeploymentLocationManager implements LocationManagerInternal {

    private final ManagementContext initialManagementContext;
    
    public NonDeploymentLocationManager(ManagementContext initialManagementContext) {
        this.initialManagementContext = initialManagementContext;
    }
    
    @Override
    public <T extends Location> T createLocation(LocationSpec<T> spec) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getLocationManager().createLocation(spec);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot create "+spec);
        }
    }

    @Override
    public <T extends Location> T createLocation(Map<?, ?> config, Class<T> type) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getLocationManager().createLocation(config, type);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot create "+type);
        }
    }
    
    @Override
    public Collection<Location> getLocations() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getLocationManager().getLocations();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Location getLocation(String id) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getLocationManager().getLocation(id);
        } else {
            return null;
        }
    }

    @Override
    public Iterable<String> getLocationIds() {
        if (isInitialManagementContextReal()) {
            return ((LocationManagerInternal)initialManagementContext.getLocationManager()).getLocationIds();
        } else {
            return Collections.emptyList();
        }
    }
    
    @Override
    public boolean isManaged(Location loc) {
        return false;
    }

    @Override
    public void manageRebindedRoot(Location loc) {
        if (isInitialManagementContextReal()) {
            ((LocationManagerInternal)initialManagementContext.getLocationManager()).manageRebindedRoot(loc);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot manage "+loc);
        }
    }

    @Override
    @Deprecated
    public Location manage(Location loc) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getLocationManager().manage(loc);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot manage "+loc);
        }
    }


    @Override
    public ManagementTransitionMode getLastManagementTransitionMode(String itemId) {
        if (isInitialManagementContextReal()) {
            return ((LocationManagerInternal)initialManagementContext.getLocationManager()).getLastManagementTransitionMode(itemId);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void setManagementTransitionMode(Location item, ManagementTransitionMode mode) {
        if (isInitialManagementContextReal()) {
            ((LocationManagerInternal)initialManagementContext.getLocationManager()).setManagementTransitionMode(item, mode);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void unmanage(Location item, ManagementTransitionMode info) {
        if (isInitialManagementContextReal()) {
            ((LocationManagerInternal)initialManagementContext.getLocationManager()).unmanage(item, info);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void unmanage(Location loc) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getLocationManager().unmanage(loc);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot unmanage "+loc);
        }
    }
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }
}
