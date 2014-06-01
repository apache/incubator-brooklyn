package brooklyn.policy;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.entity.trait.Configurable;
import brooklyn.mementos.PolicyMemento;

import com.google.common.annotations.Beta;

/**
 * Policies implement actions and thus must be suspendable; policies should continue to evaluate their sensors
 * and indicate their desired planned action even if they aren't invoking them
 */
public interface Policy extends EntityAdjunct, Rebindable, Configurable {
    /**
     * A unique id for this policy.
     */
    @Override
    String getId();

    /**
     * Get the name assigned to this policy.
     *
     * @return the name assigned to the policy.
     */
    @Override
    String getName();

    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    @Beta
    PolicyType getPolicyType();

    /**
     * Resume the policy
     */
    void resume();

    /**
     * Suspend the policy
     */
    void suspend();
    
    /**
     * Whether the policy is suspended
     */
    boolean isSuspended();
    
    <T> T getConfig(ConfigKey<T> key);
    
    <T> T setConfig(ConfigKey<T> key, T val);
    
    Map<ConfigKey<?>, Object> getAllConfig();

    @Override
    RebindSupport<PolicyMemento> getRebindSupport();
}
