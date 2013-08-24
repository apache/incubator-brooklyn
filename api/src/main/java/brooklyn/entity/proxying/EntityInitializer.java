package brooklyn.entity.proxying;

import brooklyn.entity.basic.EntityLocal;

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
