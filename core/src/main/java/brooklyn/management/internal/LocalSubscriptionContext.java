package brooklyn.management.internal;

import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionManager;

/**
 * A {@link SUbscriptionContext} that uses the {@link LocalSubscriptionManager}.
 */
public class LocalSubscriptionContext implements SubscriptionContext {
    private static SubscriptionManager manager;
    
    public SubscriptionManager getSubscriptionManager() {
        if (manager != null) return manager;
        
        synchronized (LocalSubscriptionContext.class) {
            manager = new LocalSubscriptionManager();
        }
        
        return manager;
    }
}
