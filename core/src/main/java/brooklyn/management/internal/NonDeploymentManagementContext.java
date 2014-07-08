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

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.proxying.InternalLocationFactory;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.entity.rebind.ChangeListener;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.management.AccessController;
import brooklyn.management.EntityManager;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.LocationManager;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.management.entitlement.EntitlementManager;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.ha.HighAvailabilityManager;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersister;
import brooklyn.management.ha.OsgiManager;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.guava.Maybe;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;

public class NonDeploymentManagementContext implements ManagementContextInternal {

    public enum NonDeploymentManagementContextMode {
        PRE_MANAGEMENT,
        MANAGEMENT_REBINDING,
        MANAGEMENT_STARTING,
        MANAGEMENT_STARTED,
        MANAGEMENT_STOPPING,
        MANAGEMENT_STOPPED;
        
        public boolean isPreManaged() {
            return this == PRE_MANAGEMENT || this == MANAGEMENT_REBINDING;
        }
    }
    
    private final AbstractEntity entity;
    private NonDeploymentManagementContextMode mode;
    private ManagementContextInternal initialManagementContext;
    
    private final QueueingSubscriptionManager qsm;
    private final BasicSubscriptionContext subscriptionContext;
    private NonDeploymentEntityManager entityManager;
    private NonDeploymentLocationManager locationManager;
    private NonDeploymentAccessManager accessManager;
    private NonDeploymentUsageManager usageManager;
    private EntitlementManager entitlementManager;;

    public NonDeploymentManagementContext(AbstractEntity entity, NonDeploymentManagementContextMode mode) {
        this.entity = checkNotNull(entity, "entity");
        this.mode = checkNotNull(mode, "mode");
        qsm = new QueueingSubscriptionManager();
        subscriptionContext = new BasicSubscriptionContext(qsm, entity);
        entityManager = new NonDeploymentEntityManager(null);
        locationManager = new NonDeploymentLocationManager(null);
        accessManager = new NonDeploymentAccessManager(null);
        usageManager = new NonDeploymentUsageManager(null);
        
        // TODO might need to be some kind of "system" which can see that the system is running at this point
        // though quite possibly we are entirely behind the auth-wall at this point
        entitlementManager = Entitlements.minimal();
    }

    @Override
    public String getManagementPlaneId() {
        return (initialManagementContext == null) ? null : initialManagementContext.getManagementPlaneId();
    }
    
    @Override
    public String getManagementNodeId() {
        return (initialManagementContext == null) ? null : initialManagementContext.getManagementNodeId();
    }

    @Override
    public Maybe<URI> getManagementNodeUri() {
        return (initialManagementContext == null) ? Maybe.<URI>absent() : initialManagementContext.getManagementNodeUri();
    }

    public void setManagementContext(ManagementContextInternal val) {
        this.initialManagementContext = checkNotNull(val, "initialManagementContext");
        this.entityManager = new NonDeploymentEntityManager(val);
        this.locationManager = new NonDeploymentLocationManager(val);
        this.accessManager = new NonDeploymentAccessManager(val);
        this.usageManager = new NonDeploymentUsageManager(val);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("entity", entity.getId()).add("mode", mode).toString();
    }
    
    public void setMode(NonDeploymentManagementContextMode mode) {
        this.mode = checkNotNull(mode, "mode");
    }
    public NonDeploymentManagementContextMode getMode() {
        return mode;
    }
    
    @Override
    public Collection<Application> getApplications() {
        return Collections.emptyList();
    }

    @Override
    public boolean isRunning() {
        // Assume that the real management context has not been terminated, so always true
        return true;
    }

    @Override
    public InternalEntityFactory getEntityFactory() {
        checkInitialManagementContextReal();
        return initialManagementContext.getEntityFactory();
    }

    @Override
    public InternalLocationFactory getLocationFactory() {
        checkInitialManagementContextReal();
        return initialManagementContext.getLocationFactory();
    }

    @Override
    public InternalPolicyFactory getPolicyFactory() {
        checkInitialManagementContextReal();
        return initialManagementContext.getPolicyFactory();
    }

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }
    
    @Override
    public LocationManager getLocationManager() {
        return locationManager;
    }

    @Override
    public AccessManager getAccessManager() {
        return accessManager;
    }

    @Override
    public UsageManager getUsageManager() {
        return usageManager;
    }
    
    @Override
    public Maybe<OsgiManager> getOsgiManager() {
        return Maybe.absent();
    }

    @Override
    public AccessController getAccessController() {
        return getAccessManager().getAccessController();
    }

    @Override
    public ExecutionManager getExecutionManager() {
        checkInitialManagementContextReal();
        return initialManagementContext.getExecutionManager();
    }

    @Override
    public QueueingSubscriptionManager getSubscriptionManager() {
        return qsm;
    }

    @Override
    public synchronized SubscriptionContext getSubscriptionContext(Entity entity) {
        if (!this.entity.equals(entity)) throw new IllegalStateException("Non-deployment context "+this+" can only use a single Entity: has "+this.entity+", but passed "+entity);
        return subscriptionContext;
    }

    @Override
    public ExecutionContext getExecutionContext(Entity entity) {
        if (!this.entity.equals(entity)) throw new IllegalStateException("Non-deployment context "+this+" can only use a single Entity: has "+this.entity+", but passed "+entity);
        checkInitialManagementContextReal();
        return initialManagementContext.getExecutionContext(entity);
    }

    // TODO the methods below should delegate to the application?
    @Override
    public EntityDriverManager getEntityDriverManager() {
        checkInitialManagementContextReal();
        return initialManagementContext.getEntityDriverManager();
    }

    @Override
    public DownloadResolverManager getEntityDownloadsManager() {
        checkInitialManagementContextReal();
        return initialManagementContext.getEntityDownloadsManager();
    }

    @Override
    public StringConfigMap getConfig() {
        checkInitialManagementContextReal();
        return initialManagementContext.getConfig();
    }

    @Override
    public BrooklynProperties getBrooklynProperties() {
        checkInitialManagementContextReal();
        return initialManagementContext.getBrooklynProperties();
    }

    @Override
    public BrooklynStorage getStorage() {
        checkInitialManagementContextReal();
        return initialManagementContext.getStorage();
    }
    
    @Override
    public RebindManager getRebindManager() {
        // There was a race where EffectorUtils on invoking an effector calls:
        //     mgmtSupport.getEntityChangeListener().onEffectorCompleted(eff);
        // but where the entity/app may be being unmanaged concurrently (e.g. calling app.stop()).
        // So now we allow the change-listener to be called.
        
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getRebindManager();
        } else {
            return new NonDeploymentRebindManager();
        }
    }

    @Override
    public HighAvailabilityManager getHighAvailabilityManager() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getHighAvailabilityManager();
        } else {
            return new NonDeploymentHighAvailabilityManager();
        }
    }

    @Override
    public LocationRegistry getLocationRegistry() {
        checkInitialManagementContextReal();
        return initialManagementContext.getLocationRegistry();
    }

    @Override
    public BrooklynCatalog getCatalog() {
        checkInitialManagementContextReal();
        return initialManagementContext.getCatalog();
    }
    
    @Override
    public EntitlementManager getEntitlementManager() {
        return entitlementManager;
    }
    
    @Override
    public <T> T invokeEffectorMethodSync(final Entity entity, final Effector<T> eff, final Object args) throws ExecutionException {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot invoke effector "+eff+" on entity "+entity);
    }
    
    @Override
    public <T> Task<T> invokeEffector(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters) {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot invoke effector "+eff+" on entity "+entity);
    }

    @Override
    public ClassLoader getBaseClassLoader() {
        checkInitialManagementContextReal();
        return initialManagementContext.getBaseClassLoader();
    }

    @Override
    public Iterable<URL> getBaseClassPathForScanning() {
        checkInitialManagementContextReal();
        return initialManagementContext.getBaseClassPathForScanning();
    }

    @Override
    public void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        checkInitialManagementContextReal();
        initialManagementContext.addEntitySetListener(listener);
    }

    @Override
    public void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        checkInitialManagementContextReal();
        initialManagementContext.removeEntitySetListener(listener);
    }

    @Override
    public void terminate() {
        if (isInitialManagementContextReal()) {
            initialManagementContext.terminate();
        } else {
            // no-op; the non-deployment management context has nothing needing terminated
        }
    }

    @Override
    public long getTotalEffectorInvocations() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getTotalEffectorInvocations();
        } else {
            return 0;
        }
    }

    @Override
    public void setBaseClassPathForScanning(Iterable<URL> urls) {
        checkInitialManagementContextReal();
        initialManagementContext.setBaseClassPathForScanning(urls);
    }

    @Override
    public void setManagementNodeUri(URI uri) {
        checkInitialManagementContextReal();
        initialManagementContext.setManagementNodeUri(uri);
    }

    @Override
    public void prePreManage(Entity entity) {
        // no-op
    }

    @Override
    public void prePreManage(Location location) {
        // no-op
    }

    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }
    
    private void checkInitialManagementContextReal() {
        if (!isInitialManagementContextReal()) {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
    }
    
    @Override
    public void reloadBrooklynProperties() {
        checkInitialManagementContextReal();
        initialManagementContext.reloadBrooklynProperties();
    }
    
    @Override
    public void addPropertiesReloadListener(PropertiesReloadListener listener) {
        checkInitialManagementContextReal();
        initialManagementContext.addPropertiesReloadListener(listener);
    }
    
    @Override
    public void removePropertiesReloadListener(PropertiesReloadListener listener) {
        checkInitialManagementContextReal();
        initialManagementContext.removePropertiesReloadListener(listener);
    }

    /**
     * For when the initial management context is not "real"; the changeListener is a no-op, but everything else forbidden.
     * 
     * @author aled
     */
    private class NonDeploymentRebindManager implements RebindManager {

        @Override
        public ChangeListener getChangeListener() {
            return ChangeListener.NOOP;
        }

        @Override
        public void setPersister(BrooklynMementoPersister persister) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public void setPersister(BrooklynMementoPersister persister, PersistenceExceptionHandler exceptionHandler) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public BrooklynMementoPersister getPersister() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public List<Application> rebind() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public List<Application> rebind(ClassLoader classLoader) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public List<Application> rebind(ClassLoader classLoader, RebindExceptionHandler exceptionHandler) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public void start() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        
        @Override
        public void stop() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public void waitForPendingComplete(Duration timeout) throws InterruptedException, TimeoutException {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public void forcePersistNow() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
    }

    /**
     * For when the initial management context is not "real".
     * 
     * @author aled
     */
    private class NonDeploymentHighAvailabilityManager implements HighAvailabilityManager {
        @Override
        public ManagementNodeState getNodeState() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public boolean isRunning() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public HighAvailabilityManager setPersister(ManagementPlaneSyncRecordPersister persister) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public void disabled() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public void start(HighAvailabilityMode startMode) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public void stop() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public ManagementPlaneSyncRecordPersister getPersister() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
        @Override
        public ManagementPlaneSyncRecord getManagementPlaneSyncState() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
    }
}
