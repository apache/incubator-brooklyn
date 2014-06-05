package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

/**
 * Listener to be notified of changes within brooklyn, so that the new state
 * of the entity/location/policy can be persisted.
 * 
 * Users are not expected to implement this class. It is for use by the {@link RebindManager}.
 * 
 * @author aled
 */
public interface ChangeListener {

    public static final ChangeListener NOOP = new ChangeListener() {
        @Override public void onManaged(Entity entity) {}
        @Override public void onUnmanaged(Entity entity) {}
        @Override public void onChanged(Entity entity) {}
        @Override public void onManaged(Location location) {}
        @Override public void onUnmanaged(Location location) {}
        @Override public void onChanged(Location location) {}
        @Override public void onManaged(Policy policy) {}
        @Override public void onUnmanaged(Policy policy) {}
        @Override public void onChanged(Policy policy) {}
        @Override public void onManaged(Enricher enricher) {}
        @Override public void onUnmanaged(Enricher enricher) {}
        @Override public void onChanged(Enricher enricher) {}
    };
    
    void onManaged(Entity entity);
    
    void onUnmanaged(Entity entity);
    
    void onChanged(Entity entity);
    
    void onManaged(Location location);

    void onUnmanaged(Location location);

    void onChanged(Location location);
    
    void onManaged(Policy policy);

    void onUnmanaged(Policy policy);

    void onChanged(Policy policy);
    
    void onManaged(Enricher enricher);

    void onUnmanaged(Enricher enricher);

    void onChanged(Enricher enricher);
}
