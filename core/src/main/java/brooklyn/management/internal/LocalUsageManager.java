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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.management.entitlement.EntitlementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ManagementContextInjectable;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.usage.ApplicationUsage;
import brooklyn.management.usage.LocationUsage;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class LocalUsageManager implements UsageManager {

    // TODO Threading model needs revisited.
    // Synchronizes on updates to storage; but if two Brooklyn nodes were both writing to the same
    // ApplicationUsage or LocationUsage record there'd be a race. That currently won't happen
    // (at least for ApplicationUsage?) because the app is mastered in just one node at a time,
    // and because location events are just manage/unmanage which should be happening in just 
    // one place at a time for a given location.
    
    private static final Logger log = LoggerFactory.getLogger(LocalUsageManager.class);

    private static class ApplicationMetadataImpl implements brooklyn.management.internal.UsageListener.ApplicationMetadata {
        private final Application app;
        private String applicationId;
        private String applicationName;
        private String entityType;
        private String catalogItemId;
        private Map<String, String> metadata;

        ApplicationMetadataImpl(Application app) {
            this.app = checkNotNull(app, "app");
            applicationId = app.getId();
            applicationName = app.getDisplayName();
            entityType = app.getEntityType().getName();
            catalogItemId = app.getCatalogItemId();
            metadata = ((EntityInternal)app).toMetadataRecord();
        }
        @Override public Application getApplication() {
            return app;
        }
        @Override public String getApplicationId() {
            return applicationId;
        }
        @Override public String getApplicationName() {
            return applicationName;
        }
        @Override public String getEntityType() {
            return entityType;
        }
        @Override public String getCatalogItemId() {
            return catalogItemId;
        }
        @Override public Map<String, String> getMetadata() {
            return metadata;
        }
    }
    
    private static class LocationMetadataImpl implements brooklyn.management.internal.UsageListener.LocationMetadata {
        private final Location loc;
        private String locationId;
        private Map<String, String> metadata;

        LocationMetadataImpl(Location loc) {
            this.loc = checkNotNull(loc, "loc");
            locationId = loc.getId();
            metadata = ((LocationInternal)loc).toMetadataRecord();
        }
        @Override public Location getLocation() {
            return loc;
        }
        @Override public String getLocationId() {
            return locationId;
        }
        @Override public Map<String, String> getMetadata() {
            return metadata;
        }
    }
    
    // Register a coercion from String->UsageListener, so that USAGE_LISTENERS defined in brooklyn.properties
    // will be instantiated, given their class names.
    static {
        TypeCoercions.registerAdapter(String.class, brooklyn.management.internal.UsageListener.class, new Function<String, brooklyn.management.internal.UsageListener>() {
            @Override public brooklyn.management.internal.UsageListener apply(String input) {
                // TODO Want to use classLoader = mgmt.getCatalog().getRootClassLoader();
                ClassLoader classLoader = LocalUsageManager.class.getClassLoader();
                Optional<Object> result = Reflections.invokeConstructorWithArgs(classLoader, input);
                if (result.isPresent()) {
                    if (result.get() instanceof brooklyn.management.internal.UsageManager.UsageListener) {
                        return new brooklyn.management.internal.UsageManager.UsageListener.UsageListenerAdapter((brooklyn.management.internal.UsageManager.UsageListener) result.get());
                    } else {
                        return (brooklyn.management.internal.UsageListener) result.get();
                    }
                } else {
                    throw new IllegalStateException("Failed to create UsageListener from class name '"+input+"' using no-arg constructor");
                }
            }
        });
    }
    
    @VisibleForTesting
    public static final String APPLICATION_USAGE_KEY = "usage-application";
    
    @VisibleForTesting
    public static final String LOCATION_USAGE_KEY = "usage-location";

    private final LocalManagementContext managementContext;
    
    private final Object mutex = new Object();

    private final List<brooklyn.management.internal.UsageListener> listeners = Lists.newCopyOnWriteArrayList();
    
    private final AtomicInteger listenerQueueSize = new AtomicInteger();
    
    private ListeningExecutorService listenerExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
            .setNameFormat("brooklyn-usagemanager-listener-%d")
            .build()));

    public LocalUsageManager(LocalManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        
        // TODO Once brooklyn.management.internal.UsageManager.UsageListener is deleted, restore this
        // to normal generics!
        Collection<?> listeners = managementContext.getBrooklynProperties().getConfig(UsageManager.USAGE_LISTENERS);
        if (listeners != null) {
            for (Object listener : listeners) {
                if (listener instanceof ManagementContextInjectable) {
                    ((ManagementContextInjectable)listener).injectManagementContext(managementContext);
                }
                if (listener instanceof brooklyn.management.internal.UsageManager.UsageListener) {
                    addUsageListener((brooklyn.management.internal.UsageManager.UsageListener)listener);
                } else if (listener instanceof brooklyn.management.internal.UsageListener) {
                    addUsageListener((brooklyn.management.internal.UsageListener)listener);
                } else if (listener == null) {
                    throw new NullPointerException("null listener in config "+UsageManager.USAGE_LISTENERS);
                } else {
                    throw new ClassCastException("listener "+listener+" of type "+listener.getClass()+" is not of type "+brooklyn.management.internal.UsageListener.class.getName());
                }
            }
        }
    }

    public void terminate() {
        // Wait for the listeners to finish + close the listeners
        Duration timeout = managementContext.getBrooklynProperties().getConfig(UsageManager.USAGE_LISTENER_TERMINATION_TIMEOUT);
        if (listenerQueueSize.get() > 0) {
            log.info("Usage manager waiting for "+listenerQueueSize+" listener events for up to "+timeout);
        }
        List<ListenableFuture<?>> futures = Lists.newArrayList();
        for (final brooklyn.management.internal.UsageListener listener : listeners) {
            ListenableFuture<?> future = listenerExecutor.submit(new Runnable() {
                public void run() {
                    if (listener instanceof Closeable) {
                        try {
                            ((Closeable)listener).close();
                        } catch (IOException e) {
                            log.warn("Problem closing usage listener "+listener+" (continuing)", e);
                        }
                    }
                }});
            futures.add(future);
        }
        try {
            Futures.successfulAsList(futures).get(timeout.toMilliseconds(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.warn("Problem terminiating usage listeners (continuing)", e);
        } finally {
            listenerExecutor.shutdownNow();
        }
    }

    private void execOnListeners(final Function<brooklyn.management.internal.UsageListener, Void> job) {
        for (final brooklyn.management.internal.UsageListener listener : listeners) {
            listenerQueueSize.incrementAndGet();
            listenerExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        job.apply(listener);
                    } catch (RuntimeException e) {
                        log.error("Problem notifying listener "+listener+" of "+job, e);
                        Exceptions.propagateIfFatal(e);
                    } finally {
                        listenerQueueSize.decrementAndGet();
                    }
                }});
        }
    }
    
    @Override
    public void recordApplicationEvent(final Application app, final Lifecycle state) {
        log.debug("Storing application lifecycle usage event: application {} in state {}", new Object[] {app, state});
        ConcurrentMap<String, ApplicationUsage> eventMap = managementContext.getStorage().getMap(APPLICATION_USAGE_KEY);
        synchronized (mutex) {
            ApplicationUsage usage = eventMap.get(app.getId());
            if (usage == null) {
                usage = new ApplicationUsage(app.getId(), app.getDisplayName(), app.getEntityType().getName(), ((EntityInternal)app).toMetadataRecord());
            }
            final ApplicationUsage.ApplicationEvent event = new ApplicationUsage.ApplicationEvent(state, getUser());
            usage.addEvent(event);        
            eventMap.put(app.getId(), usage);

            execOnListeners(new Function<brooklyn.management.internal.UsageListener, Void>() {
                    public Void apply(brooklyn.management.internal.UsageListener listener) {
                        listener.onApplicationEvent(new ApplicationMetadataImpl(Entities.proxy(app)), event);
                        return null;
                    }
                    public String toString() {
                        return "applicationEvent("+app+", "+state+")";
                    }});
        }
    }
    
    /**
     * Adds this location event to the usage record for the given location (creating the usage 
     * record if one does not already exist).
     */
    @Override
    public void recordLocationEvent(final Location loc, final Lifecycle state) {
        // TODO This approach (i.e. recording events on manage/unmanage would not work for
        // locations that are reused. For example, in a FixedListMachineProvisioningLocation
        // the ssh machine location is returned to the pool and handed back out again.
        // But maybe the solution there is to hand out different instances so that one user
        // can't change the config of the SshMachineLocation to subsequently affect the next 
        // user.
        //
        // TODO Should perhaps extract the location storage methods into their own class,
        // but no strong enough feelings yet...
        
        checkNotNull(loc, "location");
        if (loc.getConfig(AbstractLocation.TEMPORARY_LOCATION)) {
            log.info("Ignoring location lifecycle usage event for {} (state {}), because location is a temporary location", loc, state);
            return;
        }
        checkNotNull(state, "state of location %s", loc);
        if (loc.getId() == null) {
            log.error("Ignoring location lifecycle usage event for {} (state {}), because location has no id", loc, state);
            return;
        }
        if (managementContext.getStorage() == null) {
            log.warn("Cannot store location lifecycle usage event for {} (state {}), because storage not available", loc, state);
            return;
        }
        
        Object callerContext = loc.getConfig(LocationConfigKeys.CALLER_CONTEXT);
        
        if (callerContext != null && callerContext instanceof Entity) {
            log.debug("Storing location lifecycle usage event: location {} in state {}; caller context {}", new Object[] {loc, state, callerContext});
            
            Entity caller = (Entity) callerContext;
            String entityTypeName = caller.getEntityType().getName();
            String appId = caller.getApplicationId();

            final LocationUsage.LocationEvent event = new LocationUsage.LocationEvent(state, caller.getId(), entityTypeName, appId, getUser());
            
            ConcurrentMap<String, LocationUsage> usageMap = managementContext.getStorage().<String, LocationUsage>getMap(LOCATION_USAGE_KEY);
            synchronized (mutex) {
                LocationUsage usage = usageMap.get(loc.getId());
                if (usage == null) {
                    usage = new LocationUsage(loc.getId(), ((LocationInternal)loc).toMetadataRecord());
                }
                usage.addEvent(event);
                usageMap.put(loc.getId(), usage);
                
                execOnListeners(new Function<brooklyn.management.internal.UsageListener, Void>() {
                        public Void apply(brooklyn.management.internal.UsageListener listener) {
                            listener.onLocationEvent(new LocationMetadataImpl(loc), event);
                            return null;
                        }
                        public String toString() {
                            return "locationEvent("+loc+", "+state+")";
                        }});
            }
        } else {
            // normal for high-level locations
            log.trace("Not recording location lifecycle usage event for {} in state {}, because no caller context", new Object[] {loc, state});
        }
    }

    /**
     * Returns the usage info for the location with the given id, or null if unknown.
     */
    @Override
    public LocationUsage getLocationUsage(String locationId) {
        BrooklynStorage storage = managementContext.getStorage();

        Map<String, LocationUsage> usageMap = storage.getMap(LOCATION_USAGE_KEY);
        return usageMap.get(locationId);
    }
    
    /**
     * Returns the usage info that matches the given predicate.
     * For example, could be used to find locations used within a given time period.
     */
    @Override
    public Set<LocationUsage> getLocationUsage(Predicate<? super LocationUsage> filter) {
        // TODO could do more efficient indexing, to more easily find locations in use during a given period.
        // But this is good enough for first-pass.

        Map<String, LocationUsage> usageMap = managementContext.getStorage().getMap(LOCATION_USAGE_KEY);
        Set<LocationUsage> result = Sets.newLinkedHashSet();
        
        for (LocationUsage usage : usageMap.values()) {
            if (filter.apply(usage)) {
                result.add(usage);
            }
        }
        return result;
    }
    
    /**
     * Returns the usage info for the location with the given id, or null if unknown.
     */
    @Override
    public ApplicationUsage getApplicationUsage(String appId) {
        BrooklynStorage storage = managementContext.getStorage();

        Map<String, ApplicationUsage> usageMap = storage.getMap(APPLICATION_USAGE_KEY);
        return usageMap.get(appId);
    }
    
    /**
     * Returns the usage info that matches the given predicate.
     * For example, could be used to find applications used within a given time period.
     */
    @Override
    public Set<ApplicationUsage> getApplicationUsage(Predicate<? super ApplicationUsage> filter) {
        // TODO could do more efficient indexing, to more easily find locations in use during a given period.
        // But this is good enough for first-pass.

        Map<String, ApplicationUsage> usageMap = managementContext.getStorage().getMap(APPLICATION_USAGE_KEY);
        Set<ApplicationUsage> result = Sets.newLinkedHashSet();
        
        for (ApplicationUsage usage : usageMap.values()) {
            if (filter.apply(usage)) {
                result.add(usage);
            }
        }
        return result;
    }

    @Override
    @Deprecated
    public void addUsageListener(brooklyn.management.internal.UsageManager.UsageListener listener) {
        addUsageListener(new brooklyn.management.internal.UsageManager.UsageListener.UsageListenerAdapter(listener));
    }

    @Override
    @Deprecated
    public void removeUsageListener(brooklyn.management.internal.UsageManager.UsageListener listener) {
        removeUsageListener(new brooklyn.management.internal.UsageManager.UsageListener.UsageListenerAdapter(listener));
    }
    
    @Override
    public void addUsageListener(brooklyn.management.internal.UsageListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeUsageListener(brooklyn.management.internal.UsageListener listener) {
        listeners.remove(listener);
    }

    private String getUser() {
        EntitlementContext entitlementContext = Entitlements.getEntitlementContext();
        if (entitlementContext != null) {
            return entitlementContext.user();
        }
        return null;
    }
}
