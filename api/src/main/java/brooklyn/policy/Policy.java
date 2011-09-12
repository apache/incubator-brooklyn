package brooklyn.policy;

/**
 * Policy interface, indicating that this is a full policy that can be suspended and resumed
 */
public interface Policy extends EntityAdjunct {
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
     * resume the policy
     */
    void resume();

    void destroy();

    Boolean isDestroyed();

    /**
     * suspend the policy
     */
    void suspend();
    
    /**
     * whether the policy is suspended
     */
    Boolean isSuspended();
}
