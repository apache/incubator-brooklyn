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
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.policy.Policy
import brooklyn.util.task.BasicExecutionContext

import com.google.common.base.Preconditions

/**
 * Base {@link Policy} implementation; all policies should extend this or its children
 */
public abstract class AbstractPolicy extends AbstractEntityAdjunct implements Policy {
    private static final Logger log = LoggerFactory.getLogger(AbstractPolicy.class);

    String policyStatus;
    protected String name;
    protected Map leftoverProperties
    protected AtomicBoolean suspended = new AtomicBoolean(false)

    protected transient ExecutionContext execution
    
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

    public void suspend() {suspended.set(true)}
    public void resume() {suspended.set(false)}
    public boolean isSuspended() {return suspended.get()}

    public void destroy(){
        suspend()
        super.destroy()
    }

    @Override
    public boolean isRunning() {
        return !isSuspended() && !isDestroyed()
    }
}
