package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

public interface RebindContext {

    public Entity getEntity(String id);

    public Location getLocation(String id);

    public Class<?> loadClass(String typeName) throws ClassNotFoundException;
}
