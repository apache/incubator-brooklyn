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
package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObject;
import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogLoadMode;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalFactory;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.management.ha.HighAvailabilityManagerImpl;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.management.internal.EntityManagerInternal;
import brooklyn.management.internal.LocationManagerInternal;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.management.internal.ManagementTransitionInfo.ManagementTransitionMode;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.mementos.CatalogItemMemento;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.FeedMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;
import brooklyn.mementos.TreeNode;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

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
    public static final ConfigKey<RebindFailureMode> ADD_POLICY_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.addPolicy",
                    "Action to take if a failure occurs when adding a policy or enricher", RebindFailureMode.CONTINUE);
    public static final ConfigKey<RebindFailureMode> LOAD_POLICY_FAILURE_MODE =
            ConfigKeys.newConfigKey(RebindFailureMode.class, "rebind.failureMode.loadPolicy",
                    "Action to take if a failure occurs when loading a policy or enricher", RebindFailureMode.CONTINUE);
    
    public static final Logger LOG = LoggerFactory.getLogger(RebindManagerImpl.class);

    private final ManagementContextInternal managementContext;
    
    private volatile Duration periodicPersistPeriod = Duration.ONE_SECOND;
    
    private volatile boolean persistenceRunning = false;
    private volatile PeriodicDeltaChangeListener persistenceRealChangeListener;
    private volatile ChangeListener persistencePublicChangeListener;
    
    private volatile boolean readOnlyRunning = false;
    private volatile ScheduledTask readOnlyTask = null;
    private transient Semaphore rebindActive = new Semaphore(1);
    
    private volatile BrooklynMementoPersister persistenceStoreAccess;

    private final boolean persistPoliciesEnabled;
    private final boolean persistEnrichersEnabled;
    private final boolean persistFeedsEnabled;
    private final boolean persistCatalogItemsEnabled;
    
    private RebindFailureMode danglingRefFailureMode;
    private RebindFailureMode rebindFailureMode;
    private RebindFailureMode addPolicyFailureMode;
    private RebindFailureMode loadPolicyFailureMode;

    /**
     * For tracking if rebinding, for {@link AbstractEnricher#isRebinding()} etc.
     *  
     * TODO What is a better way to do this?!
     * 
     * @author aled
     */
    public static class RebindTracker {
        private static ThreadLocal<Boolean> rebinding = new ThreadLocal<Boolean>();
        
        public static boolean isRebinding() {
            return (rebinding.get() == Boolean.TRUE);
        }
        
        static void reset() {
            rebinding.set(Boolean.FALSE);
        }
        
        static void setRebinding() {
            rebinding.set(Boolean.TRUE);
        }
    }

    public RebindManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
        this.persistencePublicChangeListener = ChangeListener.NOOP;
        
        this.persistPoliciesEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_POLICY_PERSISTENCE_PROPERTY);
        this.persistEnrichersEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_ENRICHER_PERSISTENCE_PROPERTY);
        this.persistFeedsEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_FEED_PERSISTENCE_PROPERTY);
        this.persistCatalogItemsEnabled = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY);

        danglingRefFailureMode = managementContext.getConfig().getConfig(DANGLING_REFERENCE_FAILURE_MODE);
        rebindFailureMode = managementContext.getConfig().getConfig(REBIND_FAILURE_MODE);
        addPolicyFailureMode = managementContext.getConfig().getConfig(ADD_POLICY_FAILURE_MODE);
        loadPolicyFailureMode = managementContext.getConfig().getConfig(LOAD_POLICY_FAILURE_MODE);

        LOG.debug("Persistence in {} of: policies={}, enrichers={}, feeds={}, catalog={}",
                new Object[]{this, persistPoliciesEnabled, persistEnrichersEnabled, persistFeedsEnabled, persistCatalogItemsEnabled});
    }

    /**
     * Must be called before setPerister()
     */
    public void setPeriodicPersistPeriod(Duration period) {
        if (persistenceStoreAccess!=null) throw new IllegalStateException("Cannot set period after persister is generated.");
        this.periodicPersistPeriod = period;
    }

    /**
     * @deprecated since 0.7.0; use {@link #setPeriodicPersistPeriod(Duration)}
     */
    public void setPeriodicPersistPeriod(long periodMillis) {
        setPeriodicPersistPeriod(Duration.of(periodMillis, TimeUnit.MILLISECONDS));
    }

    public boolean isPersistenceRunning() {
        return persistenceRunning;
    }
    
    public boolean isReadOnlyRunning() {
        return readOnlyRunning;
    }
    
    @Override
    public void setPersister(BrooklynMementoPersister val) {
        PersistenceExceptionHandler exceptionHandler = PersistenceExceptionHandlerImpl.builder()
                .build();
        setPersister(val, exceptionHandler);
    }

    @Override
    public void setPersister(BrooklynMementoPersister val, PersistenceExceptionHandler exceptionHandler) {
        if (persistenceStoreAccess != null && persistenceStoreAccess != val) {
            throw new IllegalStateException("Dynamically changing persister is not supported: old="+persistenceStoreAccess+"; new="+val);
        }
        this.persistenceStoreAccess = checkNotNull(val, "persister");
        
        this.persistenceRealChangeListener = new PeriodicDeltaChangeListener(managementContext.getServerExecutionContext(), persistenceStoreAccess, exceptionHandler, periodicPersistPeriod);
        this.persistencePublicChangeListener = new SafeChangeListener(persistenceRealChangeListener);
        
        if (persistenceRunning) {
            persistenceRealChangeListener.start();
        }
    }

    @Override
    @VisibleForTesting
    public BrooklynMementoPersister getPersister() {
        return persistenceStoreAccess;
    }
    
    @Override
    public void startPersistence() {
        if (readOnlyRunning) {
            throw new IllegalStateException("Cannot start read-only when already running with persistence");
        }
        LOG.debug("Starting persistence, mgmt "+managementContext.getManagementNodeId());
        persistenceRunning = true;
        persistenceStoreAccess.enableWriteAccess();
        if (persistenceRealChangeListener != null) persistenceRealChangeListener.start();
    }
    
    @Override
    public void stopPersistence() {
        LOG.debug("Stopping rebind (persistence), mgmt "+managementContext.getManagementNodeId());
        persistenceRunning = false;
        if (persistenceRealChangeListener != null) persistenceRealChangeListener.stop();
        if (persistenceStoreAccess != null) persistenceStoreAccess.disableWriteAccess(true);
        LOG.debug("Stopped rebind (persistence), mgmt "+managementContext.getManagementNodeId());
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void startReadOnly() {
        if (persistenceRunning) {
            throw new IllegalStateException("Cannot start read-only when already running with persistence");
        }
        if (readOnlyRunning || readOnlyTask!=null) {
            LOG.warn("Cannot request read-only mode for "+this+" when already running - "+readOnlyTask+"; ignoring");
            return;
        }
        LOG.debug("Starting read-only rebinding, mgmt "+managementContext.getManagementNodeId());
        
        if (persistenceRealChangeListener != null) persistenceRealChangeListener.stop();
        if (persistenceStoreAccess != null) persistenceStoreAccess.disableWriteAccess(true);
        
        readOnlyRunning = true;

        try {
            rebind(null, null, ManagementNodeState.HOT_STANDBY);
        } catch (Exception e) {
            Exceptions.propagate(e);
        }
        
        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<Void> call() {
                return Tasks.<Void>builder().dynamic(false).name("rebind (periodic run").body(new Callable<Void>() {
                    public Void call() {
                        try {
                            rebind(null, null, ManagementNodeState.HOT_STANDBY);
                            return null;
                        } catch (RuntimeInterruptedException e) {
                            LOG.debug("Interrupted rebinding (re-interrupting): "+e);
                            if (LOG.isTraceEnabled())
                                LOG.trace("Interrupted rebinding (re-interrupting): "+e, e);
                            Thread.currentThread().interrupt();
                            return null;
                        } catch (Exception e) {
                            // Don't rethrow: the behaviour of executionManager is different from a scheduledExecutorService,
                            // if we throw an exception, then our task will never get executed again
                            if (!readOnlyRunning) {
                                if (LOG.isTraceEnabled())
                                    LOG.trace("Problem rebinding (read-only running turned off): "+e, e);
                            } else {
                                LOG.warn("Problem rebinding: "+Exceptions.collapseText(e), e);
                            }
                            return null;
                        } catch (Throwable t) {
                            LOG.warn("Problem rebinding (rethrowing)", t);
                            throw Exceptions.propagate(t);
                        }
                    }}).build();
            }
        };
        readOnlyTask = (ScheduledTask) managementContext.getServerExecutionContext().submit(
            new ScheduledTask(MutableMap.of("displayName", "Periodic read-only rebind"), taskFactory).period(periodicPersistPeriod));
    }
    
    @Override
    public void stopReadOnly() {
        readOnlyRunning = false;
        if (readOnlyTask!=null) {
            LOG.debug("Stopping read-only rebinding, mgmt "+managementContext.getManagementNodeId());
            readOnlyTask.cancel(true);
            readOnlyTask.blockUntilEnded();
            boolean reallyEnded = Tasks.blockUntilInternalTasksEnded(readOnlyTask, Duration.TEN_SECONDS);
            if (!reallyEnded) {
                LOG.warn("Rebind (read-only) tasks took too long to die after interrupt (ignoring): "+readOnlyTask);
            }
            readOnlyTask = null;
            LOG.debug("Stopped read-only rebinding, mgmt "+managementContext.getManagementNodeId());
        }
    }
    
    @Override
    public void start() {
        ManagementNodeState target = getRebindMode();
        if (target==ManagementNodeState.HOT_STANDBY) {
            startReadOnly();
        } else if (target==ManagementNodeState.MASTER) {
            startPersistence();
        } else {
            LOG.warn("Nothing to start in "+this+" when HA mode is "+target);
        }
    }

    @Override
    public void stop() {
        stopReadOnly();
        stopPersistence();
        if (persistenceStoreAccess != null) persistenceStoreAccess.stop(true);
    }
    
    protected ManagementNodeState getRebindMode() {
        if (managementContext==null) throw new IllegalStateException("Invalid "+this+": no management context");
        if (!(managementContext.getHighAvailabilityManager() instanceof HighAvailabilityManagerImpl))
            throw new IllegalStateException("Invalid "+this+": unknown HA manager type "+managementContext.getHighAvailabilityManager());
        ManagementNodeState target = ((HighAvailabilityManagerImpl)managementContext.getHighAvailabilityManager()).getTransitionTargetNodeState();
        return target;
    }
    
    @Override
    @VisibleForTesting
    @Deprecated /** @deprecated since 0.7.0 use Duration as argument */
    public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        waitForPendingComplete(Duration.of(timeout, unit));
    }
    @Override
    @VisibleForTesting
    public void waitForPendingComplete(Duration timeout) throws InterruptedException, TimeoutException {
        if (persistenceStoreAccess == null || !persistenceRunning) return;
        persistenceRealChangeListener.waitForPendingComplete(timeout);
        persistenceStoreAccess.waitForWritesCompleted(timeout);
    }
    @Override
    @VisibleForTesting
    public void forcePersistNow() {
        persistenceRealChangeListener.persistNow();
    }
    
    @Override
    public ChangeListener getChangeListener() {
        return persistencePublicChangeListener;
    }
    
    @Override
    public List<Application> rebind() {
        return rebind(null, null, null);
    }
    
    @Override
    public List<Application> rebind(final ClassLoader classLoader) {
        return rebind(classLoader, null, null);
    }

    @Override
    public List<Application> rebind(final ClassLoader classLoader, final RebindExceptionHandler exceptionHandler) {
        return rebind(classLoader, exceptionHandler, null);
    }
    
    @Override
    public List<Application> rebind(ClassLoader classLoaderO, RebindExceptionHandler exceptionHandlerO, ManagementNodeState modeO) {
        final ClassLoader classLoader = classLoaderO!=null ? classLoaderO :
            managementContext.getCatalog().getRootClassLoader();
        final RebindExceptionHandler exceptionHandler = exceptionHandlerO!=null ? exceptionHandlerO :
            RebindExceptionHandlerImpl.builder()
                .danglingRefFailureMode(danglingRefFailureMode)
                .rebindFailureMode(rebindFailureMode)
                .addPolicyFailureMode(addPolicyFailureMode)
                .loadPolicyFailureMode(loadPolicyFailureMode)
                .build();
        final ManagementNodeState mode = modeO!=null ? modeO : getRebindMode();
        
        if (mode!=ManagementNodeState.HOT_STANDBY && mode!=ManagementNodeState.MASTER)
            throw new IllegalStateException("Must be either master or read only to rebind (mode "+mode+")");

        ExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
        if (ec == null) {
            ec = managementContext.getServerExecutionContext();
            Task<List<Application>> task = ec.submit(new Callable<List<Application>>() {
                @Override public List<Application> call() throws Exception {
                    return rebindImpl(classLoader, exceptionHandler, mode);
                }});
            try {
                return task.get();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        } else {
            return rebindImpl(classLoader, exceptionHandler, mode);
        }
    }
    
    @Override
    public BrooklynMementoRawData retrieveMementoRawData() throws IOException {
        RebindExceptionHandler exceptionHandler = RebindExceptionHandlerImpl.builder()
                .danglingRefFailureMode(danglingRefFailureMode)
                .rebindFailureMode(rebindFailureMode)
                .addPolicyFailureMode(addPolicyFailureMode)
                .loadPolicyFailureMode(loadPolicyFailureMode)
                .build();
        
        return loadMementoRawData(exceptionHandler);
    }

    /**
     * Uses the persister to retrieve (and thus deserialize) the memento.
     * 
     * In so doing, it instantiates the entities + locations, registering them with the rebindContext.
     */
    protected BrooklynMementoRawData loadMementoRawData(final RebindExceptionHandler exceptionHandler) throws IOException {
        try {
            if (!(persistenceStoreAccess instanceof BrooklynMementoPersisterToObjectStore)) {
                throw new IllegalStateException("Cannot load raw memento with persister "+persistenceStoreAccess);
            }
            
            return ((BrooklynMementoPersisterToObjectStore)persistenceStoreAccess).loadMementoRawData(exceptionHandler);
            
        } catch (RuntimeException e) {
            throw exceptionHandler.onFailed(e);
        }
    }
    
    protected List<Application> rebindImpl(final ClassLoader classLoader, final RebindExceptionHandler exceptionHandler, ManagementNodeState mode) {
        checkNotNull(classLoader, "classLoader");

        try {
            rebindActive.acquire();
        } catch (InterruptedException e1) { Exceptions.propagate(e1); }
        RebindTracker.setRebinding();
        try {
            exceptionHandler.onStart();
            
            Reflections reflections = new Reflections(classLoader);
            RebindContextImpl rebindContext = new RebindContextImpl(exceptionHandler, classLoader);
            if (mode==ManagementNodeState.HOT_STANDBY) {
                rebindContext.setAllReadOnly();
            } else {
                Preconditions.checkState(mode==ManagementNodeState.MASTER, "Must be either master or read only to rebind (mode "+mode+")");
            }
            
            LookupContext realLookupContext = new RebindContextLookupContext(managementContext, rebindContext, exceptionHandler);
            
            // Mutli-phase deserialization.
            //  1. deserialize just the "manifest" to find all instances (and their types).
            //  2. instantiate entities+locations so that inter-entity references can subsequently be set during deserialize (and entity config/state is set).
            //  3. deserialize the memento
            //  4. instantiate policies+enrichers (could perhaps merge this with (2), depending how they are implemented)
            //  5. reconstruct the entities etc (i.e. calling init on the already-instantiated instances).
            //  6. add policies+enrichers to all the entities.
            //  7. manage the entities
            
            // TODO if underlying data-store is changed between first and second phase (e.g. to add an
            // entity), then second phase might try to reconstitute an entity that has not been put in
            // the rebindContext. This should not affect normal production usage, because rebind is run
            // against a data-store that is not being written to by other brooklyn instance(s).

            
            //
            // PHASE ONE
            //
            
            BrooklynMementoManifest mementoManifest = persistenceStoreAccess.loadMementoManifest(exceptionHandler);
            
            boolean isEmpty = mementoManifest.isEmpty();
            if (!isEmpty) {
                if (mode==ManagementNodeState.HOT_STANDBY)
                    LOG.debug("Rebinding (read-only) from "+getPersister().getBackingStoreDescription()+"...");
                else
                    LOG.info("Rebinding from "+getPersister().getBackingStoreDescription()+"...");
            } else {
                if (mode==ManagementNodeState.HOT_STANDBY)
                    LOG.debug("Rebind check (read-only): no existing state, reading from "+getPersister().getBackingStoreDescription());
                else
                    LOG.info("Rebind check: no existing state; will persist new items to "+getPersister().getBackingStoreDescription());
            }

            
            //
            // PHASE TWO
            //
            
            // Instantiate locations
            LOG.debug("RebindManager instantiating locations: {}", mementoManifest.getLocationIdToType().keySet());
            for (Map.Entry<String, String> entry : mementoManifest.getLocationIdToType().entrySet()) {
                String locId = entry.getKey();
                String locType = entry.getValue();
                if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating location {}", locId);
                
                try {
                    Location location = newLocation(locId, locType, reflections);
                    rebindContext.registerLocation(locId, location);
                } catch (Exception e) {
                    exceptionHandler.onCreateFailed(BrooklynObjectType.LOCATION, locId, locType, e);
                }
            }
            
            // Instantiate entities
            LOG.debug("RebindManager instantiating entities: {}", mementoManifest.getEntityIdToType().keySet());
            for (Map.Entry<String, String> entry : mementoManifest.getEntityIdToType().entrySet()) {
                String entityId = entry.getKey();
                String entityType = entry.getValue();
                if (LOG.isTraceEnabled()) LOG.trace("RebindManager instantiating entity {}", entityId);
                
                try {
                    Entity entity = newEntity(entityId, entityType, reflections);
                    rebindContext.registerEntity(entityId, entity);
                } catch (Exception e) {
                    exceptionHandler.onCreateFailed(BrooklynObjectType.ENTITY, entityId, entityType, e);
                }
            }
            
            
            //
            // PHASE THREE
            //
            
            BrooklynMemento memento = persistenceStoreAccess.loadMemento(realLookupContext, exceptionHandler);

            
            //
            // PHASE FOUR
            //
            
            // Instantiate policies
            if (persistPoliciesEnabled) {
                LOG.debug("RebindManager instantiating policies: {}", memento.getPolicyIds());
                for (PolicyMemento policyMemento : memento.getPolicyMementos().values()) {
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating policy {}", policyMemento);
                    
                    try {
                        Policy policy = newPolicy(policyMemento, reflections);
                        rebindContext.registerPolicy(policyMemento.getId(), policy);
                    } catch (Exception e) {
                        exceptionHandler.onCreateFailed(BrooklynObjectType.POLICY, policyMemento.getId(), policyMemento.getType(), e);
                    }
                }
            } else {
                LOG.debug("Not rebinding policies; feature disabled: {}", memento.getPolicyIds());
            }
            
            // Instantiate enrichers
            if (persistEnrichersEnabled) {
                LOG.debug("RebindManager instantiating enrichers: {}", memento.getEnricherIds());
                for (EnricherMemento enricherMemento : memento.getEnricherMementos().values()) {
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating enricher {}", enricherMemento);

                    try {
                        Enricher enricher = newEnricher(enricherMemento, reflections);
                        rebindContext.registerEnricher(enricherMemento.getId(), enricher);
                    } catch (Exception e) {
                        exceptionHandler.onCreateFailed(BrooklynObjectType.ENRICHER, enricherMemento.getId(), enricherMemento.getType(), e);
                    }
                }
            } else {
                LOG.debug("Not rebinding enrichers; feature disabled: {}", memento.getEnricherIds());
            } 
            
            // Instantiate feeds
            if (persistFeedsEnabled) {
                LOG.debug("RebindManager instantiating feeds: {}", memento.getFeedIds());
                for (FeedMemento feedMemento : memento.getFeedMementos().values()) {
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating feed {}", feedMemento);

                    try {
                        Feed feed = newFeed(feedMemento, reflections);
                        rebindContext.registerFeed(feedMemento.getId(), feed);
                    } catch (Exception e) {
                        exceptionHandler.onCreateFailed(BrooklynObjectType.FEED, feedMemento.getId(), feedMemento.getType(), e);
                    }
                }
            } else {
                LOG.debug("Not rebinding feeds; feature disabled: {}", memento.getFeedIds());
            } 

            // Instantiate catalog items
            if (persistCatalogItemsEnabled) {
                LOG.debug("RebindManager instantiating catalog items: {}", memento.getCatalogItemIds());
                for (CatalogItemMemento catalogItemMemento : memento.getCatalogItemMementos().values()) {
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager instantiating catalog item {}", catalogItemMemento);
                    try {
                        CatalogItem<?, ?> catalogItem = newCatalogItem(catalogItemMemento, reflections);
                        rebindContext.registerCatalogItem(catalogItemMemento.getId(), catalogItem);
                    } catch (Exception e) {
                        exceptionHandler.onCreateFailed(BrooklynObjectType.CATALOG_ITEM, catalogItemMemento.getId(), catalogItemMemento.getType(), e);
                    }
                }
            } else {
                LOG.debug("Not rebinding catalog; feature disabled: {}", memento.getCatalogItemIds());
            }

            //
            // PHASE FIVE
            //
            
            // Reconstruct locations
            LOG.debug("RebindManager reconstructing locations");
            for (LocationMemento locMemento : sortParentFirst(memento.getLocationMementos()).values()) {
                Location location = rebindContext.getLocation(locMemento.getId());
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing location {}", locMemento);
                if (location == null) {
                    // usually because of creation-failure, when not using fail-fast
                    exceptionHandler.onNotFound(BrooklynObjectType.LOCATION, locMemento.getId());
                } else {
                    try {
                        ((LocationInternal)location).getRebindSupport().reconstruct(rebindContext, locMemento);
                    } catch (Exception e) {
                        exceptionHandler.onRebindFailed(BrooklynObjectType.LOCATION, location, e);
                    }
                }
            }

            // Reconstruct policies
            if (persistPoliciesEnabled) {
                LOG.debug("RebindManager reconstructing policies");
                for (PolicyMemento policyMemento : memento.getPolicyMementos().values()) {
                    Policy policy = rebindContext.getPolicy(policyMemento.getId());
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing policy {}", policyMemento);
    
                    if (policy == null) {
                        // usually because of creation-failure, when not using fail-fast
                        exceptionHandler.onNotFound(BrooklynObjectType.POLICY, policyMemento.getId());
                    } else {
                        try {
                            policy.getRebindSupport().reconstruct(rebindContext, policyMemento);
                        } catch (Exception e) {
                            exceptionHandler.onRebindFailed(BrooklynObjectType.POLICY, policy, e);
                            rebindContext.unregisterPolicy(policy);
                        }
                    }
                }
            }

            // Reconstruct enrichers
            if (persistEnrichersEnabled) {
                LOG.debug("RebindManager reconstructing enrichers");
                for (EnricherMemento enricherMemento : memento.getEnricherMementos().values()) {
                    Enricher enricher = rebindContext.getEnricher(enricherMemento.getId());
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing enricher {}", enricherMemento);
        
                    if (enricher == null) {
                        // usually because of creation-failure, when not using fail-fast
                        exceptionHandler.onNotFound(BrooklynObjectType.ENRICHER, enricherMemento.getId());
                    } else {
                        try {
                            enricher.getRebindSupport().reconstruct(rebindContext, enricherMemento);
                        } catch (Exception e) {
                            exceptionHandler.onRebindFailed(BrooklynObjectType.ENRICHER, enricher, e);
                            rebindContext.unregisterEnricher(enricher);
                        }
                    }
                }
            }
    
            // Reconstruct feeds
            if (persistFeedsEnabled) {
                LOG.debug("RebindManager reconstructing feeds");
                for (FeedMemento feedMemento : memento.getFeedMementos().values()) {
                    Feed feed = rebindContext.getFeed(feedMemento.getId());
                    if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing feed {}", feedMemento);
        
                    if (feed == null) {
                        // usually because of creation-failure, when not using fail-fast
                        exceptionHandler.onNotFound(BrooklynObjectType.FEED, feedMemento.getId());
                    } else {
                        try {
                            feed.getRebindSupport().reconstruct(rebindContext, feedMemento);
                        } catch (Exception e) {
                            exceptionHandler.onRebindFailed(BrooklynObjectType.FEED, feed, e);
                            rebindContext.unregisterFeed(feed);
                        }
                    }

                }
            }
    
            // Reconstruct entities
            LOG.debug("RebindManager reconstructing entities");
            for (EntityMemento entityMemento : sortParentFirst(memento.getEntityMementos()).values()) {
                Entity entity = rebindContext.getEntity(entityMemento.getId());
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing entity {}", entityMemento);
    
                if (entity == null) {
                    // usually because of creation-failure, when not using fail-fast
                    exceptionHandler.onNotFound(BrooklynObjectType.ENTITY, entityMemento.getId());
                } else {
                    try {
                        entityMemento.injectTypeClass(entity.getClass());
                        ((EntityInternal)entity).getRebindSupport().reconstruct(rebindContext, entityMemento);
                    } catch (Exception e) {
                        exceptionHandler.onRebindFailed(BrooklynObjectType.ENTITY, entity, e);
                    }
                }
            }

            // Reconstruct catalog entries
            if (persistCatalogItemsEnabled) {
                LOG.debug("RebindManager reconstructing catalog items");
                for (CatalogItemMemento catalogItemMemento : memento.getCatalogItemMementos().values()) {
                    CatalogItem<?, ?> item = rebindContext.getCatalogItem(catalogItemMemento.getId());
                    LOG.debug("RebindManager reconstructing catalog item {}", catalogItemMemento);
                    if (item == null) {
                        exceptionHandler.onNotFound(BrooklynObjectType.CATALOG_ITEM, catalogItemMemento.getId());
                    } else {
                        try {
                            item.getRebindSupport().reconstruct(rebindContext, catalogItemMemento);
                            if (item instanceof AbstractBrooklynObject) {
                                AbstractBrooklynObject.class.cast(item).setManagementContext(managementContext);
                            }
                        } catch (Exception e) {
                            exceptionHandler.onRebindFailed(BrooklynObjectType.CATALOG_ITEM, item, e);
                        }
                    }
                }
            }

            //
            // PHASE SIX
            //
            
            // Associate policies+enrichers with entities
            LOG.debug("RebindManager reconstructing entities");
            for (EntityMemento entityMemento : sortParentFirst(memento.getEntityMementos()).values()) {
                Entity entity = rebindContext.getEntity(entityMemento.getId());
                if (LOG.isDebugEnabled()) LOG.debug("RebindManager reconstructing entity {}", entityMemento);
    
                if (entity == null) {
                    // usually because of creation-failure, when not using fail-fast
                    exceptionHandler.onNotFound(BrooklynObjectType.ENTITY, entityMemento.getId());
                } else {
                    try {
                        entityMemento.injectTypeClass(entity.getClass());
                        // TODO these call to the entity which in turn sets the entity on the underlying feeds and enrichers;
                        // that is taken as the cue to start, but it should not be. start should be a separate call.
                        ((EntityInternal)entity).getRebindSupport().addPolicies(rebindContext, entityMemento);
                        ((EntityInternal)entity).getRebindSupport().addEnrichers(rebindContext, entityMemento);
                        ((EntityInternal)entity).getRebindSupport().addFeeds(rebindContext, entityMemento);
                    } catch (Exception e) {
                        exceptionHandler.onRebindFailed(BrooklynObjectType.ENTITY, entity, e);
                    }
                }
            }
            
            
            //
            // PHASE SEVEN
            //

            LOG.debug("RebindManager managing locations");
            LocationManagerInternal locationManager = (LocationManagerInternal)managementContext.getLocationManager();
            Set<String> oldLocations = Sets.newLinkedHashSet(locationManager.getLocationIds());
            for (Location location: rebindContext.getLocations()) {
                ManagementTransitionMode oldMode = locationManager.getLastManagementTransitionMode(location.getId());
                locationManager.setManagementTransitionMode(location, computeMode(location, oldMode, rebindContext.isReadOnly(location)) );
                if (oldMode!=null)
                    oldLocations.remove(location.getId());
            }
            for (Location location: rebindContext.getLocations()) {
                if (location.getParent()==null) {
                    // manage all root locations
                    try {
                        ((LocationManagerInternal)managementContext.getLocationManager()).manageRebindedRoot(location);
                    } catch (Exception e) {
                        exceptionHandler.onManageFailed(BrooklynObjectType.LOCATION, location, e);
                    }
                }
            }
            // destroy old
            for (String oldLocationId: oldLocations) {
               locationManager.unmanage(locationManager.getLocation(oldLocationId), ManagementTransitionMode.REBINDING_DESTROYED); 
            }
            
            // Manage the top-level apps (causing everything under them to become managed)
            LOG.debug("RebindManager managing entities");
            EntityManagerInternal entityManager = (EntityManagerInternal)managementContext.getEntityManager();
            Set<String> oldEntities = Sets.newLinkedHashSet(entityManager.getEntityIds());
            for (Entity entity: rebindContext.getEntities()) {
                ((EntityInternal)entity).getManagementSupport().setReadOnly( rebindContext.isReadOnly(entity) );
                
                ManagementTransitionMode oldMode = entityManager.getLastManagementTransitionMode(entity.getId());
                entityManager.setManagementTransitionMode(entity, computeMode(entity, oldMode, rebindContext.isReadOnly(entity)) );
                if (oldMode!=null)
                    oldEntities.remove(entity.getId());
            }
            List<Application> apps = Lists.newArrayList();
            for (String appId : memento.getApplicationIds()) {
                Entity entity = rebindContext.getEntity(appId);
                if (entity == null) {
                    // usually because of creation-failure, when not using fail-fast
                    exceptionHandler.onNotFound(BrooklynObjectType.ENTITY, appId);
                } else {
                    try {
                        entityManager.manageRebindedRoot(entity);
                    } catch (Exception e) {
                        exceptionHandler.onManageFailed(BrooklynObjectType.ENTITY, entity, e);
                    }
                    apps.add((Application)entity);
                }
            }
            // destroy old
            for (String oldEntityId: oldEntities) {
               entityManager.unmanage(entityManager.getEntity(oldEntityId), ManagementTransitionMode.REBINDING_DESTROYED); 
            }

            // Register catalogue items with the management context.
            CatalogLoadMode catalogLoadMode = managementContext.getConfig().getConfig(BrooklynServerConfig.CATALOG_LOAD_MODE);
            if (persistCatalogItemsEnabled) {
                boolean shouldResetCatalog = catalogLoadMode == CatalogLoadMode.LOAD_PERSISTED_STATE
                        || (!isEmpty && catalogLoadMode == CatalogLoadMode.LOAD_BROOKLYN_CATALOG_URL_IF_NO_PERSISTED_STATE);
                boolean shouldLoadDefaultCatalog = catalogLoadMode == CatalogLoadMode.LOAD_BROOKLYN_CATALOG_URL
                        || (isEmpty && catalogLoadMode == CatalogLoadMode.LOAD_BROOKLYN_CATALOG_URL_IF_NO_PERSISTED_STATE);
                if (shouldResetCatalog) {
                    // Reset catalog with previously persisted state
                    LOG.debug("RebindManager resetting management context catalog to previously persisted state");
                    managementContext.getCatalog().reset(rebindContext.getCatalogItems());
                } else if (shouldLoadDefaultCatalog) {
                    // Load catalogue as normal
                    // TODO in read-only mode, should do this less frequently than entities etc
                    LOG.debug("RebindManager loading default catalog");
                    ((BasicBrooklynCatalog) managementContext.getCatalog()).resetCatalogToContentsAtConfiguredUrl();
                } else {
                    // Management context should have taken care of loading the catalogue
                    Collection<CatalogItem<?, ?>> catalogItems = rebindContext.getCatalogItems();
                    String message = "RebindManager not resetting catalog to persisted state. Catalog load mode is {}.";
                    if (!catalogItems.isEmpty()) {
                        LOG.info(message + " There {} {} item{} persisted.", new Object[]{
                                catalogLoadMode, catalogItems.size() == 1 ? "was" : "were", catalogItems.size(), Strings.s(catalogItems)});
                    } else if (LOG.isDebugEnabled()) {
                        LOG.debug(message, catalogLoadMode);
                    }
                }
                // TODO destroy old (as above)
            } else {
                LOG.debug("RebindManager not resetting catalog because catalog persistence is disabled");
            }

            exceptionHandler.onDone();

            if (!isEmpty && mode!=ManagementNodeState.HOT_STANDBY) {
                LOG.info("Rebind complete: {} app{}, {} entit{}, {} location{}, {} polic{}, {} enricher{}, {} feed{}, {} catalog item{}", new Object[]{
                    apps.size(), Strings.s(apps),
                    rebindContext.getEntities().size(), Strings.ies(rebindContext.getEntities()),
                    rebindContext.getLocations().size(), Strings.s(rebindContext.getLocations()),
                    rebindContext.getPolicies().size(), Strings.ies(rebindContext.getPolicies()),
                    rebindContext.getEnrichers().size(), Strings.s(rebindContext.getEnrichers()),
                    rebindContext.getFeeds().size(), Strings.s(rebindContext.getFeeds()),
                    rebindContext.getCatalogItems().size(), Strings.s(rebindContext.getCatalogItems())
                });
            }

            // Return the top-level applications
            LOG.debug("RebindManager complete; return apps: {}", memento.getApplicationIds());
            return apps;

        } catch (Exception e) {
            throw exceptionHandler.onFailed(e);
        } finally {
            rebindActive.release();
            RebindTracker.reset();
        }
    }
    
    static ManagementTransitionMode computeMode(BrooklynObject item, ManagementTransitionMode oldMode, boolean isNowReadOnly) {
        return computeMode(item, oldMode==null ? null : oldMode.wasReadOnly(), isNowReadOnly);
    }

    static ManagementTransitionMode computeMode(BrooklynObject item, Boolean wasReadOnly, boolean isNowReadOnly) {
        if (wasReadOnly==null) {
            // not known
            if (Boolean.TRUE.equals(isNowReadOnly)) return ManagementTransitionMode.REBINDING_READONLY;
            else return ManagementTransitionMode.CREATING;
        } else {
            if (wasReadOnly && isNowReadOnly)
                return ManagementTransitionMode.REBINDING_READONLY;
            else if (wasReadOnly)
                return ManagementTransitionMode.REBINDING_BECOMING_PRIMARY;
            else if (isNowReadOnly)
                return ManagementTransitionMode.REBINDING_NO_LONGER_PRIMARY;
            else {
                LOG.warn("Transitioning to master, though never stopped being a master - " + item);
                return ManagementTransitionMode.REBINDING_BECOMING_PRIMARY;
            }
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
        for (T node : nodes.values()) {
            List<T> tempchain = Lists.newLinkedList();
            
            T nodeinchain = node;
            while (nodeinchain != null) {
                tempchain.add(0, nodeinchain);
                nodeinchain = (nodeinchain.getParent() == null) ? null : nodes.get(nodeinchain.getParent());
            }
            for (T n : tempchain) {
                result.put(n.getId(), n);
            }
        }
        return result;
    }

    private Entity newEntity(String entityId, String entityType, Reflections reflections) {
        Class<? extends Entity> entityClazz = reflections.loadClass(entityType, Entity.class);
        
        if (InternalFactory.isNewStyle(entityClazz)) {
            // Not using entityManager.createEntity(EntitySpec) because don't want init() to be called.
            // Creates an uninitialized entity, but that has correct id + proxy.
            InternalEntityFactory entityFactory = managementContext.getEntityFactory();
            Entity entity = entityFactory.constructEntity(entityClazz, Reflections.getAllInterfaces(entityClazz), entityId);
            
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
        Class<? extends Location> locationClazz = reflections.loadClass(locationType, Location.class);

        if (InternalFactory.isNewStyle(locationClazz)) {
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
    @SuppressWarnings("unchecked")
    private Policy newPolicy(PolicyMemento memento, Reflections reflections) {
        String id = memento.getId();
        String policyType = checkNotNull(memento.getType(), "policy type of %s must not be null in memento", id);
        Class<? extends Policy> policyClazz = (Class<? extends Policy>) reflections.loadClass(policyType);

        if (InternalFactory.isNewStyle(policyClazz)) {
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
        Class<? extends Enricher> enricherClazz = reflections.loadClass(enricherType, Enricher.class);

        if (InternalFactory.isNewStyle(enricherClazz)) {
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

    /**
     * Constructs a new enricher, passing to its constructor the enricher id and all of memento.getConfig().
     */
    private Feed newFeed(FeedMemento memento, Reflections reflections) {
        String id = memento.getId();
        String feedType = checkNotNull(memento.getType(), "feed type of %s must not be null in memento", id);
        Class<? extends Feed> feedClazz = reflections.loadClass(feedType, Feed.class);

        if (InternalFactory.isNewStyle(feedClazz)) {
            InternalPolicyFactory policyFactory = managementContext.getPolicyFactory();
            Feed feed = policyFactory.constructFeed(feedClazz);
            FlagUtils.setFieldsFromFlags(ImmutableMap.of("id", id), feed);
            ((AbstractFeed)feed).setManagementContext(managementContext);
            
            return feed;

        } else {
            throw new IllegalStateException("rebind of feed without no-arg constructor unsupported: id="+id+"; type="+feedType);
        }
    }

    @SuppressWarnings({ "rawtypes" })
    private CatalogItem<?, ?> newCatalogItem(CatalogItemMemento memento, Reflections reflections) {
        String id = memento.getId();
        String itemType = checkNotNull(memento.getType(), "catalog item type of %s must not be null in memento", id);
        Class<? extends CatalogItem> clazz = reflections.loadClass(itemType, CatalogItem.class);
        return invokeConstructor(reflections, clazz, new Object[]{});
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
        public void onManaged(BrooklynObject instance) {
            try {
                delegate.onManaged(instance);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onManaged("+instance+"); continuing.", t);
            }
        }

        @Override
        public void onChanged(BrooklynObject instance) {
            try {
                delegate.onChanged(instance);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onChanged("+instance+"); continuing.", t);
            }
        }
        
        @Override
        public void onUnmanaged(BrooklynObject instance) {
            try {
                delegate.onUnmanaged(instance);
            } catch (Throwable t) {
                LOG.error("Error persisting mememento onUnmanaged("+instance+"); continuing.", t);
            }
        }
    }
    
    @Override
    public String toString() {
        return super.toString()+"[mgmt="+managementContext.getManagementNodeId()+"]";
    }
}
