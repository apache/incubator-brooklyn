package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoPersister.Delta;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Reflections;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RebindManagerImpl implements RebindManager {

    // TODO Use ImmediateDeltaChangeListener if the period is set to 0?
    // But for MultiFile persister, that is still async
    
    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private volatile long periodicPersistPeriod = 1000;
    
    private volatile boolean running = true;
    
    private final ManagementContext managementContext;

    private volatile PeriodicDeltaChangeListener realChangeListener;
    private volatile ChangeListener changeListener;
    
    private volatile BrooklynMementoPersister persister;

    public RebindManagerImpl(ManagementContext managementContext) {
        this.managementContext = managementContext;
        this.changeListener = ChangeListener.NOOP;
    }

    /**
     * Must be called before setPerister()
     */
    public void setPeriodicPersistPeriod(long periodMillis) {
        this.periodicPersistPeriod = periodMillis;
    }

    @Override
    public void setPersister(BrooklynMementoPersister val) {
        if (persister != null && persister != val) {
            throw new IllegalStateException("Dynamically changing persister is not supported: old="+persister+"; new="+val);
        }
        this.persister = checkNotNull(val, "persister");
        
        if (running) {
            this.realChangeListener = new PeriodicDeltaChangeListener(managementContext.getExecutionManager(), persister, periodicPersistPeriod);
            this.changeListener = new SafeChangeListener(realChangeListener);
        }
    }

    @Override
    public BrooklynMementoPersister getPersister() {
        return persister;
    }
    
    @Override
    public void stop() {
        running = false;
        if (realChangeListener != null) realChangeListener.stop();
        if (persister != null) persister.stop();
    }
    
    @Override
    @VisibleForTesting
     public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (persister == null || !running) return;
        realChangeListener.waitForPendingComplete(timeout, unit);
        persister.waitForWritesCompleted(timeout, unit);
    }
    
    @Override
    public ChangeListener getChangeListener() {
        return changeListener;
    }
    
    @Override
    public List<Application> rebind() throws IOException {
        return rebind(getClass().getClassLoader());
    }
    
    @Override
    public List<Application> rebind(final ClassLoader classLoader) throws IOException {
        checkNotNull(classLoader, "classLoader");
        
        Reflections reflections = new Reflections(classLoader);
        Map<String,Entity> entities = Maps.newLinkedHashMap();
        Map<String,Location> locations = Maps.newLinkedHashMap();
        Map<String,Policy> policies = Maps.newLinkedHashMap();
        
        final RebindContextImpl rebindContext = new RebindContextImpl(classLoader);

        LookupContext dummyLookupContext = new LookupContext() {
            private final Entity dummyEntity = (Entity) java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class[] {Entity.class, EntityInternal.class, EntityProxy.class},
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                            return m.invoke(this, args);
                        }
                    });
            private final Location dummyLocation = (Location) java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class[] {Location.class, LocationInternal.class},
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                            return m.invoke(this, args);
                        }
                    });
            @Override public Entity lookupEntity(String id) {
                return dummyEntity;
            }
            @Override public Location lookupLocation(String id) {
                return dummyLocation;
            }
        };
        
        LookupContext realLookupContext = new LookupContext() {
            @Override public Entity lookupEntity(String id) {
                return rebindContext.getEntity(id);
            }
            @Override public Location lookupLocation(String id) {
                return rebindContext.getLocation(id);
            }
        };
        
        // Two-phase deserialization. First we deserialize to find all instances (and their types).
        // Then we deserialize so that inter-entity references can be set. During the first phase,
        // any inter-entity reference will get the dummyEntity/dummyLocation.
        BrooklynMemento mementoHeaders = persister.loadMemento(dummyLookupContext);

        // Instantiate locations
        LOG.info("RebindManager instantiating locations: {}", mementoHeaders.getLocationIds());
        for (LocationMemento locMemento : mementoHeaders.getLocationMementos().values()) {
            if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating location {}", locMemento);
            
            Location location = newLocation(locMemento, reflections);
            locations.put(locMemento.getId(), location);
            rebindContext.registerLocation(locMemento.getId(), location);
        }
        
        // Instantiate entities
        LOG.info("RebindManager instantiating entities: {}", mementoHeaders.getEntityIds());
        for (EntityMemento entityMemento : mementoHeaders.getEntityMementos().values()) {
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating entity {}", entityMemento);
            
            Entity entity = newEntity(entityMemento, reflections);
            entities.put(entityMemento.getId(), entity);
            rebindContext.registerEntity(entityMemento.getId(), entity);
        }
        
        // Instantiate policies
        LOG.info("RebindManager instantiating policies: {}", mementoHeaders.getPolicyIds());
        for (PolicyMemento policyMemento : mementoHeaders.getPolicyMementos().values()) {
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating policy {}", policyMemento);
            
            Policy policy = newPolicy(policyMemento, reflections);
            policies.put(policyMemento.getId(), policy);
            rebindContext.registerPolicy(policyMemento.getId(), policy);
        }
        
        BrooklynMemento memento = persister.loadMemento(realLookupContext);

        // Reconstruct locations
        LOG.info("RebindManager reconstructing locations");
        for (LocationMemento locMemento : memento.getLocationMementos().values()) {
            Location location = rebindContext.getLocation(locMemento.getId());
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing location {}", locMemento);

            location.getRebindSupport().reconstruct(rebindContext, locMemento);
        }

        // Reconstruct policies
        LOG.info("RebindManager reconstructing policies");
        for (PolicyMemento policyMemento : memento.getPolicyMementos().values()) {
            Policy policy = rebindContext.getPolicy(policyMemento.getId());
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing policy {}", policyMemento);

            policy.getRebindSupport().reconstruct(rebindContext, policyMemento);
        }

        // Reconstruct entities
        LOG.info("RebindManager reconstructing entities");
        for (EntityMemento entityMemento : memento.getEntityMementos().values()) {
            Entity entity = rebindContext.getEntity(entityMemento.getId());
            if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing entity {}", entityMemento);

            entity.getRebindSupport().reconstruct(rebindContext, entityMemento);
        }
        
        LOG.info("RebindManager managing locations");
        for (Location location: locations.values()) {
            if (location.getParent()==null) {
                // manage all root locations
                // LocationManager.manage perhaps should not be deprecated, as we need to do this I think?
                managementContext.getLocationManager().manage(location);
            }
        }
        
        // Manage the top-level apps (causing everything under them to become managed)
        LOG.info("RebindManager managing entities");
        for (String appId : memento.getApplicationIds()) {
            Entities.startManagement((Application)rebindContext.getEntity(appId), managementContext);
        }
        
        // Return the top-level applications
        List<Application> apps = Lists.newArrayList();
        for (String appId : memento.getApplicationIds()) {
            apps.add((Application)rebindContext.getEntity(appId));
        }
        
        LOG.info("RebindManager complete; return apps: {}", memento.getApplicationIds());
        return apps;
    }
    
    private Entity newEntity(EntityMemento memento, Reflections reflections) {
        String entityId = memento.getId();
        String entityType = checkNotNull(memento.getType(), "entityType of "+entityId);
        Class<?> entityClazz = reflections.loadClass(entityType);
        
        if (InternalEntityFactory.isNewStyleEntity(managementContext, entityClazz)) {
            Class<?> entityInterface = managementContext.getEntityManager().getEntityTypeRegistry().getEntityTypeOf((Class)entityClazz);
            EntitySpec<?> entitySpec = EntitySpec.create((Class)entityInterface).impl((Class)entityClazz).configure("id", entityId);
            return managementContext.getEntityManager().createEntity(entitySpec);
        } else {
            // There are several possibilities for the constructor; find one that works.
            // Prefer passing in the flags because required for Application to set the management context
            // TODO Feels very hacky!
            Map<String,Object> flags = Maps.newLinkedHashMap();
            flags.put("id", entityId);
            if (AbstractApplication.class.isAssignableFrom(entityClazz)) flags.put("mgmt", managementContext);
            
            return (Entity) invokeConstructor(reflections, entityClazz, new Object[] {flags}, new Object[] {flags, null}, new Object[] {null}, new Object[0]);
        }
    }
    
    /**
     * Constructs a new location, passing to its constructor the location id and all of memento.getFlags().
     */
    private Location newLocation(LocationMemento memento, Reflections reflections) {
        String locationId = memento.getId();
        String locationType = checkNotNull(memento.getType(), "locationType of "+locationId);
        Class<?> locationClazz = reflections.loadClass(locationType);

        if (InternalLocationFactory.isNewStyleLocation(managementContext, locationClazz)) {
            LocationSpec<?> locationSpec = LocationSpec.create((Class)locationClazz).configure("id", locationId);
            return managementContext.getLocationManager().createLocation(locationSpec);
        } else {
            // There are several possibilities for the constructor; find one that works.
            // Prefer passing in the flags because required for Application to set the management context
            // TODO Feels very hacky!
            Map<String,?> flags = MutableMap.of("id", locationId, "deferConstructionChecks", true);

            return (Location) invokeConstructor(reflections, locationClazz, new Object[] {flags});
        }
        // note 'used' config keys get marked in BasicLocationRebindSupport
    }

    /**
     * Constructs a new location, passing to its constructor the location id and all of memento.getFlags().
     */
    private Policy newPolicy(PolicyMemento memento, Reflections reflections) {
        String id = memento.getId();
        String policyType = checkNotNull(memento.getType(), "policyType of "+id);
        Class<?> policyClazz = reflections.loadClass(policyType);

        Map<String, Object> flags = MutableMap.<String, Object>builder()
                .put("id", id)
                .putAll(memento.getFlags())
                .build();

        return (Policy) invokeConstructor(reflections, policyClazz, new Object[] {flags});
    }

    private <T> T invokeConstructor(Reflections reflections, Class<T> clazz, Object[]... possibleArgs) {
        for (Object[] args : possibleArgs) {
            try {
                Optional<T> v = Reflections.invokeConstructorWithArgs(clazz, args, true);
                if (v.isPresent()) {
                    return v.get();
                }
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        }
        throw new IllegalStateException("Cannot instantiate instance of type "+clazz+"; expected constructor signature not found");
    }

    private static class DeltaImpl implements Delta {
        Collection<LocationMemento> locations = Collections.emptyList();
        Collection<EntityMemento> entities = Collections.emptyList();
        Collection<PolicyMemento> policies = Collections.emptyList();
        Collection <String> removedLocationIds = Collections.emptyList();
        Collection <String> removedEntityIds = Collections.emptyList();
        Collection <String> removedPolicyIds = Collections.emptyList();
        
        @Override
        public Collection<LocationMemento> locations() {
            return locations;
        }

        @Override
        public Collection<EntityMemento> entities() {
            return entities;
        }

        @Override
        public Collection<PolicyMemento> policies() {
            return policies;
        }

        @Override
        public Collection<String> removedLocationIds() {
            return removedLocationIds;
        }

        @Override
        public Collection<String> removedEntityIds() {
            return removedEntityIds;
        }
        
        @Override
        public Collection<String> removedPolicyIds() {
            return removedPolicyIds;
        }
    }
    
    /**
     * Wraps a ChangeListener, to log and never propagate any exceptions that it throws.
     * 
     * Catches Throwable, because really don't want a problem to propagate up to user code,
     * to cause business-level operations to fail. For example, if there is a linkage error
     * due to some problem in the serialization dependencies then just log it. For things
     * more severe (e.g. OutOfMemoryError) then the catch+log means we'll report that we
     * failed to persist, and we'd expect other threads to throw the OutOfMemoryError so
     * we shouldn't lose anything.
     */
    private static class SafeChangeListener implements ChangeListener {
        private final ChangeListener delegate;
        
        public SafeChangeListener(ChangeListener delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public void onManaged(Entity entity) {
            try {
                delegate.onManaged(entity);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onManaged("+entity+"); continuing.", t);
            }
        }

        @Override
        public void onManaged(Location location) {
            try {
                delegate.onManaged(location);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onManaged("+location+"); continuing.", t);
            }
        }
        
        @Override
        public void onChanged(Entity entity) {
            try {
                delegate.onChanged(entity);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+entity+"); continuing.", t);
            }
        }
        
        @Override
        public void onUnmanaged(Entity entity) {
            try {
                delegate.onUnmanaged(entity);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onUnmanaged("+entity+"); continuing.", t);
            }
        }

        @Override
        public void onUnmanaged(Location location) {
            try {
                delegate.onUnmanaged(location);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onUnmanaged("+location+"); continuing.", t);
            }
        }

        @Override
        public void onChanged(Location location) {
            try {
                delegate.onChanged(location);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+location+"); continuing.", t);
            }
        }
        
        @Override
        public void onChanged(Policy policy) {
            try {
                delegate.onChanged(policy);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+policy+"); continuing.", t);
            }
        }
    }
}
