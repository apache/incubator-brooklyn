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
package brooklyn.management.internal;

import java.util.concurrent.atomic.AtomicReference;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.AccessController;
import brooklyn.management.AccessController.Response;

import com.google.common.annotations.Beta;

@Beta
public class LocalAccessManager implements AccessManager {

    private volatile boolean locationProvisioningAllowed = true;
    private volatile boolean locationManagementAllowed = true;
    private volatile boolean entityManagementAllowed = true;

    private final AtomicReference<AccessControllerImpl> controller = new AtomicReference<AccessControllerImpl>();

    public LocalAccessManager() {
        updateAccessController();
    }

    @Override
    public AccessController getAccessController() {
        return controller.get();
    }
    
    @Override
    public boolean isLocationProvisioningAllowed() {
        return locationProvisioningAllowed;
    }

    @Override
    public boolean isLocationManagementAllowed() {
        return locationManagementAllowed;
    }

    @Override
    public boolean isEntityManagementAllowed() {
        return entityManagementAllowed;
    }

    @Override
    public void setLocationProvisioningAllowed(boolean allowed) {
        locationProvisioningAllowed = allowed;
        updateAccessController();
    }

    @Override
    public void setLocationManagementAllowed(boolean allowed) {
        locationManagementAllowed = allowed;
        updateAccessController();
    }

    @Override
    public void setEntityManagementAllowed(boolean allowed) {
        entityManagementAllowed = allowed;
        updateAccessController();
    }

    private void updateAccessController() {
        controller.set(new AccessControllerImpl(locationProvisioningAllowed, locationManagementAllowed, entityManagementAllowed));
    }
    
    private static class AccessControllerImpl implements AccessController {
        private final boolean locationProvisioningAllowed;
        private final boolean locationManagementAllowed;
        private final boolean entityManagementAllowed;
        
        public AccessControllerImpl(boolean locationProvisioningAllowed, boolean locationManagementAllowed, 
                boolean entityManagementAllowed) {
            this.locationProvisioningAllowed = locationProvisioningAllowed;
            this.locationManagementAllowed = locationManagementAllowed;
            this.entityManagementAllowed = entityManagementAllowed;
        }

        @Override
        public Response canProvisionLocation(Location provisioner) {
            return (locationProvisioningAllowed ? Response.allowed() : Response.disallowed("location provisioning disabled"));
        }
        
        @Override
        public Response canManageLocation(Location loc) {
            return (locationManagementAllowed ? Response.allowed() : Response.disallowed("location management disabled"));
        }
        
        @Override
        public Response canManageEntity(Entity entity) {
            return (entityManagementAllowed ? Response.allowed() : Response.disallowed("entity management disabled"));
        }
    }
}
