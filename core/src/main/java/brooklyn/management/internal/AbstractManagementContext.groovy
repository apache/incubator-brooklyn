package brooklyn.management.internal;

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.util.task.ExecutionContext

public abstract class AbstractManagementContext implements ManagementContext {
    
    public abstract void registerApplication(Application app);
    
    public ExecutionContext getExecutionContext(Entity e) { 
        return new ExecutionContext(tag: e, getExecutionManager());
    }
    
    public SubscriptionContext getSubscriptionContext(Entity e) {
        new BasicSubscriptionContext(getSubscriptionManager(), e);
    }

}
