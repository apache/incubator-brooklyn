package brooklyn.policy;

import brooklyn.entity.rebind.Rebindable;
import brooklyn.mementos.PolicyMemento;

/**
 * Policies implement actions and thus must be suspendable; policies should continue to evaluate their sensors
 * and indicate their desired planned action even if they aren't invoking them
 */
public interface Policy extends EntityAdjunct, Rebindable<PolicyMemento> {
    /**
     * A unique id for this policy.
     */
    String getId();

    /**
     * Get the name assigned to this policy.
     *
     * @return the name assigned to the policy.
     */
    String getName();

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
}
