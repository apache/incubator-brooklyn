package brooklyn.management;

/**
 * This is the contexst through which an entity can manage subscriptions.
 */
public interface SubscriptionContext {
    /**
     * Returns the current {@link SubscriptionManager} instance.
     */
    SubscriptionManager getSubscriptionManager();
}