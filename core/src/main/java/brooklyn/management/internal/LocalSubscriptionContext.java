package brooklyn.management.internal;

import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionManager;

/**
 * A {@link SubscriptionContext} that uses the {@link LocalSubscriptionManager}.
 */
public class LocalSubscriptionContext implements SubscriptionContext {
    private static SubscriptionManager manager;
    
  //FIXME SUBS  should specify a manager _and_ the entity who is publishing subscribing
    
    public SubscriptionManager getSubscriptionManager() {
        synchronized (LocalSubscriptionContext.class) {
            if (manager == null) manager = new LocalSubscriptionManager();
        }
        
        return manager;
    }
}
