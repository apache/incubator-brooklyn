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

import java.util.Set;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.Location;
import brooklyn.management.usage.ApplicationUsage;
import brooklyn.management.usage.LocationUsage;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;

@Beta
public interface UsageManager {

    /**
     * Adds this application event to the usage record for the given app (creating the usage 
     * record if one does not already exist).
     */
    void recordApplicationEvent(Application app, Lifecycle state);
    
    /**
     * Adds this location event to the usage record for the given location (creating the usage 
     * record if one does not already exist).
     */
    void recordLocationEvent(Location loc, Lifecycle state);

    /**
     * Returns the usage info for the location with the given id, or null if unknown.
     */
    LocationUsage getLocationUsage(String locationId);
    
    /**
     * Returns the usage info that matches the given predicate.
     * For example, could be used to find locations used within a given time period.
     */
    Set<LocationUsage> getLocationUsage(Predicate<? super LocationUsage> filter);
    
    /**
     * Returns the usage info for the application with the given id, or null if unknown.
     */
    ApplicationUsage getApplicationUsage(String appId);
    
    /**
     * Returns the usage info that matches the given predicate.
     * For example, could be used to find applications used within a given time period.
     */
    Set<ApplicationUsage> getApplicationUsage(Predicate<? super ApplicationUsage> filter);

}
