package brooklyn.management.internal;

import java.util.Collection
import java.util.Set

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionContext
import brooklyn.management.ExecutionManager
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionManager
import brooklyn.util.task.BasicExecutionContext
import brooklyn.util.task.BasicExecutionManager

/**
 * A local implementation of the {@link ManagementContext} API.
 */
public class LocalManagementContext extends AbstractManagementContext {
    private static final Logger log = LoggerFactory.getLogger(ManagementContext.class)

    private ExecutionManager execution = new BasicExecutionManager();
    private SubscriptionManager subscriptions = new LocalSubscriptionManager(execution);
    
    Set<Application> apps = []
 
    public static ManagementContext getContext() { return new LocalManagementContext() }
                             
    public void registerApplication(Application app) {
        apps.add(app);
    }
    
    public Collection<Application> getApplications() {
        return apps
    }
    
    public Entity getEntity(String id) {
        return null;
    }

    public ExecutionManager getExecutionManager() { return execution; }
 
    public SubscriptionManager getSubscriptionManager() { return subscriptions; }
 
    @Override
    public ExecutionContext getExecutionContext(Entity e) { return new BasicExecutionContext(tag:e, execution); }
 
    @Override
    public SubscriptionContext getSubscriptionContext(Entity e) { return new BasicSubscriptionContext(getSubscriptionManager(), e); }
}
