package brooklyn.policy.basic

import java.util.Map
import java.util.concurrent.atomic.AtomicBoolean

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEventListener
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.internal.BasicSubscriptionContext
import brooklyn.policy.EntityAdjunct
import brooklyn.util.internal.LanguageUtils


/**
 * Common functionality for policies and enrichers
 */
abstract class AbstractEntityAdjunct implements EntityAdjunct {
    String id = LanguageUtils.newUid();
    String name;
    
    protected transient EntityLocal entity
    protected transient SubscriptionContext subscription
    private AtomicBoolean destroyed = new AtomicBoolean(false)
    
    private Map<Entity, SubscriptionHandle> subscriptions = new LinkedHashMap<Entity, SubscriptionHandle>()

    public String getName() { return name; }
    public String getId() { return id; }
    
    public void setEntity(EntityLocal entity) {
        if (destroyed.get()) throw new IllegalStateException("Cannot set entity on a destroyed entity adjunct")
        this.entity = entity;
        this.subscription = new BasicSubscriptionContext(entity.getManagementContext().getSubscriptionManager(), this)
    }
    
    /** @see SubscriptionContext#subscribe(Entity, Sensor, EventListener) */
    protected <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<T> listener) {
        if (destroyed.get()) return null
        def handle = subscription.subscribe producer, sensor, listener
        subscriptions.put(producer, handle)
        return handle
    }
    
    /**
     * Unsubscribes the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    protected boolean unsubscribe(Entity producer) {
        if (destroyed.get()) return
        def handle = subscriptions.remove(producer)
        if (handle) subscription.unsubscribe(handle)
    }
    
    /**
    * @return an ordered list of all subscription handles
    */
   protected Collection<SubscriptionHandle> getAllSubscriptions() {
       return Collections.unmodifiableCollection(subscriptions.values())
   }
    
    protected ManagementContext getManagementContext() {
        entity.getManagementContext();
    }
    
    /** 
     * Unsubscribes and clears all managed subscriptions; is called by the owning entity when a policy is removed
     * and should always be called by any subclasses overriding this method
     */
    public void destroy() {
        destroyed.set(true)
        subscriptions.values().each { subscription.unsubscribe(it) }
        subscriptions.clear()
    }
    
    @Override
    public boolean isDestroyed() {
        return destroyed.get().booleanValue()
    }
    
    @Override
    public boolean isRunning() {
        return !isDestroyed().booleanValue()
    }
}
