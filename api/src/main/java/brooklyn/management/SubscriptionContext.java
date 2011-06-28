package brooklyn.management;

/**
 * This is the context through which an {@link Entity} can manage subscriptions.
 */
public interface SubscriptionContext {
    /**
     * Returns the current {@link SubscriptionManager} instance.
     */
    SubscriptionManager getSubscriptionManager();
    
    //FIXME SUBS  should expose subcribe which is customised for a particular entity; publish should check that event.source == this.entity
}