package brooklyn.management.internal;

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.util.task.BasicExecutionContext

public abstract class AbstractManagementContext implements ManagementContext {
    public abstract void registerApplication(Application app);
    
    public ExecutionContext getExecutionContext(Entity e) { 
        new BasicExecutionContext(tag:e, getExecutionManager());
    }
    
    public SubscriptionContext getSubscriptionContext(Entity e) {
        new BasicSubscriptionContext(getSubscriptionManager(), e);
    }
}
