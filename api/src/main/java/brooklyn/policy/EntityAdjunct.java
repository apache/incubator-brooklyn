package brooklyn.policy;

public interface EntityAdjunct {
    
    /**
     * A unique id for this location.
     */
    String getId();

    /**
     * Get the name assigned to this location.
     *
     * @return the name assigned to the location.
     */
    String getName();
    
    /**
     * destroy the policy
     */
    void destroy();
    
    /**
     * whether the adjunct is destroyed
     */
    Boolean isDestroyed();

}
