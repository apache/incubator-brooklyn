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

import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.mgmt.usage.ApplicationUsage;
import org.apache.brooklyn.core.mgmt.usage.LocationUsage;
import org.apache.brooklyn.core.mgmt.usage.UsageListener;
import org.apache.brooklyn.core.mgmt.usage.UsageManager;

import com.google.common.base.Predicate;


public class NonDeploymentUsageManager implements UsageManager {

    // TODO All the `isInitialManagementContextReal()` code-checks is a code-smell.
    // Expect we can delete a lot of this once we guarantee that all entities are 
    // instantiated via EntitySpec / EntityManager. Until then, we'll live with this.
    
    private final ManagementContextInternal initialManagementContext;
    
    public NonDeploymentUsageManager(ManagementContextInternal initialManagementContext) {
        this.initialManagementContext = initialManagementContext;
    }
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }

    @Override
    public void recordApplicationEvent(Application app, Lifecycle state) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getUsageManager().recordApplicationEvent(app, state);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void recordLocationEvent(Location loc, Lifecycle state) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getUsageManager().recordLocationEvent(loc, state);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public LocationUsage getLocationUsage(String locationId) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getLocationUsage(locationId);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public Set<LocationUsage> getLocationUsage(Predicate<? super LocationUsage> filter) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getLocationUsage(filter);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public ApplicationUsage getApplicationUsage(String appId) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getApplicationUsage(appId);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public Set<ApplicationUsage> getApplicationUsage(Predicate<? super ApplicationUsage> filter) {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getUsageManager().getApplicationUsage(filter);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void addUsageListener(UsageListener listener) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getUsageManager().addUsageListener(listener);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }

    @Override
    public void removeUsageListener(UsageListener listener) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.getUsageManager().removeUsageListener(listener);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation");
        }
    }
}
