package brooklyn.policy.basic

import java.util.Map
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEventListener
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.internal.BasicSubscriptionContext
import brooklyn.policy.Policy
import brooklyn.util.task.BasicExecutionContext

import com.google.common.base.Preconditions

/**
 * Default {@link Policy} implementation.
 */
public abstract class AbstractPolicy implements Policy {
   private static final Logger log = LoggerFactory.getLogger(AbstractPolicy.class);

    String policyStatus;
    protected String name;
    protected Map leftoverProperties
    protected AtomicBoolean suspended = new AtomicBoolean(false)
   
   protected transient EntityLocal entity
   protected transient ExecutionContext execution
   protected transient SubscriptionContext subscription
   
   private Map<Entity, SubscriptionHandle> subscriptions = new HashMap<Entity, SubscriptionHandle>()
   
   public AbstractPolicy(Map properties = [:]) {
        if (properties.name) {
            Preconditions.checkArgument properties.name instanceof String, "'name' property should be a string"
            name = properties.name
        } else if (properties.displayName) {
            Preconditions.checkArgument properties.displayName instanceof String, "'displayName' property should be a string"
            name = properties.displayName
        }
        if (properties.id) {
            Preconditions.checkArgument properties.id == null || properties.id instanceof String,
                "'id' property should be a string"
            id = properties.remove("id")
        }
        leftoverProperties = properties

   }

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
   public String getName() { return name; }
   public String getId() { return id; }

   /**
    * Unsubscribes the given producer. 
    *
    * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
    */
   protected boolean unsubscribe(Entity producer) {
       def handle = subscriptions.remove(producer)
       if (handle) subscription.unsubscribe(handle)
   }
   
   public void suspend(){
   //TODO:implement suspend policy action
        suspended = true;
        destroyed = false;
   }
   public void resume(){
   //TODO:implement pause policy action
        suspended = false;
        destroyed = false;
   }
   public void destroy(){
   //TODO:implement destroy policy action
        destroyed = true;
        suspended = false;
   }

   public Boolean isDestroyed() { return destroyed; }

   private ManagementContext getManagementContext() {
       entity.getManagementContext();
   }

    public Boolean isSuspended() {return suspended.get()}
    
    @Override
    public boolean isRunning() {
        return !isSuspended() && !isDestroyed()
    }
}
