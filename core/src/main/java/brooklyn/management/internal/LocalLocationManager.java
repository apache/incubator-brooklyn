package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.LocationEvent;
import brooklyn.location.basic.LocationInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.management.AccessController;
import brooklyn.management.LocationManager;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class LocalLocationManager implements LocationManager {

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
    
    private void storeLocationEvent(Lifecycle state, LocationInternal loc) {
        System.err.println(state + " = " + loc);
        if (state != null && loc != null && loc.getId() != null && managementContext.getStorage() != null) {
            Object o = loc.getConfig(LocationConfigKeys.CALLER_CONTEXT);
            System.err.println(state + " entity= " + o);
            if (o != null && o instanceof Entity) {
                Entity caller = (Entity) o;
//                LocationEvent event = new LocationEvent(state, caller.getId(), caller.getEntityType() == null ? null : caller.getEntityType().getName(), caller.getApplicationId(), loc.toMetadataRecord());
                LocationEvent event = new LocationEvent(state, caller.getId(), null, null, loc.toMetadataRecord());
                managementContext.getStorage().<String, LocationEvent>getMap("location-usage").put(loc.getId(), event);
            }
        }
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
