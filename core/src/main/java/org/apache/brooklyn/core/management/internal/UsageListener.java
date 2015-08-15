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
package org.apache.brooklyn.core.management.internal;

import java.util.Map;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.management.usage.ApplicationUsage.ApplicationEvent;
import org.apache.brooklyn.core.management.usage.LocationUsage.LocationEvent;

import com.google.common.annotations.Beta;

@Beta
public interface UsageListener {
    
    /**
     * A no-op implementation of {@link UsageListener}, for users to extend.
     * 
     * Users are encouraged to extend this class, which will shield the user 
     * from the addition of other usage event methods being added. If additional
     * methods are added in a future release, a no-op implementation will be
     * added to this class.
     */
    @Beta
    public static class BasicUsageListener implements UsageListener {
        @Override
        public void onApplicationEvent(ApplicationMetadata app, ApplicationEvent event) {
        }
        
        @Override public void onLocationEvent(LocationMetadata loc, LocationEvent event) {
        }
    }
    
    /**
     * Users should never implement this interface directly; methods may be added in future releases
     * without notice.
     */
    @Beta
    public interface ApplicationMetadata {
        /**
         * Access the application directly with caution: by the time the listener fires, 
         * the application may no longer be managed.
         */
        @Beta
        Application getApplication();
        
        String getApplicationId();
        
        String getApplicationName();
        
        String getEntityType();
        
        String getCatalogItemId();
        
        Map<String, String> getMetadata();
    }
    
    /**
     * Users should never implement this interface directly; methods may be added in future releases
     * without notice.
     */
    @Beta
    public interface LocationMetadata {
        /**
         * Access the location directly with caution: by the time the listener fires, 
         * the location may no longer be managed.
         */
        @Beta
        Location getLocation();
        
        String getLocationId();
        
        Map<String, String> getMetadata();
    }
    
    public static final UsageListener NOOP = new UsageListener() {
        @Override public void onApplicationEvent(ApplicationMetadata app, ApplicationEvent event) {}
        @Override public void onLocationEvent(LocationMetadata loc, LocationEvent event) {}
    };
    
    @Beta
    void onApplicationEvent(ApplicationMetadata app, ApplicationEvent event);
    
    @Beta
    void onLocationEvent(LocationMetadata loc, LocationEvent event);
}
