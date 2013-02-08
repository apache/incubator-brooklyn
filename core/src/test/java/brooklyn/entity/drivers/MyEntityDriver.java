package brooklyn.entity.drivers;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;

class MyEntityDriver implements EntityDriver {
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