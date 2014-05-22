package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
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

    Entity getEntity(String id);

    Location getLocation(String id);

    Policy getPolicy(String id);

    Enricher getEnricher(String id);

    Class<?> loadClass(String typeName) throws ClassNotFoundException;

}
