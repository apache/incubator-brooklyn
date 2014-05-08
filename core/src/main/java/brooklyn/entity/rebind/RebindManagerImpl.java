package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/** Manages the persistence/rebind process.
 * <p>
 * Lifecycle is to create an instance of this, set it up (e.g. {@link #setPeriodicPersistPeriod(Duration)}, 
 * {@link #setPersister(BrooklynMementoPersister)}; however noting that persist period must be set before the persister).
 * <p>
 * Usually done for you by the conveniences (such as the launcher). */
public class RebindManagerImpl implements RebindManager {

    // TODO Use ImmediateDeltaChangeListener if the period is set to 0?
    // But for MultiFile persister, that is still async
    
    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private volatile Duration periodicPersistPeriod = Duration.ONE_SECOND;
    
    private volatile boolean running = false;
    
    private final ManagementContextInternal managementContext;

    private volatile PeriodicDeltaChangeListener realChangeListener;
    private volatile ChangeListener changeListener;
    
    private volatile BrooklynMementoPersister persister;

    public RebindManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
        this.changeListener = ChangeListener.NOOP;
    }

    /**
     * Must be called before setPerister()
     */
    public void setPeriodicPersistPeriod(Duration period) {
        if (persister!=null) throw new IllegalStateException("Cannot set period after persister is generated.");
        this.periodicPersistPeriod = period;
    }

    /**
     * @deprecated since 0.7.0; use {@link #setPeriodicPersistPeriod(Duration)}
     */
    public void setPeriodicPersistPeriod(long periodMillis) {
        setPeriodicPersistPeriod(Duration.of(periodMillis, TimeUnit.MILLISECONDS));
    }

    @Override
    public void setPersister(BrooklynMementoPersister val) {
        if (persister != null && persister != val) {
            throw new IllegalStateException("Dynamically changing persister is not supported: old="+persister+"; new="+val);
        }
        this.persister = checkNotNull(val, "persister");
        
        this.realChangeListener = new PeriodicDeltaChangeListener(managementContext.getExecutionManager(), persister, periodicPersistPeriod.toMilliseconds());
        this.changeListener = new SafeChangeListener(realChangeListener);
        
        if (running) {
            realChangeListener.start();
        }
    }

    @Override
    @VisibleForTesting
    public BrooklynMementoPersister getPersister() {
        return persister;
    }
    
    @Override
    public void start() {
        running = true;
        if (realChangeListener != null) realChangeListener.start();
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
        persister.waitForWritesCompleted(Duration.of(timeout, unit));
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
        
        try {
            Reflections reflections = new Reflections(classLoader);
            Map<String,Entity> entities = Maps.newLinkedHashMap();
            Map<String,Location> locations = Maps.newLinkedHashMap();
            Map<String,Policy> policies = Maps.newLinkedHashMap();
            
            final RebindContextImpl rebindContext = new RebindContextImpl(classLoader);
    
            LookupContext dummyLookupContext = new LookupContext() {
                @Override public Entity lookupEntity(Class<?> type, String id) {
                    Set<Class<?>> types = MutableSet.<Class<?>>of(Entity.class, EntityInternal.class, EntityProxy.class);
                    if (type != null) types.add(type);
                    return (Entity) newDummy(types);
                }
                @Override public Location lookupLocation(Class<?> type, String id) {
                    Set<Class<?>> types = MutableSet.<Class<?>>of(Location.class, LocationInternal.class);
                    if (type != null) types.add(type);
                    return (Location) newDummy(types);
                }
                private Object newDummy(Collection<Class<?>> types) {
                    return java.lang.reflect.Proxy.newProxyInstance(
                        classLoader,
                        types.toArray(new Class<?>[types.size()]),
                        new InvocationHandler() {
                            @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                                return m.invoke(this, args);
                            }
                        });
                }
            };
            
            LookupContext realLookupContext = new LookupContext() {
                private final boolean removeDanglingRefs = true;
                @Override public Entity lookupEntity(Class<?> type, String id) {
                    Entity result = rebindContext.getEntity(id);
                    if (result == null) {
                        if (removeDanglingRefs) {
                            LOG.warn("No entity found with id "+id+"; returning null");
                        } else {
                            throw new IllegalStateException("No entity found with id "+id);
                        }
                    } else if (type != null && !type.isInstance(result)) {
                        LOG.warn("Entity with id "+id+" does not match type "+type+"; returning "+result);
                    }
                    return result;
                }
                @Override public Location lookupLocation(Class<?> type, String id) {
                    Location result = rebindContext.getLocation(id);
                    if (result == null) {
                        if (removeDanglingRefs) {
                            LOG.warn("No location found with id "+id+"; returning null");
                        } else {
                            throw new IllegalStateException("No location found with id "+id);
                        }
                    } else if (type != null && !type.isInstance(result)) {
                        LOG.warn("Location with id "+id+" does not match type "+type+"; returning "+result);
                    }
                    return result;
                }
            };
            
            // Two-phase deserialization. First we deserialize to find all instances (and their types).
            // Then we deserialize so that inter-entity references can be set. During the first phase,
            // any inter-entity reference will get the dummyEntity/dummyLocation.
            //
            // TODO if underlying data-store is changed between first and second phase (e.g. to add an
            // entity), then second phase might try to reconstitute an entity that has not been put in
            // the rebindContext. This should not affect normal production usage, because rebind is run
            // against a data-store that is not being written to by other brooklyn instance(s).
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
            for (LocationMemento locMemento : sortParentFirst(memento.getLocationMementos()).values()) {
                Location location = rebindContext.getLocation(locMemento.getId());
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing location {}", locMemento);
    
                ((LocationInternal)location).getRebindSupport().reconstruct(rebindContext, locMemento);
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
            for (EntityMemento entityMemento : sortParentFirst(memento.getEntityMementos()).values()) {
                Entity entity = rebindContext.getEntity(entityMemento.getId());
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing entity {}", entityMemento);
    
                entityMemento.injectTypeClass(entity.getClass());
                ((EntityInternal)entity).getRebindSupport().reconstruct(rebindContext, entityMemento);
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
        } catch (Exception e) {
            LOG.warn("Problem during rebind (rethrowing)", e);
            throw Exceptions.propagate(e);
        }
    }
    
    /**
     * Sorts the map of nodes, so that a node's parent is guaranteed to come before that node
     * (unless the parent is missing).
     * 
     * Relies on ordering guarantees of returned map (i.e. LinkedHashMap, which guarantees insertion order 
     * even if a key is re-inserted into the map).
     * 
     * TODO Inefficient implementation!
     */
    @VisibleForTesting
    <T extends TreeNode> Map<String, T> sortParentFirst(Map<String, T> nodes) {
        Map<String, T> result = Maps.newLinkedHashMap();
        for (Map.Entry<String, T> entry : nodes.entrySet()) {
            String id = entry.getKey();
            T node = entry.getValue();
            List<T> tempchain = Lists.newLinkedList();
            
            T nodeinchain = node;
            while (nodeinchain != null) {
                tempchain.add(0, nodeinchain);
                nodeinchain = nodes.get(nodeinchain.getParent());
            }
            for (T n : tempchain) {
                result.put(n.getId(), n);
            }
        }
        return result;
    }

    private Entity newEntity(EntityMemento memento, Reflections reflections) {
        String entityId = memento.getId();
        String entityType = checkNotNull(memento.getType(), "entityType of "+entityId);
        Class<? extends Entity> entityClazz = (Class<? extends Entity>) reflections.loadClass(entityType);
        memento.injectTypeClass(entityClazz);
        
        if (InternalEntityFactory.isNewStyleEntity(managementContext, entityClazz)) {
            // Not using entityManager.createEntity(EntitySpec) because don't want init() to be called
            // TODO Need to rationalise this to move code into methods of InternalEntityFactory.
            //      The InternalEntityFactory.constructEntity is used in three places:
            //       1. normal entity creation (through entityManager.createEntity)
            //       2. rebind (i.e. here)
            //       3. yaml parsing
            //      Purpose is to create a new (unconfigured/uninitialised) entity, but that is
            //      known about by the managementContext and that has things like the right id and 
            //      a proxy for if another entity needs to reference it during the init phase.
            InternalEntityFactory entityFactory = managementContext.getEntityFactory();
            Entity entity = entityFactory.constructEntity(entityClazz);
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", entityId), entity);
            if (entity instanceof AbstractApplication) {
                FlagUtils.setFieldsFromFlags(ImmutableMap.of("mgmt", managementContext), entity);
            }
            managementContext.prePreManage(entity);
            ((AbstractEntity)entity).setManagementContext(managementContext);
            
            Class<?> entityInterface = managementContext.getEntityManager().getEntityTypeRegistry().getEntityTypeOf((Class)entityClazz);
            EntitySpec<Entity> entitySpec = EntitySpec.create((Class)entityInterface)
                    .impl((Class)entityClazz)
                    .id(entityId);

            ((AbstractEntity)entity).setProxy(entityFactory.createEntityProxy(entitySpec, entity));
            
            return entity;

        } else {
            // There are several possibilities for the constructor; find one that works.
            // Prefer passing in the flags because required for Application to set the management context
            // TODO Feels very hacky!

            Map<Object,Object> flags = Maps.newLinkedHashMap();
            flags.put("id", entityId);
            if (AbstractApplication.class.isAssignableFrom(entityClazz)) flags.put("mgmt", managementContext);

            // TODO document the multiple sources of flags, and the reason for setting the mgmt context *and* supplying it as the flag
            // (NB: merge reported conflict as the two things were added separately)
            flags.putAll(memento.getConfig());
            flags.putAll(memento.getConfigUnmatched());
            Entity entity = (Entity) invokeConstructor(reflections, entityClazz, new Object[] {flags}, new Object[] {flags, null}, new Object[] {null}, new Object[0]);
            
            // In case the constructor didn't take the Map arg, then also set it here.
            // e.g. for top-level app instances such as WebClusterDatabaseExampleApp will (often?) not have
            // interface + constructor.
            // TODO On serializing the memento, we should capture which interfaces so can recreate
            // the proxy+spec (including for apps where there's not an obvious interface).
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", entityId), entity);
            if (entity instanceof AbstractApplication) {
                FlagUtils.setFieldsFromFlags(ImmutableMap.of("mgmt", managementContext), entity);
            }
            ((AbstractEntity)entity).setManagementContext(managementContext);
            managementContext.prePreManage(entity);
            
            return entity;
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
            LocationSpec<?> locationSpec = LocationSpec.create((Class)locationClazz).id(locationId);
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
