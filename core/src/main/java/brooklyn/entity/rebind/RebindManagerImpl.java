package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.task.BasicExecutionContext;
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
    
    public static final ConfigKey<RebindFailureMode> DANGLING_REFERENCE_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.danglingRef",
                    "Action to take if a dangling reference is discovered during rebind", RebindFailureMode.CONTINUE);
    public static final ConfigKey<RebindFailureMode> REBIND_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.rebind",
                    "Action to take if a failure occurs during rebind", RebindFailureMode.FAIL_AT_END);
    
    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private volatile Duration periodicPersistPeriod = Duration.ONE_SECOND;
    
    private volatile boolean running = false;
    
    private final ManagementContextInternal managementContext;

    private volatile PeriodicDeltaChangeListener realChangeListener;
    private volatile ChangeListener changeListener;
    
    private volatile BrooklynMementoPersister persister;

    private final boolean persistPoliciesEnabled;
    private final boolean persistEnrichersEnabled;
    private RebindFailureMode danglingRefFailureMode;
    private RebindFailureMode rebindFailureMode;

    public RebindManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
        this.changeListener = ChangeListener.NOOP;
        
        this.persistPoliciesEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_POLICY_PERSISTENCE_PROPERTY);
        this.persistEnrichersEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_ENRICHER_PERSISTENCE_PROPERTY);

        danglingRefFailureMode = managementContext.getConfig().getConfig(DANGLING_REFERENCE_FAILURE_MODE);
        rebindFailureMode = managementContext.getConfig().getConfig(REBIND_FAILURE_MODE);
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
        waitForPendingComplete(Duration.of(timeout, unit));
    }
    @Override
    @VisibleForTesting
    public void waitForPendingComplete(Duration timeout) throws InterruptedException, TimeoutException {
        if (persister == null || !running) return;
        realChangeListener.waitForPendingComplete(timeout);
        persister.waitForWritesCompleted(timeout);
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
        RebindExceptionHandler exceptionHandler = new RebindExceptionHandlerImpl(danglingRefFailureMode, rebindFailureMode);
        return rebind(classLoader, exceptionHandler);
    }

    @Override
    public List<Application> rebind(final ClassLoader classLoader, final RebindExceptionHandler exceptionHandler) throws IOException {
        BasicExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
        if (ec == null) {
            ec = new BasicExecutionContext(managementContext.getExecutionManager());
            Task<List<Application>> task = ec.submit(new Callable<List<Application>>() {
                @Override public List<Application> call() throws Exception {
                    return rebindImpl(classLoader, exceptionHandler);
                }});
            try {
                return task.get();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        } else {
            return rebindImpl(classLoader, exceptionHandler);
        }
    }
    
    protected List<Application> rebindImpl(final ClassLoader classLoader, final RebindExceptionHandler exceptionHandler) throws IOException {
        checkNotNull(classLoader, "classLoader");

        try {
            Reflections reflections = new Reflections(classLoader);
            Map<String,Entity> entities = Maps.newLinkedHashMap();
            Map<String,Location> locations = Maps.newLinkedHashMap();
            Map<String,Policy> policies = Maps.newLinkedHashMap();
            Map<String,Enricher> enrichers = Maps.newLinkedHashMap();
            
            final RebindContextImpl rebindContext = new RebindContextImpl(classLoader);
    
            LookupContext realLookupContext = new LookupContext() {
                @Override public Entity lookupEntity(Class<?> type, String id) {
                    Entity result = rebindContext.getEntity(id);
                    if (result == null) {
                        result = exceptionHandler.onDanglingEntityRef(id);
                    } else if (type != null && !type.isInstance(result)) {
                        LOG.warn("Entity with id "+id+" does not match type "+type+"; returning "+result);
                    }
                    return result;
                }
                @Override public Location lookupLocation(Class<?> type, String id) {
                    Location result = rebindContext.getLocation(id);
                    if (result == null) {
                        result = exceptionHandler.onDanglingLocationRef(id);
                    } else if (type != null && !type.isInstance(result)) {
                        LOG.warn("Location with id "+id+" does not match type "+type+"; returning "+result);
                    }
                    return result;
                }
                @Override public Policy lookupPolicy(Class<?> type, String id) {
                    Policy result = rebindContext.getPolicy(id);
                    if (result == null) {
                        result = exceptionHandler.onDanglingPolicyRef(id);
                    } else if (type != null && !type.isInstance(result)) {
                        LOG.warn("Policy with id "+id+" does not match type "+type+"; returning "+result);
                    }
                    return result;
                }
                @Override public Enricher lookupEnricher(Class<?> type, String id) {
                    Enricher result = rebindContext.getEnricher(id);
                    if (result == null) {
                        result = exceptionHandler.onDanglingEnricherRef(id);
                    } else if (type != null && !type.isInstance(result)) {
                        LOG.warn("Enricher with id "+id+" does not match type "+type+"; returning "+result);
                    }
                    return result;
                }
            };
            
            // Two-phase deserialization.
            // First we deserialize just the "manifest" to find all instances (and their types).
            // Then we deserialize so that inter-entity references can be set.
            //
            // TODO if underlying data-store is changed between first and second phase (e.g. to add an
            // entity), then second phase might try to reconstitute an entity that has not been put in
            // the rebindContext. This should not affect normal production usage, because rebind is run
            // against a data-store that is not being written to by other brooklyn instance(s).
            BrooklynMementoManifest mementoManifest = persister.loadMementoManifest(exceptionHandler);
            
            // Instantiate locations
            LOG.info("RebindManager instantiating locations: {}", mementoManifest.getLocationIdToType().keySet());
            for (Map.Entry<String, String> entry : mementoManifest.getLocationIdToType().entrySet()) {
                String locId = entry.getKey();
                String locType = entry.getValue();
                if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating location {}", locId);
                
                try {
                    Location location = newLocation(locId, locType, reflections);
                    locations.put(locId, location);
                    rebindContext.registerLocation(locId, location);
                } catch (Exception e) {
                    exceptionHandler.onCreateLocationFailed(locId, locType, e);
                }
            }
            
            // Instantiate entities
            LOG.info("RebindManager instantiating entities: {}", mementoManifest.getEntityIdToType().keySet());
            for (Map.Entry<String, String> entry : mementoManifest.getEntityIdToType().entrySet()) {
                String entityId = entry.getKey();
                String entityType = entry.getValue();
                if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating entity {}", entityId);
                
                try {
                    Entity entity = newEntity(entityId, entityType, reflections);
                    entities.put(entityId, entity);
                    rebindContext.registerEntity(entityId, entity);
                } catch (Exception e) {
                    exceptionHandler.onCreateEntityFailed(entityId, entityType, e);
                }
            }
            
            BrooklynMemento memento = persister.loadMemento(realLookupContext, exceptionHandler);
            
            // Instantiate policies
            if (persistPoliciesEnabled) {
                LOG.info("RebindManager instantiating policies: {}", memento.getPolicyIds());
                for (PolicyMemento policyMemento : memento.getPolicyMementos().values()) {
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating policy {}", policyMemento);
                    
                    try {
                        Policy policy = newPolicy(policyMemento, reflections);
                        policies.put(policyMemento.getId(), policy);
                        rebindContext.registerPolicy(policyMemento.getId(), policy);
                    } catch (Exception e) {
                        exceptionHandler.onCreatePolicyFailed(policyMemento.getId(), policyMemento.getType(), e);
                    }
                }
            } else {
                LOG.debug("Not rebinding policies; feature disabled: {}", memento.getPolicyIds());
            }
            
            // Instantiate enrichers
            if (persistEnrichersEnabled) {
                LOG.info("RebindManager instantiating enrichers: {}", memento.getEnricherIds());
                for (EnricherMemento enricherMemento : memento.getEnricherMementos().values()) {
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating enricher {}", enricherMemento);
                    
                    Enricher enricher = newEnricher(enricherMemento, reflections);
                    enrichers.put(enricherMemento.getId(), enricher);
                    rebindContext.registerEnricher(enricherMemento.getId(), enricher);
                }
            } else {
                LOG.debug("Not rebinding enrichers; feature disabled: {}", memento.getEnricherIds());
            } 
            
            // Reconstruct locations
            LOG.info("RebindManager reconstructing locations");
            for (LocationMemento locMemento : sortParentFirst(memento.getLocationMementos()).values()) {
                Location location = rebindContext.getLocation(locMemento.getId());
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing location {}", locMemento);
                if (location == null) {
                    // usually because of creation-failure, when not using fail-fast
                    exceptionHandler.onLocationNotFound(locMemento.getId());
                } else {
                    try {
                        ((LocationInternal)location).getRebindSupport().reconstruct(rebindContext, locMemento);
                    } catch (Exception e) {
                        exceptionHandler.onRebindLocationFailed(location, e);
                    }
                }
            }

            // Reconstruct policies
            if (persistPoliciesEnabled) {
                LOG.info("RebindManager reconstructing policies");
                for (PolicyMemento policyMemento : memento.getPolicyMementos().values()) {
                    Policy policy = rebindContext.getPolicy(policyMemento.getId());
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing policy {}", policyMemento);
    
                    if (policy == null) {
                        // usually because of creation-failure, when not using fail-fast
                        exceptionHandler.onPolicyNotFound(policyMemento.getId());
                    } else {
                        try {
                            policy.getRebindSupport().reconstruct(rebindContext, policyMemento);
                        } catch (Exception e) {
                            exceptionHandler.onRebindPolicyFailed(policy, e);
                        }
                    }
                }
            }

            // Reconstruct enrichers
            if (persistEnrichersEnabled) {
                LOG.info("RebindManager reconstructing enrichers");
                for (EnricherMemento enricherMemento : memento.getEnricherMementos().values()) {
                    Enricher enricher = rebindContext.getEnricher(enricherMemento.getId());
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing enricher {}", enricherMemento);
        
                    enricher.getRebindSupport().reconstruct(rebindContext, enricherMemento);
                }
            }
    
            // Reconstruct entities
            LOG.info("RebindManager reconstructing entities");
            for (EntityMemento entityMemento : sortParentFirst(memento.getEntityMementos()).values()) {
                Entity entity = rebindContext.getEntity(entityMemento.getId());
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing entity {}", entityMemento);
    
                if (entity == null) {
                    // usually because of creation-failure, when not using fail-fast
                    exceptionHandler.onEntityNotFound(entityMemento.getId());
                } else {
                    try {
                        entityMemento.injectTypeClass(entity.getClass());
                        ((EntityInternal)entity).getRebindSupport().reconstruct(rebindContext, entityMemento);
                    } catch (Exception e) {
                        exceptionHandler.onRebindEntityFailed(entity, e);
                    }
                }
            }
            
            LOG.info("RebindManager managing locations");
            for (Location location: locations.values()) {
                if (location.getParent()==null) {
                    // manage all root locations
                    // LocationManager.manage perhaps should not be deprecated, as we need to do this I think?
                    try {
                        managementContext.getLocationManager().manage(location);
                    } catch (Exception e) {
                        exceptionHandler.onManageLocationFailed(location, e);
                    }
                }
            }
            
            // Manage the top-level apps (causing everything under them to become managed)
            LOG.info("RebindManager managing entities");
            List<Application> apps = Lists.newArrayList();
            for (String appId : memento.getApplicationIds()) {
                Entity entity = rebindContext.getEntity(appId);
                if (entity == null) {
                    // usually because of creation-failure, when not using fail-fast
                    exceptionHandler.onEntityNotFound(appId);
                } else {
                    try {
                        Entities.startManagement((Application)entity, managementContext);
                    } catch (Exception e) {
                        exceptionHandler.onManageEntityFailed(entity, e);
                    }
                    apps.add((Application)entity);
                }
            }
            
            exceptionHandler.onDone();

            // Return the top-level applications
            LOG.info("RebindManager complete; return apps: {}", memento.getApplicationIds());
            return apps;
            
        } catch (RuntimeException e) {
            throw exceptionHandler.onFailed(e);
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

    private Entity newEntity(String entityId, String entityType, Reflections reflections) {
        Class<? extends Entity> entityClazz = (Class<? extends Entity>) reflections.loadClass(entityType);
        
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
            
            Class<?> entityInterface;
            List<Class<?>> additionalInterfaces;
            try {
                entityInterface = managementContext.getEntityManager().getEntityTypeRegistry().getEntityTypeOf((Class)entityClazz);
                additionalInterfaces = Reflections.getAllInterfaces(entityClazz);
            } catch (IllegalArgumentException e) {
                // TODO this can happen if it's an app with no annotation, for example; not nice catching exception
                entityInterface = Entity.class;
                additionalInterfaces = Reflections.getAllInterfaces(entityClazz);
            }
            EntitySpec<Entity> entitySpec = EntitySpec.create((Class)entityInterface)
                    .additionalInterfaces(additionalInterfaces)
                    .impl((Class)entityClazz)
                    .id(entityId);

            ((AbstractEntity)entity).setProxy(entityFactory.createEntityProxy(entitySpec, entity));
            
            return entity;

        } else {
            LOG.warn("Deprecated rebind of entity without no-arg constructor; this may not be supported in future versions: id="+entityId+"; type="+entityType);
            
            // There are several possibilities for the constructor; find one that works.
            // Prefer passing in the flags because required for Application to set the management context
            // TODO Feels very hacky!

            Map<Object,Object> flags = Maps.newLinkedHashMap();
            flags.put("id", entityId);
            if (AbstractApplication.class.isAssignableFrom(entityClazz)) flags.put("mgmt", managementContext);

            // TODO document the multiple sources of flags, and the reason for setting the mgmt context *and* supplying it as the flag
            // (NB: merge reported conflict as the two things were added separately)
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
    private Location newLocation(String locationId, String locationType, Reflections reflections) {
        Class<? extends Location> locationClazz = (Class<? extends Location>) reflections.loadClass(locationType);

        if (InternalLocationFactory.isNewStyleLocation(managementContext, locationClazz)) {
            // Not using loationManager.createLocation(LocationSpec) because don't want init() to be called
            // TODO Need to rationalise this to move code into methods of InternalLocationFactory.
            //      But note that we'll change all locations to be entities at some point!
            // See same code approach used in #newEntity(EntityMemento, Reflections)
            InternalLocationFactory locationFactory = managementContext.getLocationFactory();
            Location location = locationFactory.constructLocation(locationClazz);
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", locationId), location);
            managementContext.prePreManage(location);
            ((AbstractLocation)location).setManagementContext(managementContext);
            
            return location;
        } else {
            LOG.warn("Deprecated rebind of location without no-arg constructor; this may not be supported in future versions: id="+locationId+"; type="+locationType);
            
            // There are several possibilities for the constructor; find one that works.
            // Prefer passing in the flags because required for Application to set the management context
            // TODO Feels very hacky!
            Map<String,?> flags = MutableMap.of("id", locationId, "deferConstructionChecks", true);

            return (Location) invokeConstructor(reflections, locationClazz, new Object[] {flags});
        }
        // note 'used' config keys get marked in BasicLocationRebindSupport
    }

    /**
     * Constructs a new policy, passing to its constructor the policy id and all of memento.getConfig().
     */
    private Policy newPolicy(PolicyMemento memento, Reflections reflections) {
        String id = memento.getId();
        String policyType = checkNotNull(memento.getType(), "policy type of %s must not be null in memento", id);
        Class<? extends Policy> policyClazz = (Class<? extends Policy>) reflections.loadClass(policyType);

        if (InternalPolicyFactory.isNewStylePolicy(policyClazz)) {
            InternalPolicyFactory policyFactory = managementContext.getPolicyFactory();
            Policy policy = policyFactory.constructPolicy(policyClazz);
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", id), policy);
            ((AbstractPolicy)policy).setManagementContext(managementContext);
            
            return policy;

        } else {
            LOG.warn("Deprecated rebind of policy without no-arg constructor; this may not be supported in future versions: id="+id+"; type="+policyType);
            
            // There are several possibilities for the constructor; find one that works.
            // Prefer passing in the flags because required for Application to set the management context
            // TODO Feels very hacky!
            Map<String, Object> flags = MutableMap.<String, Object>of(
                    "id", id, 
                    "deferConstructionChecks", true,
                    "noConstructionInit", true);
            flags.putAll(memento.getConfig());

            return (Policy) invokeConstructor(reflections, policyClazz, new Object[] {flags});
        }
    }

    /**
     * Constructs a new enricher, passing to its constructor the enricher id and all of memento.getConfig().
     */
    private Enricher newEnricher(EnricherMemento memento, Reflections reflections) {
        String id = memento.getId();
        String enricherType = checkNotNull(memento.getType(), "enricher type of %s must not be null in memento", id);
        Class<? extends Enricher> enricherClazz = (Class<? extends Enricher>) reflections.loadClass(enricherType);

        if (InternalPolicyFactory.isNewStyleEnricher(enricherClazz)) {
            InternalPolicyFactory policyFactory = managementContext.getPolicyFactory();
            Enricher enricher = policyFactory.constructEnricher(enricherClazz);
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", id), enricher);
            ((AbstractEnricher)enricher).setManagementContext(managementContext);
            
            return enricher;

        } else {
            LOG.warn("Deprecated rebind of enricher without no-arg constructor; this may not be supported in future versions: id="+id+"; type="+enricherType);
            
            // There are several possibilities for the constructor; find one that works.
            // Prefer passing in the flags because required for Application to set the management context
            // TODO Feels very hacky!
            Map<String, Object> flags = MutableMap.<String, Object>of(
                    "id", id, 
                    "deferConstructionChecks", true,
                    "noConstructionInit", true);
            flags.putAll(memento.getConfig());

            return (Enricher) invokeConstructor(reflections, enricherClazz, new Object[] {flags});
        }

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

    private static boolean isNewStylePolicy(Class<?> clazz) {
        if (!Policy.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not a policy");
        }
        return Reflections.hasNoArgConstructor(clazz);
    }
    
    private static boolean isNewStyleEnricher(Class<?> clazz) {
        if (!Enricher.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class "+clazz+" is not an enricher");
        }
        return Reflections.hasNoArgConstructor(clazz);
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
        
        @Override
        public void onChanged(Enricher enricher) {
            try {
                delegate.onChanged(enricher);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+enricher+"); continuing.", t);
            }
        }
    }
}
