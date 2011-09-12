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
import com.google.common.base.Preconditions
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.task.BasicExecutionContext;

/**
 * Base {@link Policy} implementation; all policies should extend this or its children
 */
public abstract class BasePolicy extends AbstractEntityAdjunct implements Policy {
    private static final Logger log = LoggerFactory.getLogger(BasePolicy.class);

    String policyStatus;
    protected String name;
    protected Map leftoverProperties
    protected boolean suspended

    protected transient ExecutionContext execution

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
        super.setEntity(entity)
        this.execution = new BasicExecutionContext([tags:[entity,this]], getManagementContext().getExecutionManager())
    }

    public void resume() {suspended = false}
    public void suspend() {suspended = true}
    public boolean isSuspended() {return suspended}
    public boolean hasPolicyProperty(String key) { return leftoverProperties.containsKey(key); }
    public Object getPolicyProperty(String key) { return leftoverProperties.get(key); }
    public Object findPolicyProperty(String key) {
        if (hasPolicyProperty(key)) return getPolicyProperty(key);
        return null;
    }

}
