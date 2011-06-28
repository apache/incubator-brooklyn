package brooklyn.management.internal;

import java.util.Collection
import java.util.Set

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionManager
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionManager;
import brooklyn.util.task.BasicExecutionManager
import brooklyn.util.task.ExecutionContext

/**
 * A local implementation of the {@link ManagementContext} API.
 */
public class LocalManagementContext extends AbstractManagementContext {
    private static final Logger log = LoggerFactory.getLogger(ManagementContext.class)

    private SubscriptionManager subscriptions = new LocalSubscriptionManager();
    private ExecutionManager execution = new BasicExecutionManager();
    
    Set<Application> apps = []
 
    public static ManagementContext getContext() { return new LocalManagementContext() }
                             
    @Override
    public void registerApplication(Application app) {
        apps.add(app);
    }
    
    @Override
    public Collection<Application> getApplications() {
        return apps
    }
    
    @Override
    public Entity getEntity(String id) {
        // TODO Auto-generated method stub
        return null;
    }

    public SubscriptionManager getSubscriptionManager() { return subscriptions; }

    public ExecutionManager getExecutionManager() { return execution; }
 
    public ExecutionContext getExecutionContext(Entity e) { return new ExecutionContext(tag: e, execution); }
    
}
