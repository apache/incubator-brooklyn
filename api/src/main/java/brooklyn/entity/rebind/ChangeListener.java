package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Policy;

public interface ChangeListener {

    public static final ChangeListener NOOP = new ChangeListener() {
        @Override public void onManaged(Entity entity) {}
        @Override public void onUnmanaged(Entity entity) {}
        @Override public void onChanged(Entity entity) {}
        @Override public void onManaged(Location location) {}
        @Override public void onUnmanaged(Location location) {}
        @Override public void onChanged(Location location) {}
        @Override public void onChanged(Policy policy) {}
    };
    
    void onManaged(Entity entity);
    
    void onUnmanaged(Entity entity);
    
    void onChanged(Entity entity);
    
    void onManaged(Location location);

    void onUnmanaged(Location location);

    void onChanged(Location location);
    
    void onChanged(Policy policy);
}
