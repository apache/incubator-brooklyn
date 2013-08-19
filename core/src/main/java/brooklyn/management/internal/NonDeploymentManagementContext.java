package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.rebind.ChangeListener;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.location.LocationRegistry;
import brooklyn.management.EntityManager;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.LocationManager;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.task.AbstractExecutionContext;

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
    private final NonDeploymentExecutionContext executionContext;
    private NonDeploymentEntityManager entityManager;
    private NonDeploymentLocationManager locationManager;

    public NonDeploymentManagementContext(AbstractEntity entity, NonDeploymentManagementContextMode mode) {
        this.entity = checkNotNull(entity, "entity");
        this.mode = checkNotNull(mode, "mode");
        qsm = new QueueingSubscriptionManager();
        subscriptionContext = new BasicSubscriptionContext(qsm, entity);
        executionContext = new NonDeploymentExecutionContext();
        entityManager = new NonDeploymentEntityManager(null);
        locationManager = new NonDeploymentLocationManager(null);
    }
    
    public void setManagementContext(ManagementContextInternal val) {
        this.initialManagementContext = checkNotNull(val, "initialManagementContext");
        this.entityManager = new NonDeploymentEntityManager(val);
        this.locationManager = new NonDeploymentLocationManager(val);
    }

    @Override
    public String toString() {
        return super.toString()+"["+entity+";"+mode+"]";
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
    public EntityManager getEntityManager() {
        return entityManager;
    }
    
    @Override
    public LocationManager getLocationManager() {
        return locationManager;
    }

    @Override
    public ExecutionManager getExecutionManager() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: executions cannot be performed prior to management");
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
        return executionContext;
    }

    // TODO the methods below should delegate to the application?
    @Override
    public EntityDriverManager getEntityDriverManager() {
        checkInitialManagementContextReal();
        return initialManagementContext.getEntityDriverManager();
    }

    @Override
    public EntityDriverManager getEntityDriverFactory() {
        return getEntityDriverManager();
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
    public Collection<Entity> getEntities() {
        return getEntityManager().getEntities();
    }

    @Override
    public Entity getEntity(String id) {
        return getEntityManager().getEntity(id);
    }

    @Override
    public boolean isManaged(Entity entity) {
        return getEntityManager().isManaged(entity);
    }

    @Override
    public void manage(Entity e) {
        getEntityManager().manage(e);
    }

    @Override
    public void unmanage(Entity e) {
        getEntityManager().unmanage(e);
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
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }
    
    private void checkInitialManagementContextReal() {
        if (!isInitialManagementContextReal()) {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
    }

    private class NonDeploymentExecutionContext extends AbstractExecutionContext {
        @Override
        public Set<Task<?>> getTasks() {
            return Collections.emptySet();
        }
        
        @Override
        public Task<?> getCurrentTask() {
            return null;
        }

        @Override
        protected <T> Task<T> submitInternal(Map<?, ?> properties, Object task) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }
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
        public BrooklynMementoPersister getPersister() {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public List<Application> rebind(BrooklynMemento memento) {
            throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
        }

        @Override
        public List<Application> rebind(BrooklynMemento memento, ClassLoader classLoader) {
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
    }
}
