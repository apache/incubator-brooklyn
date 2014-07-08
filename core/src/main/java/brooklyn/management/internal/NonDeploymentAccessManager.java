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

import brooklyn.management.AccessController;


public class NonDeploymentAccessManager implements AccessManager {

    private final ManagementContextInternal initialManagementContext;
    
    public NonDeploymentAccessManager(ManagementContextInternal initialManagementContext) {
        this.initialManagementContext = initialManagementContext;
    }
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }

    @Override
    public AccessController getAccessController() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().getAccessController();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public boolean isLocationProvisioningAllowed() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().isLocationProvisioningAllowed();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public boolean isLocationManagementAllowed() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().isLocationManagementAllowed();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public boolean isEntityManagementAllowed() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getAccessManager().isEntityManagementAllowed();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void setLocationProvisioningAllowed(boolean allowed) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getAccessManager().setLocationProvisioningAllowed(allowed);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void setLocationManagementAllowed(boolean allowed) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getAccessManager().setLocationManagementAllowed(allowed);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void setEntityManagementAllowed(boolean allowed) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getAccessManager().setEntityManagementAllowed(allowed);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }
}
