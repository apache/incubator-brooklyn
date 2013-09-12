package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.LocationInternal;
import brooklyn.location.basic.LocationUsage;
import brooklyn.management.AccessController;
import brooklyn.management.LocationManager;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class LocalLocationManager implements LocationManager {

    private static final String LOCATION_USAGE_KEY = "location-usage";

    private static final Logger log = LoggerFactory.getLogger(LocalLocationManager.class);

    private final LocalManagementContext managementContext;
    private final InternalLocationFactory locationFactory;
    
    protected final Map<String,Location> locationsById = Maps.newLinkedHashMap();
    private final Map<String, Location> preRegisteredLocationsById = Maps.newLinkedHashMap();
    
    public LocalLocationManager(LocalManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
        this.locationFactory = new InternalLocationFactory(managementContext);
    }

    @Override
    public <T extends Location> T createLocation(LocationSpec<T> spec) {
        try {
            T loc = locationFactory.createLocation(spec);
            manage(loc);
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
        return (isRunning() && getLocation(loc.getId()) != null);
    }
    
    synchronized boolean isPreRegistered(Location loc) {
        return preRegisteredLocationsById.containsKey(loc.getId());
    }
    
    synchronized void prePreManage(Location loc) {
        if (isPreRegistered(loc)) {
            log.warn(""+this+" redundant call to pre-pre-manage location "+loc+"; skipping", 
                    new Exception("source of duplicate pre-pre-manage of "+loc));
            return;
        }
        preRegisteredLocationsById.put(loc.getId(), loc);
    }
    
    // TODO synchronization issues here: see comment in LocalEntityManager.manage(Entity)
    @Override
    public Location manage(Location loc) {
        if (isManaged(loc)) {
            // TODO put log.warn back in if/when manage(Location) becomes private; or could even have assert.
            // Can be stricter about contract.
            return loc;
        }
        
        AccessController.Response access = managementContext.getAccessManager().getAccessController().canManageLocation(loc);
        if (!access.isAllowed()) {
            throw new IllegalStateException("Access controller forbids management of "+loc+": "+access.getMsg());
        }
        
        Location parent = loc.getParent();
        if (parent != null && !managementContext.getLocationManager().isManaged(parent)) {
//            throw new IllegalStateException("Can't manage "+e+" because its parent is not yet managed ("+parent+")");
            log.warn("Parent location "+parent+" of "+loc+" is not managed; attempting to manage it (in future this may be disallowed)");
            manage(parent);
        }
        
        recursively(loc, new Predicate<AbstractLocation>() { public boolean apply(AbstractLocation it) {
            if (it.isManaged()) {
                return false;
            } else {
                boolean result = manageNonRecursive(it);
                if (result) {
                    it.setManagementContext(managementContext);
                    it.onManagementStarted();
                    storeLocationEvent(Lifecycle.CREATED, it);
                    managementContext.getRebindManager().getChangeListener().onManaged(it);
                }
                return result;
            }
        } });
        return loc;
    }
    
    @Override
    public void unmanage(Location loc) {
        if (shouldSkipUnmanagement(loc)) return;
        
        recursively(loc, new Predicate<AbstractLocation>() { public boolean apply(AbstractLocation it) {
            if (shouldSkipUnmanagement(it)) return false;
            boolean result = unmanageNonRecursive(it);
            if (result) {
                it.onManagementStopped(); 
                managementContext.getRebindManager().getChangeListener().onUnmanaged(it);
                storeLocationEvent(Lifecycle.DESTROYED, it);
                if (managementContext.gc != null) managementContext.gc.onUnmanaged(it);
            }
            return result;
        } });
    }
    
    /**
     * Adds this location event to the usage record for the given location (creating the usage 
     * record if one does not already exist).
     */
    private void storeLocationEvent(Lifecycle state, LocationInternal loc) {
        // TODO This approach (i.e. recording events on manage/unmanage would not work for
        // locations that are reused. For example, in a FixedListMachineProvisioningLocation
        // the ssh machine location is returned to the pool and handed back out again.
        // But maybe the solution there is to hand out different instances so that one user
        // can't change the config of the SshMachineLocation to subsequently affect the next 
        // user.
        //
        // TODO Should perhaps extract the location storage methods into their own class,
        // but no strong enough feelings yet...
        
        checkNotNull(state, "state of location %s", loc);
        checkNotNull(loc, "location");
        if (loc.getId() == null) {
            log.error("Ignoring location lifecycle event for {} (state {}), because location has no id", loc, state);
            return;
        }
        if (managementContext.getStorage() == null) {
            log.warn("Cannot store location lifecycle event for {} (state {}), because storage not available", loc, state);
            return;
        }
        
        try {
            Object callerContext = loc.getConfig(LocationConfigKeys.CALLER_CONTEXT);
            log.info("Location lifecycle event: location {} in state {}; caller context {}", new Object[] {loc, state, callerContext}); // FIXME decrease logging level?
            
            if (callerContext != null && callerContext instanceof Entity) {
                Entity caller = (Entity) callerContext;
                String entityTypeName = caller.getEntityType().getName();
                String appId = caller.getApplicationId();
                LocationUsage.LocationEvent event = new LocationUsage.LocationEvent(state, caller.getId(), entityTypeName, appId);
                
                ConcurrentMap<String, LocationUsage> usageMap = managementContext.getStorage().<String, LocationUsage>getMap(LOCATION_USAGE_KEY);
                LocationUsage usage = usageMap.get(loc.getId());
                if (usage == null) {
                    usage = new LocationUsage(loc.getId(), loc.toMetadataRecord());
                }
                usage.addEvent(event);
                usageMap.put(loc.getId(), usage);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to store location lifecycle event for "+loc, e);
        }
    }

    /**
     * Returns the usage info for the location with the given id, or null if unknown.
     */
    public LocationUsage getLocationUsage(String locationId) {
        BrooklynStorage storage = managementContext.getStorage();

        Map<String, LocationUsage> usageMap = storage.getMap(LOCATION_USAGE_KEY);
        return usageMap.get(locationId);
    }
    
    /**
     * Returns the usage info that matches the given predicate.
     * For example, could be used to find locations used within a given time period.
     */
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
    
    private void recursively(Location e, Predicate<AbstractLocation> action) {
        boolean success = action.apply( (AbstractLocation)e );
        if (!success) {
            return; // Don't manage children if action false/unnecessary for parent
        }
        for (Location child : e.getChildLocations()) {
            recursively(child, action);
        }
    }

    /**
     * Should ensure that the location is now managed somewhere, and known about in all the lists.
     * Returns true if the location has now become managed; false if it was already managed (anything else throws exception)
     */
    private synchronized boolean manageNonRecursive(Location loc) {
        Object old = locationsById.put(loc.getId(), loc);
        preRegisteredLocationsById.remove(loc.getId());
        
        if (old!=null) {
            if (old.equals(loc)) {
                log.warn("{} redundant call to start management of location {}", this, loc);
            } else {
                throw new IllegalStateException("call to manage location "+loc+" but different location "+old+" already known under that id at "+this);
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * Should ensure that the location is no longer managed anywhere, remove from all lists.
     * Returns true if the location has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    private synchronized boolean unmanageNonRecursive(AbstractLocation loc) {
        loc.setParentLocation(null);
        Object old = locationsById.remove(loc.getId());

        if (old==null) {
            log.warn("{} call to stop management of unknown location (already unmanaged?) {}", this, loc);
            return false;
        } else if (!old.equals(loc)) {
            // shouldn't happen...
            log.error("{} call to stop management of location {} removed different location {}", new Object[] { this, loc, old });
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
