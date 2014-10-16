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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.AccessController;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.internal.ManagementTransitionInfo.ManagementTransitionMode;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class LocalLocationManager implements LocationManagerInternal {

    @Beta /* expect to remove when API returns LocationSpec or similar */
    public static final ConfigKey<Boolean> CREATE_UNMANAGED = ConfigKeys.newBooleanConfigKey("brooklyn.internal.location.createUnmanaged",
        "If set on a location or spec, causes the manager to create it in an unmanaged state (for peeking)", false);
    
    private static final Logger log = LoggerFactory.getLogger(LocalLocationManager.class);

    private final LocalManagementContext managementContext;
    private final InternalLocationFactory locationFactory;
    
    protected final Map<String,Location> locationsById = Maps.newLinkedHashMap();
    private final Map<String, Location> preRegisteredLocationsById = Maps.newLinkedHashMap();

    /** Management mode for each location */
    protected final Map<String,ManagementTransitionMode> locationModesById = Maps.newLinkedHashMap();

    private final BrooklynStorage storage;
    private Map<String, String> locationTypes;

    private static AtomicLong LOCATION_CNT = new AtomicLong(0);
    
    public LocalLocationManager(LocalManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        this.locationFactory = new InternalLocationFactory(managementContext);
        
        this.storage = managementContext.getStorage();
        locationTypes = storage.getMap("locations");
    }

    public InternalLocationFactory getLocationFactory() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return locationFactory;
        
    }

    @Override
    public <T extends Location> T createLocation(LocationSpec<T> spec) {
        try {
            boolean createUnmanaged = ConfigBag.coerceFirstNonNullKeyValue(CREATE_UNMANAGED, 
                spec.getConfig().get(CREATE_UNMANAGED), spec.getFlags().get(CREATE_UNMANAGED.getName()));
            if (createUnmanaged) {
                spec.removeConfig(CREATE_UNMANAGED);
            }
            
            T loc = locationFactory.createLocation(spec);
            if (!createUnmanaged) {
                manage(loc);
            } else {
                // remove references
                Location parent = loc.getParent();
                if (parent!=null) {
                    ((AbstractLocation)parent).removeChild(loc);
                }
                preRegisteredLocationsById.remove(loc.getId());
            }
            
            return loc;
        } catch (Throwable e) {
            log.warn("Failed to create location using spec "+spec+" (rethrowing)", e);
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public <T extends Location> T createLocation(Map<?,?> config, Class<T> type) {
        return createLocation(LocationSpec.create(config, type));
    }

    @Override
    public synchronized Collection<Location> getLocations() {
        return ImmutableList.copyOf(locationsById.values());
    }
    
    @Override
    public Collection<String> getLocationIds() {
        return ImmutableList.copyOf(locationsById.keySet());
    }

    @Override
    public synchronized Location getLocation(String id) {
        return locationsById.get(id);
    }
    
    public synchronized Location getLocationEvenIfPreManaged(String id) {
        Location result = locationsById.get(id);
        if (result == null) {
            result = preRegisteredLocationsById.get(id);
        }
        return result;
    }
    
    @Override
    public boolean isManaged(Location loc) {
        return (isRunning() && loc != null && getLocation(loc.getId()) != null);
    }
    
    synchronized boolean isPreRegistered(Location loc) {
        return preRegisteredLocationsById.containsKey(loc.getId());
    }
    
    public boolean isKnownLocationId(String id) {
        return preRegisteredLocationsById.containsKey(id) || locationsById.containsKey(id);
    }
    
    synchronized void prePreManage(Location loc) {
        if (isPreRegistered(loc)) {
            log.warn(""+this+" redundant call to pre-pre-manage location "+loc+"; skipping", 
                    new Exception("source of duplicate pre-pre-manage of "+loc));
            return;
        }
        preRegisteredLocationsById.put(loc.getId(), loc);
    }
    
    @Override
    public ManagementTransitionMode getLastManagementTransitionMode(String itemId) {
        return locationModesById.get(itemId);
    }
    
    @Override
    public void setManagementTransitionMode(Location item, ManagementTransitionMode mode) {
        locationModesById.put(item.getId(), mode);
    }

    // TODO synchronization issues here: see comment in LocalEntityManager.manage(Entity)
    /** management on creation */
    @Override
    public Location manage(Location loc) {
        if (isManaged(loc)) {
            // TODO put log.warn back in if/when manage(Location) becomes private; or could even have assert.
            // Can be stricter about contract.
            return loc;
        }
        
        Location parent = loc.getParent();
        if (parent != null && !managementContext.getLocationManager().isManaged(parent)) {
            log.warn("Parent location "+parent+" of "+loc+" is not managed; attempting to manage it (in future this may be disallowed)");
            return manage(parent);
        } else {
            return manageRecursive(loc, ManagementTransitionMode.CREATING);
        }
    }
    
    @Override
    public void manageRebindedRoot(Location item) {
        ManagementTransitionMode mode = getLastManagementTransitionMode(item.getId());
        Preconditions.checkNotNull(mode, "Mode not set for rebinding %s", item);
        manageRecursive(item, mode);
    }
    
    protected Location manageRecursive(Location loc, final ManagementTransitionMode initialMode) {
        AccessController.Response access = managementContext.getAccessController().canManageLocation(loc);
        if (!access.isAllowed()) {
            throw new IllegalStateException("Access controller forbids management of "+loc+": "+access.getMsg());
        }

        long count = LOCATION_CNT.incrementAndGet();
        if (log.isDebugEnabled()) {
            String msg = "Managing location " + loc + " ("+initialMode+"), from " + Tasks.current()+" / "+Entitlements.getEntitlementContext();
            if (count % 100 == 0) {
                // include trace periodically in case we get leaks or too much location management
                log.debug(msg, new Exception("Informational stack trace of call to manage location "+loc+" ("+count+" calls; "+getLocations().size()+" currently managed)"));
            } else {
                log.debug(msg);
            }
        }

        recursively(loc, new Predicate<AbstractLocation>() { public boolean apply(AbstractLocation it) {
            ManagementTransitionMode mode = getLastManagementTransitionMode(it.getId());
            if (mode==null) {
                setManagementTransitionMode(it, mode = initialMode);
            }
            
            if (it.isManaged()) {
                if (mode==ManagementTransitionMode.CREATING) {
                    // silently bail out
                    return false;
                } else {
                    // on rebind, we just replace, fall through to below
                }
            }
            
            boolean result = manageNonRecursive(it, null);
            if (result) {
                it.setManagementContext(managementContext);
                if (!mode.isReadOnly()) {
                    it.onManagementStarted();
                    recordLocationEvent(it, Lifecycle.CREATED);
                }
                managementContext.getRebindManager().getChangeListener().onManaged(it);
            }
            return result;
        } });
        return loc;
    }
    
    @Override
    public void unmanage(final Location loc) {
        unmanage(loc, ManagementTransitionMode.DESTROYING);
    }
    
    public void unmanage(final Location loc, final ManagementTransitionMode mode) {
        unmanage(loc, mode, false);
    }
    
    private void unmanage(final Location loc, ManagementTransitionMode mode, boolean hasBeenReplaced) {
        if (shouldSkipUnmanagement(loc)) return;

        if (hasBeenReplaced) {
            // we are unmanaging an old instance after having replaced it
            
            if (mode==ManagementTransitionMode.REBINDING_NO_LONGER_PRIMARY) {
                // when migrating away, these all need to be called
                managementContext.getRebindManager().getChangeListener().onUnmanaged(loc);
                if (managementContext.gc != null) managementContext.gc.onUnmanaged(loc);
            } else {
                // should be coming *from* read only; nothing needed
                if (!mode.wasReadOnly())
                    log.warn("Should not be unmanaging "+loc+" in mode "+mode+"; ignoring");
            }

        } else if (mode==ManagementTransitionMode.REBINDING_DESTROYED) {
            // we are unmanaging an instance (secondary) for which the primary has been destroyed elsewhere
            unmanageNonRecursive(loc);
            managementContext.getRebindManager().getChangeListener().onUnmanaged(loc);
            if (managementContext.gc != null) managementContext.gc.onUnmanaged(loc);
            
        } else if (mode==ManagementTransitionMode.DESTROYING) {
            // we are unmanaging an instance either because it is being destroyed (primary), 
            // or due to an explicit call (shutting down all things, read-only and primary);
            // in either case, should be recursive
            
            // Need to store all child entities as onManagementStopping removes a child from the parent entity
            recursively(loc, new Predicate<AbstractLocation>() { public boolean apply(AbstractLocation it) {
                if (shouldSkipUnmanagement(it)) return false;
                boolean result = unmanageNonRecursive(it);
                if (result) {
                    ManagementTransitionMode mode = getLastManagementTransitionMode(it.getId());
                    if (mode==null) {
                        log.warn("Missing transition mode for "+it+" when unmanaging; assuming primary");
                        mode = ManagementTransitionMode.DESTROYING;
                    }
                    if (!mode.isReadOnly()) it.onManagementStopped();
                    managementContext.getRebindManager().getChangeListener().onUnmanaged(it);
                    if (!mode.isReadOnly()) recordLocationEvent(it, Lifecycle.DESTROYED);
                    if (managementContext.gc != null) managementContext.gc.onUnmanaged(it);
                }
                return result;
            } });
            
        } else {
            log.warn("Invalid mode for unmanage: "+mode+" on "+loc+" (ignoring)");
        }
        
    }
    
    /**
     * Adds this location event to the usage record for the given location (creating the usage 
     * record if one does not already exist).
     */
    private void recordLocationEvent(LocationInternal loc, Lifecycle state) {
        try {
            managementContext.getUsageManager().recordLocationEvent(loc, state);
        } catch (RuntimeInterruptedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to store location lifecycle event for "+loc+" (ignoring)", e);
        }
    }

    private void recursively(Location e, Predicate<AbstractLocation> action) {
        boolean success = action.apply( (AbstractLocation)e );
        if (!success) {
            return; // Don't manage children if action false/unnecessary for parent
        }
        for (Location child : e.getChildren()) {
            recursively(child, action);
        }
    }

    /**
     * Should ensure that the location is now managed somewhere, and known about in all the lists.
     * Returns true if the location has now become managed; false if it was already managed (anything else throws exception)
     * @param rebindPrimary true if rebinding primary, false if rebinding as copy, null if creating (not rebinding)
     */
    private synchronized boolean manageNonRecursive(Location loc, ManagementTransitionMode mode) {
        Location old = locationsById.put(loc.getId(), loc);
        preRegisteredLocationsById.remove(loc.getId());

        locationTypes.put(loc.getId(), loc.getClass().getName());
        
        if (old!=null && mode==ManagementTransitionMode.CREATING) {
            if (old.equals(loc)) {
                log.warn("{} redundant call to start management of location {}", this, loc);
            } else {
                throw new IllegalStateException("call to manage location "+loc+" but different location "+old+" already known under that id at "+this);
            }
            return false;
        }

        if (old!=null && old!=loc) {
            // passing the transition info will ensure the right shutdown steps invoked for old instance
            unmanage(old, mode, true);
        }
        
        return true;
    }
    
    /**
     * Should ensure that the location is no longer managed anywhere, remove from all lists.
     * Returns true if the location has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    private synchronized boolean unmanageNonRecursive(Location loc) {
        loc.setParent(null);
        Object old = locationsById.remove(loc.getId());
        locationTypes.remove(loc.getId());
        locationModesById.remove(loc.getId());
        
        if (old==null) {
            log.warn("{} call to stop management of unknown location (already unmanaged?) {}; ignoring", this, loc);
            return false;
        } else if (!old.equals(loc)) {
            // shouldn't happen...
            log.error("{} call to stop management of location {} removed different location {}; ignoring", new Object[] { this, loc, old });
            return true;
        } else {
            if (log.isDebugEnabled()) log.debug("{} stopped management of location {}", this, loc);
            return true;
        }
    }

    private boolean shouldSkipUnmanagement(Location loc) {
        if (loc==null) {
            log.warn(""+this+" call to unmanage null location; skipping",  
                new IllegalStateException("source of null unmanagement call to "+this));
            return true;
        }
        if (!isManaged(loc)) {
            log.warn("{} call to stop management of unknown location (already unmanaged?) {}; skipping, and all descendants", this, loc);
            return true;
        }
        return false;
    }
    
    private boolean isRunning() {
        return managementContext.isRunning();
    }
    
}
