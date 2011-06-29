package brooklyn.policy.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.EventListener
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
   
   public AbstractPolicy() { }

   public void setEntity(EntityLocal entity) {
       this.entity = entity;
       this.subscription = new BasicSubscriptionContext(getManagementContext().getSubscriptionManager(), this)
       this.execution = new BasicExecutionContext([tags:[entity,this]], getManagementContext().getExecutionManager())
   }
   
   /** @see SubscriptionContext#subscribe(Entity, Sensor, EventListener) */
   public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener) {
       subscription.subscribe producer, sensor, listener
   }
   
   private ManagementContext getManagementContext() {
       entity.getManagementContext();
   }
}
