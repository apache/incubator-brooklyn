package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.LocationRegistry;
import brooklyn.management.EntityManager;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.util.task.AbstractExecutionContext;

public class NonDeploymentManagementContext implements ManagementContext, ManagementContextInternal {

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
    
    private final QueueingSubscriptionManager qsm = new QueueingSubscriptionManager();
    private BasicSubscriptionContext subcon;
    
    public NonDeploymentManagementContext(AbstractEntity entity, NonDeploymentManagementContextMode mode) {
        this.entity = entity;
        this.mode = mode;
    }
    
    public void setManagementContext(ManagementContextInternal val) {
        this.initialManagementContext = checkNotNull(val, "initialManagementContext");
    }

    @Override
    public String toString() {
        return super.toString()+"["+entity+";"+mode+"]";
    }
    
    public void setMode(NonDeploymentManagementContextMode mode) {
        this.mode = mode;
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
        return new NonDeploymentEntityManager(initialManagementContext == null ? this : initialManagementContext);
    }
    
    @Override
    public ExecutionManager getExecutionManager() {
        throw new IllegalStateException("Executions cannot be performed prior to management. (Non-deployment context "+this+" is not valid for this operation.)");
    }

    @Override
    public QueueingSubscriptionManager getSubscriptionManager() {
        return qsm;
    }

    @Override
    public synchronized SubscriptionContext getSubscriptionContext(Entity entity) {
        if (entity != this.entity) throw new IllegalStateException("NonDeployment context can only use a single Entity: has "+this.entity+", but passed "+entity);
        
        if (subcon==null) subcon = new BasicSubscriptionContext(qsm, entity);
        return subcon;
    }

    @Override
    public ExecutionContext getExecutionContext(Entity entity) {
        if (entity != this.entity) throw new IllegalStateException("NonDeployment context can only use a single Entity: has "+this.entity+", bug passed "+entity);
        
        return new AbstractExecutionContext() {
            @Override
            public Set<Task<?>> getTasks() {
                return Collections.emptySet();
            }
            
            @Override
            public Task<?> getCurrentTask() {
                return null;
            }
            
            @Override
            protected <T> Task<T> submitInternal(@SuppressWarnings("rawtypes") Map properties, Object task) {
                throw new IllegalStateException("Non-deployment context "+NonDeploymentManagementContext.this+" is not valid for this operation.");
            }
        };
    }

    // TODO the methods below should delegate to the application?
    @Override
    public EntityDriverManager getEntityDriverManager() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    @Override
    public EntityDriverManager getEntityDriverFactory() {
        return getEntityDriverManager();
    }

    @Override
    public DownloadResolverManager getEntityDownloadsManager() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    @Override
    public StringConfigMap getConfig() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getConfig();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
    }

    @Override
    public RebindManager getRebindManager() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    @Override
    public LocationRegistry getLocationRegistry() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    @Override
    public BrooklynCatalog getCatalog() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }
    
    @Override
    public Collection<Entity> getEntities() {
        return Collections.emptyList();
    }

    @Override
    public Entity getEntity(String id) {
        return null;
    }

    @Override
    public boolean isManaged(Entity entity) {
        return false;
    }

    @Override
    public void manage(Entity e) {
        if (e != entity) throw new IllegalStateException("NonDeployment context can only use a single Entity: has "+this.entity+", bug passed "+entity);
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    @Override
    public void unmanage(Entity e) {
        if (e != entity) throw new IllegalStateException("NonDeployment context can only use a single Entity: has "+this.entity+", bug passed "+entity);
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation: cannot unmanage "+e);
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
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getBaseClassLoader();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
    }

    @Override
    public Iterable<URL> getBaseClassPathForScanning() {
        if (isInitialManagementContextReal()) {
            return initialManagementContext.getBaseClassPathForScanning();
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
    }

    @Override
    public void addEntitySetListener(CollectionChangeListener<Entity> listener) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.addEntitySetListener(listener);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
    }

    @Override
    public void removeEntitySetListener(CollectionChangeListener<Entity> listener) {
        if (isInitialManagementContextReal()) {
            initialManagementContext.removeEntitySetListener(listener);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
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
        if (isInitialManagementContextReal()) {
            initialManagementContext.setBaseClassPathForScanning(urls);
        } else {
            throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
        }
    }
    
    private boolean isInitialManagementContextReal() {
        return (initialManagementContext != null && !(initialManagementContext instanceof NonDeploymentManagementContext));
    }
}
