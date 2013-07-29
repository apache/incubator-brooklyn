package brooklyn.management.internal;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import brooklyn.internal.storage.BrooklynStorageFactory;
import brooklyn.internal.storage.impl.inmemory.InMemoryBrooklynStorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.ExecutionManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionManager;
import brooklyn.management.Task;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.text.Identifiers;

/**
 * A local implementation of the {@link ManagementContext} API.
 */
public class LocalManagementContext extends AbstractManagementContext {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(LocalManagementContext.class);

    private BasicExecutionManager execution;
    private SubscriptionManager subscriptions;
    private LocalEntityManager entityManager;
    private final LocalLocationManager locationManager;
    
    private final String shortid = Identifiers.getBase64IdFromValue(System.identityHashCode(this), 5);
    private final String tostring = "LocalManagementContext("+shortid+")";

    /**
     * Creates a LocalManagement with default BrooklynProperties.
     */
    public LocalManagementContext() {
        this(BrooklynProperties.Factory.newDefault());
    }

    public LocalManagementContext(BrooklynProperties brooklynProperties) {
        this(brooklynProperties, new InMemoryBrooklynStorageFactory());
    }

    public LocalManagementContext(BrooklynProperties brooklynProperties, BrooklynStorageFactory storageFactory) {
        super(brooklynProperties,storageFactory);
        configMap.putAll(checkNotNull(brooklynProperties, "brooklynProperties"));
        this.locationManager = new LocalLocationManager(this);
    }
    
    public void prePreManage(Entity entity) {
        getEntityManager().prePreManage(entity);
    }

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
    public synchronized LocalLocationManager getLocationManager() {
        if (!isRunning()) throw new IllegalStateException("Management context no longer running");
        return locationManager;
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
            execution = new BasicExecutionManager(shortid);
            gc = new BrooklynGarbageCollector(configMap, execution);
        }
        return execution;
    }
    
    @Override
    public void terminate() {
        super.terminate();
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
    public boolean isManagedLocally(Entity e) {
        return true;
    }

    @Override
    public String toString() {
        return tostring;
    }
}
