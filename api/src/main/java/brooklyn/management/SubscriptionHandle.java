package brooklyn.management;

/**
 * A "receipt" returned by {@link SubscriptionContext} and {@link SubscriptionManager}'s {@code subscribe()} 
 * methods. It can be used to unsubscribe - see {@link SubscriptionContext#unsubscribe(SubscriptionHandle)} 
 * and {@link SubscriptionManager#unsubscribe(SubscriptionHandle)}.
 */
public interface SubscriptionHandle {
}
