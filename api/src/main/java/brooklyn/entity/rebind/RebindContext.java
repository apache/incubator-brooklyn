package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Policy;

/**
 * Gives access to things that are being currently rebinding. This is used during a
 * rebind to wire everything back together again, e.g. to find the necessary entity 
 * instances even before they are available through 
 * {@code managementContext.getEntityManager().getEnties()}.
 * 
 * Users are not expected to implement this class. It is for use by {@link Rebindable} 
 * instances, and will generally be created by the {@link RebindManager}.
 */
public interface RebindContext {

    public Entity getEntity(String id);

    public Location getLocation(String id);

    public Policy getPolicy(String id);

    public Class<?> loadClass(String typeName) throws ClassNotFoundException;
}
