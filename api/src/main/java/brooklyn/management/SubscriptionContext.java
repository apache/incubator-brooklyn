package brooklyn.management;

/**
 * This is the context through which an {@link Entity} can manage subscriptions.
 */
public interface SubscriptionContext {
    /**
     * Returns the current {@link SubscriptionManager} instance.
     */
    SubscriptionManager getSubscriptionManager();
}