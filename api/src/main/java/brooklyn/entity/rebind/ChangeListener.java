package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

public interface ChangeListener {

    void onManaged(Entity entity);
    
    void onUnmanaged(Entity entity);
    
    void onChanged(Entity entity);
    
    void onManaged(Location location);

    void onUnmanaged(Location location);

    void onChanged(Location location);
}
