package brooklyn.policy;

/**
 * Marker interface, indicating that this is a policy that can be associated with an entity.
 */
public interface Policy {
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
     * Returns <code>true</code> iff this location contains a property with the specified <code>key</code>. The
     * property's value can be obtained by calling {@link #getLocationProperty}. This method only interrogates the
     * immediate properties; the parent hierarchy is NOT searched in the event that the property is not found locally.
     */
    boolean hasPolicyProperty(String key);
    
    /**
     * Returns the value of the property identified by the specified <code>key</code>. This method only interrogates the
     * immediate properties; the parent hierarchy is NOT searched in the event that the property is not found locally.
     * 
     * NOTE: must not name this method 'getProperty' as this will clash with the 'magic' Groovy's method of the same
     *       name, at which point everything stops working!
     */
    Object getPolicyProperty(String key);
    
    /**
     * Like {@link #getLocationProperty}, but if the property is not defined on this location, searches recursively up
     * the parent hierarchy until it is found, or the root is reached (when this method will return <code>null</code>).
     */
    Object findPolicyProperty(String key);

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
