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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.management.usage.ApplicationUsage;
import org.apache.brooklyn.core.management.usage.LocationUsage;
import org.apache.brooklyn.core.management.usage.ApplicationUsage.ApplicationEvent;
import org.apache.brooklyn.core.management.usage.LocationUsage.LocationEvent;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

@Beta
public interface UsageManager {

    @SuppressWarnings("serial")
    public static final ConfigKey<List<org.apache.brooklyn.core.management.internal.UsageListener>> USAGE_LISTENERS = ConfigKeys.newConfigKey(
            new TypeToken<List<org.apache.brooklyn.core.management.internal.UsageListener>>() {},
            "brooklyn.usageManager.listeners", "Optional usage listeners (i.e. for metering)",
            ImmutableList.<org.apache.brooklyn.core.management.internal.UsageListener>of());
    
    public static final ConfigKey<Duration> USAGE_LISTENER_TERMINATION_TIMEOUT = ConfigKeys.newConfigKey(
            Duration.class,
            "brooklyn.usageManager.listeners.timeout",
            "Timeout on termination, to wait for queue of usage listener events to be processed",
            Duration.TEN_SECONDS);

    /**
     * @since 0.7.0
     * @deprecated since 0.7.0; use {@link org.apache.brooklyn.core.management.internal.UsageListener}; see {@link UsageListenerAdapter} 
     */
    public interface UsageListener {
        public static final UsageListener NOOP = new UsageListener() {
            @Override public void onApplicationEvent(String applicationId, String applicationName, String entityType, 
                    String catalogItemId, Map<String, String> metadata, ApplicationEvent event) {}
            @Override public void onLocationEvent(String locationId, Map<String, String> metadata, LocationEvent event) {}
        };
        
        public static class UsageListenerAdapter implements org.apache.brooklyn.core.management.internal.UsageListener {
            private final UsageListener listener;

            public UsageListenerAdapter(UsageListener listener) {
                this.listener = checkNotNull(listener, "listener");
            }
            
            @Override
            public void onApplicationEvent(ApplicationMetadata app, ApplicationEvent event) {
                listener.onApplicationEvent(app.getApplicationId(), app.getApplicationName(), app.getEntityType(), app.getCatalogItemId(), app.getMetadata(), event);
            }

            @Override
            public void onLocationEvent(LocationMetadata loc, LocationEvent event) {
                listener.onLocationEvent(loc.getLocationId(), loc.getMetadata(), event);
            }
            
            @Override
            public boolean equals(Object obj) {
                return (obj instanceof UsageListenerAdapter) && listener.equals(((UsageListenerAdapter)obj).listener);
            }
            
            @Override
            public int hashCode() {
                return Objects.hashCode(listener);
            }
        }
        
        void onApplicationEvent(String applicationId, String applicationName, String entityType, String catalogItemId,
                Map<String, String> metadata, ApplicationEvent event);
        
        void onLocationEvent(String locationId, Map<String, String> metadata, LocationEvent event);
    }

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

    /**
     * @since 0.7.0
     * @deprecated since 0.7.0; use {@link #removeUsageListener(org.apache.brooklyn.core.management.internal.UsageListener)};
     *             see {@link org.apache.brooklyn.core.management.internal.UsageManager.UsageListener.UsageListenerAdapter} 
     */
    void addUsageListener(org.apache.brooklyn.core.management.internal.UsageManager.UsageListener listener);

    /**
     * @since 0.7.0
     * @deprecated since 0.7.0; use {@link #removeUsageListener(org.apache.brooklyn.core.management.internal.UsageListener)}
     */
    @Deprecated
    void removeUsageListener(org.apache.brooklyn.core.management.internal.UsageManager.UsageListener listener);
    
    /**
     * Adds the given listener, to be notified on recording of application/location events.
     * The listener notifications may be asynchronous.
     * 
     * As of 0.7.0, the listener is not persisted so will be lost on restart/rebind. This
     * behaviour may change in a subsequent release. 
     */
    void addUsageListener(org.apache.brooklyn.core.management.internal.UsageListener listener);

    /**
     * Removes the given listener.
     */
    void removeUsageListener(org.apache.brooklyn.core.management.internal.UsageListener listener);
}
