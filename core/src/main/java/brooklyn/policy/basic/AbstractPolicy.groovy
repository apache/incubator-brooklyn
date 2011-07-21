package brooklyn.policy.basic

import java.util.Map;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.SensorEventListener
import brooklyn.event.Sensor
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.internal.BasicSubscriptionContext
import brooklyn.policy.Policy
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.task.BasicExecutionContext;

/**
 * Default {@link Policy} implementation.
 */
class AbstractPolicy implements Policy {
   private static final Logger log = LoggerFactory.getLogger(AbstractPolicy.class);

   String id = LanguageUtils.newUid();
   String displayName;
   
   protected transient EntityLocal entity
   protected transient ExecutionContext execution
   protected transient SubscriptionContext subscription
   
   private Map<Entity, SubscriptionHandle> subscriptions = new HashMap<Entity, SubscriptionHandle>()
   
   public AbstractPolicy() { }

   public void setEntity(EntityLocal entity) {
       this.entity = entity;
       this.subscription = new BasicSubscriptionContext(getManagementContext().getSubscriptionManager(), this)
       this.execution = new BasicExecutionContext([tags:[entity,this]], getManagementContext().getExecutionManager())
   }
   
   /** @see SubscriptionContext#subscribe(Entity, Sensor, EventListener) */
   protected <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<T> listener) {
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
       def handle = subscriptions.remove(producer)
       if (handle) subscription.unsubscribe(handle)
   }
   
   private ManagementContext getManagementContext() {
       entity.getManagementContext();
   }
}
