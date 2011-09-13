package brooklyn.policy;

/**
 * Marker interface, indicating that this is a policy that can be associated with an entity.
 */
public interface Policy {
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
     * methods for actions which can be executed against a policy
     */

    void suspend();

    void resume();

    void destroy();

    /**
     * Methods for checking policy status
     */
    Boolean isSuspended();

    Boolean isDestroyed();
}
