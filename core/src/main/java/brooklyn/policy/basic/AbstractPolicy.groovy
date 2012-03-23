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
    private static final Logger log = LoggerFactory.getLogger(AbstractPolicy.class)

    protected String policyStatus
    protected Map leftoverProperties
    protected AtomicBoolean suspended = new AtomicBoolean(false)

    protected transient ExecutionContext execution

    public AbstractPolicy(Map properties = [:]) {
        if (properties.name) {
            name = properties.remove "name"
        } else if (properties.displayName) {
            name = properties.remove "displayName"
        } else if (getClass().getSimpleName()) {
            name = getClass().getSimpleName()
        }
        if (properties.id) {
            id = properties.remove "id"
        }
        leftoverProperties = properties
    }

    public void suspend() {
        suspended.set(true)
    }

    public void resume() {
        suspended.set(false)
    }

    public boolean isSuspended() {
        return suspended.get().booleanValue()
    }

    @Override
    public void destroy(){
        suspend()
        super.destroy()
    }

    @Override
    public boolean isRunning() {
        return !isSuspended() && !isDestroyed()
    }
}
