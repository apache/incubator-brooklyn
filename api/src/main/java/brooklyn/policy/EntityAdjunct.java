package brooklyn.policy;

/**
 * EntityAdjuncts are supplementary logic that can be attached to Entities, providing sensor enrichment
 * or enabling policy
 */
public interface EntityAdjunct {
    
    /**
     * A unique id for this adjunct
     */
    String getId();

    /**
     * Get the name assigned to this adjunct
     *
     * @return the name assigned to the adjunct
     */
    String getName();
    
    /**
     * destroy the adjunct
     */
    void destroy();
    
    /**
     * whether the adjunct is destroyed
     */
    Boolean isDestroyed();

}
