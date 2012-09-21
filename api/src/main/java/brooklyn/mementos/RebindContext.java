package brooklyn.mementos;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

public interface RebindContext {

    public Entity getEntity(String id);

    public Location getLocation(String id);
}
