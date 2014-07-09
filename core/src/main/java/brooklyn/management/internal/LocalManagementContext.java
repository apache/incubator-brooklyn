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

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynProperties.Factory.Builder;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.drivers.downloads.BasicDownloadsManager;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.internal.storage.DataGridFactory;
import brooklyn.location.Location;
import brooklyn.management.AccessController;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionManager;
import brooklyn.management.Task;
import brooklyn.management.ha.OsgiManager;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * A local (single node) implementation of the {@link ManagementContext} API.
 */
public class LocalManagementContext extends AbstractManagementContext {
    
    private static final Logger log = LoggerFactory.getLogger(LocalManagementContext.class);

    private static final Set<LocalManagementContext> INSTANCES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<LocalManagementContext, Boolean>()));
    
    private final Builder builder;
    
    private final List<ManagementContext.PropertiesReloadListener> reloadListeners = new CopyOnWriteArrayList<ManagementContext.PropertiesReloadListener>();

    @VisibleForTesting
    static Set<LocalManagementContext> getInstances() {
        synchronized (INSTANCES) {
            return ImmutableSet.copyOf(INSTANCES);
        }
    }

    // Note also called reflectively by BrooklynLeakListener
    public static void logAll(Logger logger){
        for (LocalManagementContext context : getInstances()) {
            logger.warn("Management Context "+context+" running, creation stacktrace:\n" + Throwables.getStackTraceAsString(context.constructionStackTrace));
        }
    }

    /** terminates all (best effort); returns count of sessions closed; if exceptions thrown, returns negative number.
     * semantics might change, particular in dealing with interminable mgmt contexts. */
    // Note also called reflectively by BrooklynLeakListener
    @Beta
    public static int terminateAll() {
        int closed=0,dangling=0;
        for (LocalManagementContext context : getInstances()) {
            try {
                context.terminate();
                closed++;
            }catch (Throwable t) {
                Exceptions.propagateIfFatal(t);
                log.warn("Failed to terminate management context", t);
                dangling++;
            }
        }
        if (dangling>0) return -dangling;
        return closed;
    }

    private String managementPlaneId;
    private String managementNodeId;
    private BasicExecutionManager execution;
    private SubscriptionManager subscriptions;
    private LocalEntityManager entityManager;
    private final LocalLocationManager locationManager;
    private final LocalAccessManager accessManager;
    private final LocalUsageManager usageManager;
    private OsgiManager osgiManager;
    
    public final Throwable constructionStackTrace = new Throwable("for construction stacktrace").fillInStackTrace();
    
    private final Map<String, Object> brooklynAdditionalProperties;

    /**
     * Creates a LocalManagement with default BrooklynProperties.
     */
    public LocalManagementContext() {
        this(new Builder());
    }

    public LocalManagementContext(BrooklynProperties brooklynProperties) {
        this(brooklynProperties, (DataGridFactory)null);
    }

    /**
     * Creates a new LocalManagementContext.
     *
     * @param brooklynProperties the BrooklynProperties.
     * @param datagridFactory the DataGridFactory to use. If this instance is null, it means that the system
     *                        is going to use BrooklynProperties to figure out which instance to load or otherwise
     *                        use a default instance.
     */
    @VisibleForTesting
    public LocalManagementContext(BrooklynProperties brooklynProperties, DataGridFactory datagridFactory) {
        this(Builder.fromProperties(brooklynProperties), datagridFactory);
    }
    
    public LocalManagementContext(Builder builder) {
        this(builder, null, null);
    }
    
    public LocalManagementContext(Builder builder, DataGridFactory datagridFactory) {
        this(builder, null, datagridFactory);
    }

    public LocalManagementContext(Builder builder, Map<String, Object> brooklynAdditionalProperties) {
        this(builder, brooklynAdditionalProperties, null);
    }
    
    public LocalManagementContext(BrooklynProperties brooklynProperties, Map<String, Object> brooklynAdditionalProperties) {
        this(Builder.fromProperties(brooklynProperties), brooklynAdditionalProperties, null);
    }
    
    public LocalManagementContext(Builder builder, Map<String, Object> brooklynAdditionalProperties, DataGridFactory datagridFactory) {
        super(builder.build(), datagridFactory);
        // TODO in a persisted world the planeId may be injected
        this.managementPlaneId = Strings.makeRandomId(8);
        
        this.managementNodeId = Strings.makeRandomId(8);
        checkNotNull(configMap, "brooklynProperties");
        this.builder = builder;
        this.brooklynAdditionalProperties = brooklynAdditionalProperties;
        if (brooklynAdditionalProperties != null)
            configMap.addFromMap(brooklynAdditionalProperties);
        
        this.locationManager = new LocalLocationManager(this);
        this.accessManager = new LocalAccessManager();
        this.usageManager = new LocalUsageManager(this);
        
        if (configMap.getConfig(OsgiManager.USE_OSGI)) {
            this.osgiManager = new OsgiManager();
            osgiManager.start();
        }
        
        INSTANCES.add(this);
        log.debug("Created management context "+this);
    }

    @Override
    public String getManagementPlaneId() {
        return managementPlaneId;
    }
    
    @Override
    public String getManagementNodeId() {
        return managementNodeId;
    }
    
    @Override
    public void prePreManage(Entity entity) {
        getEntityManager().prePreManage(entity);
    }

    @Override
    public void prePreManage(Location location) {
        getLocationManager().prePreManage(location);
    }

    @Override
    public synchronized Collection<Application> getApplications() {
        return getEntityManager().getApplications();
    }

    @Override
    public void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        getEntityManager().addEntitySetListener(listener);
    }

    @Override
    public void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        getEntityManager().removeEntitySetListener(listener);
    }

    @Override
    protected void manageIfNecessary(Entity entity, Object context) {
        getEntityManager().manageIfNecessary(entity, context);
    }

    @Override
    public synchronized LocalEntityManager getEntityManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");

        if (entityManager == null) {
            entityManager = new LocalEntityManager(this);
        }
        return entityManager;
    }

    @Override
    public InternalEntityFactory getEntityFactory() {
        return getEntityManager().getEntityFactory();
    }

    @Override
    public InternalLocationFactory getLocationFactory() {
        return getLocationManager().getLocationFactory();
    }

    @Override
    public InternalPolicyFactory getPolicyFactory() {
        return getEntityManager().getPolicyFactory();
    }

    @Override
    public synchronized LocalLocationManager getLocationManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return locationManager;
    }

    @Override
    public synchronized LocalAccessManager getAccessManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return accessManager;
    }

    @Override
    public synchronized LocalUsageManager getUsageManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return usageManager;
    }
    
    @Override
    public synchronized Maybe<OsgiManager> getOsgiManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return Maybe.of(osgiManager);
    }

    @Override
    public synchronized AccessController getAccessController() {
        return getAccessManager().getAccessController();
    }
    
    @Override
    public synchronized  SubscriptionManager getSubscriptionManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");

        if (subscriptions == null) {
            subscriptions = new LocalSubscriptionManager(getExecutionManager());
        }
        return subscriptions;
    }

    @Override
    public synchronized ExecutionManager getExecutionManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");

        if (execution == null) {
            execution = new BasicExecutionManager(getManagementNodeId());
            gc = new BrooklynGarbageCollector(configMap, execution, getStorage());
        }
        return execution;
    }

    @Override
    public void terminate() {
        INSTANCES.remove(this);
        super.terminate();
        if (osgiManager!=null) {
            osgiManager.stop();
            osgiManager = null;
        }
        if (execution != null) execution.shutdownNow();
        if (gc != null) gc.shutdownNow();
    }

    @Override
    protected void finalize() {
        terminate();
    }

    @Override
    public <T> Task<T> runAtEntity(@SuppressWarnings("rawtypes") Map flags, Entity entity, Callable<T> c) {
		manageIfNecessary(entity, elvis(Arrays.asList(flags.get("displayName"), flags.get("description"), flags, c)));
        return getExecutionContext(entity).submit(flags, c);
    }

    
    @Override
    protected <T> Task<T> runAtEntity(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters) {
        manageIfNecessary(entity, eff);
        // prefer to submit this from the current execution context so it sets up correct cross-context chaining
        ExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
        if (ec == null) {
            log.debug("Top-level effector invocation: {} on {}", eff, entity);
            ec = getExecutionContext(entity);
        }
        return ec.submit(Effectors.invocation(entity, eff, parameters));
    }

    @Override
    public boolean isManagedLocally(Entity e) {
        return true;
    }

    @Override
    public String toString() {
        return LocalManagementContext.class.getSimpleName()+"["+getManagementPlaneId()+"-"+getManagementNodeId()+"]";
    }

    @Override
    public void reloadBrooklynProperties() {
        log.info("Reloading brooklyn properties from " + builder);
        if (builder.hasDelegateOriginalProperties())
            log.warn("When reloading, mgmt context "+this+" properties are fixed, so reload will be of limited utility");
        
        BrooklynProperties properties = builder.build();
        configMap = properties;
        if (brooklynAdditionalProperties != null) {
            log.info("Reloading additional brooklyn properties from " + brooklynAdditionalProperties);
            configMap.addFromMap(brooklynAdditionalProperties);
        }
        this.downloadsManager = BasicDownloadsManager.newDefault(configMap);

        // Force reload of location registry
        this.locationRegistry = null;
        
        // Notify listeners that properties have been reloaded
        for (PropertiesReloadListener listener : reloadListeners) {
            listener.reloaded();
        }
    }

    @Override
    public void addPropertiesReloadListener(PropertiesReloadListener listener) {
        reloadListeners.add(checkNotNull(listener, "listener"));
    }

    @Override
    public void removePropertiesReloadListener(PropertiesReloadListener listener) {
        reloadListeners.remove(listener);
    }
}
