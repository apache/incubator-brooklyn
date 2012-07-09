package brooklyn.policy.basic;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.internal.BasicSubscriptionContext;
import brooklyn.management.internal.SubscriptionTracker;
import brooklyn.policy.EntityAdjunct;
import brooklyn.util.IdGenerator;
import brooklyn.util.flags.SetFromFlag;


/**
 * Common functionality for policies and enrichers
 */
public abstract class AbstractEntityAdjunct implements EntityAdjunct {
    @SetFromFlag
    protected String id = IdGenerator.makeRandomId(8);
    
    @SetFromFlag
    protected String name;
    
    protected transient EntityLocal entity;
    /** not for direct access; refer to as 'subscriptionTracker' via getter so that it is initialized */
    protected transient SubscriptionTracker _subscriptionTracker;
    private AtomicBoolean destroyed = new AtomicBoolean(false);

    public String getName() { 
        if (name!=null && name.length()>0) return name;
        return getClass().getCanonicalName();
    }
    public void setName(String name) { this.name = name; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
 
    public void setEntity(EntityLocal entity) {
        if (destroyed.get()) throw new IllegalStateException("Cannot set entity on a destroyed entity adjunct");
        this.entity = entity;
    }
    
    protected synchronized SubscriptionTracker getSubscriptionTracker() {
        if (_subscriptionTracker!=null) return _subscriptionTracker;
        if (entity==null) return null;
        if (entity.getManagementContext()==null) return null;
        BasicSubscriptionContext subscriptionContext = new BasicSubscriptionContext(entity.getManagementContext().getSubscriptionManager(), this);
        _subscriptionTracker = new SubscriptionTracker(subscriptionContext);
        return _subscriptionTracker;
    }
    
    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    protected <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!check(entity)) return null;
        return getSubscriptionTracker().subscribe(producer, sensor, listener);
    }

    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    protected <T> SubscriptionHandle subscribeToMembers(Group producerGroup, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!check(producerGroup)) return null;
        return getSubscriptionTracker().subscribeToMembers(producerGroup, sensor, listener);
    }

    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    protected <T> SubscriptionHandle subscribeToChildren(Entity producerParent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!check(producerParent)) return null;
        return getSubscriptionTracker().subscribeToChildren(producerParent, sensor, listener);
    }

    /** returns false if deleted, throws exception if invalid state, otherwise true */
    protected boolean check(Entity producer) {
        if (destroyed.get()) return false;
        if (entity==null) throw new IllegalStateException(this+" cannot subscribe to "+producer+" because it is not associated to an entity");
        if (entity.getManagementContext()==null) throw new IllegalStateException(this+" cannot subscribe to "+producer+" because the associated entity "+entity+" is not yet managed");
        return true;
    }
        
    /**
     * Unsubscribes the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    protected boolean unsubscribe(Entity producer) {
        if (destroyed.get()) return false;
        return getSubscriptionTracker().unsubscribe(producer);
    }

    /**
    * Unsubscribes the given producer.
    *
    * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
    */
   protected boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
       if (destroyed.get()) return false;
       return getSubscriptionTracker().unsubscribe(producer, handle);
   }

    /**
    * @return a list of all subscription handles
    */
    protected Collection<SubscriptionHandle> getAllSubscriptions() {
        SubscriptionTracker tracker = getSubscriptionTracker();
        return (tracker != null) ? tracker.getAllSubscriptions() : Collections.<SubscriptionHandle>emptyList();
    }
    
    protected ManagementContext getManagementContext() {
        return entity.getManagementContext();
    }
    
    /** 
     * Unsubscribes and clears all managed subscriptions; is called by the owning entity when a policy is removed
     * and should always be called by any subclasses overriding this method
     */
    public void destroy() {
        destroyed.set(true);
        SubscriptionTracker tracker = getSubscriptionTracker();
        if (tracker != null) tracker.unsubscribeAll();
    }
    
    @Override
    public boolean isDestroyed() {
        return destroyed.get();
    }
    
    @Override
    public boolean isRunning() {
        return !isDestroyed();
    }
}
