package brooklyn.entity.drivers.downloads;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.location.Location;

public class MyEntityDriver implements EntityDriver {
    private final Entity entity;
    private final Location location;

    MyEntityDriver(Entity entity, Location location) {
        this.entity = entity;
        this.location = location;
    }
    
    @Override
    public EntityLocal getEntity() {
        return (EntityLocal) entity;
    }

    @Override
    public Location getLocation() {
        return location;
    }
}
