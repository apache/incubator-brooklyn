package brooklyn.entity.proxying;

import java.util.Map;

import brooklyn.entity.basic.EntityLocal;

/** 
 * Instances of this class supply logic which can be used to initialize entities. 
 * These can be added to an {@link EntitySpec} programmatically, or declared as part
 * of YAML recipes in a <code>brooklyn.initializers</code> section.
 * In the case of the latter, implementing classes should define a no-arg constructor
 * or a {@link Map} constructor so that YAML parameters can be supplied.
 **/ 
public interface EntityInitializer {
    
    /** Applies initialization logic to a just-built entity.
     * Invoked immediately after the "init" call on the AbstractEntity constructed.
     * 
     * @param entity guaranteed to be the actual implementation instance, 
     * thus guaranteed to be castable to EntityInternal which is often desired,
     * or to the type at hand (it is not even a proxy)
     */
    public void apply(EntityLocal entity);
    
}
