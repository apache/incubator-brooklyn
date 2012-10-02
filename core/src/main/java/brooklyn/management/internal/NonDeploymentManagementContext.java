package brooklyn.management.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigMap.StringConfigMap;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.drivers.EntityDriverFactory;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.util.task.AbstractExecutionContext;

public class NonDeploymentManagementContext implements ManagementContext {

    public enum NonDeploymentManagementContextMode {
        PRE_MANAGEMENT,
        MANAGEMENT_STARTING,
        MANAGEMENT_STARTED,
        MANAGEMENT_STOPPING,
        MANAGEMENT_STOPPED,
    }
    
    AbstractEntity entity;
    NonDeploymentManagementContextMode mode;

    QueueingSubscriptionManager qsm = new QueueingSubscriptionManager();
    BasicSubscriptionContext subcon;
    
    public NonDeploymentManagementContext(AbstractEntity entity, NonDeploymentManagementContextMode mode) {
        this.entity = entity;
        this.mode = mode;
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
    public ExecutionManager getExecutionManager() {
        throw new IllegalStateException("Executions cannot be performed prior to management. (Non-deployment context "+this+" is not valid for this operation.)");
    }

    @Override
    public QueueingSubscriptionManager getSubscriptionManager() {
        return qsm;
    }

    @Override
    public synchronized SubscriptionContext getSubscriptionContext(Entity entity) {
        assert entity == this.entity : "NonDeployment context can only use a single Entity";
        if (subcon==null) subcon = new BasicSubscriptionContext(qsm, entity);
        return subcon;
    }

    @Override
    public ExecutionContext getExecutionContext(Entity entity) {
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
                throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
            }
        };
    }

    @Override
    public void manage(Entity e) {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    @Override
    public void unmanage(Entity e) {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

    // TODO the methods below should delegate to the application?
    @Override
    public EntityDriverFactory getEntityDriverFactory() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }
    @Override
    public StringConfigMap getConfig() {
        throw new IllegalStateException("Non-deployment context "+this+" is not valid for this operation.");
    }

}
