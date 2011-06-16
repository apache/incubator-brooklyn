package brooklyn.management.internal;

public class LocalManagementContext {
    SubscriptionManager subscriptions = new LocalSubscriptionManager();
    
    SubscriptionManager getSubscriptionManager() { return subscriptions; }
}
