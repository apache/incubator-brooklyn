package brooklyn.management.internal;

import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionManager;

/**
 * A {@link SubscriptionContext} for an entitiy or other user of a {@link SubscriptionManager}.
 */
public class BasicSubscriptionContext implements SubscriptionContext {
	
    private SubscriptionManager manager;
    private Object subscriptionId;
    
  //FIXME SUBS  should specify a manager _and_ the entity who is publishing subscribing

    public BasicSubscriptionContext(SubscriptionManager m, Object subscriptionId) {
    	this.manager = m;
    	this.subscriptionId = subscriptionId;
    }
    
    public SubscriptionManager getSubscriptionManager() { manager }
    
}
