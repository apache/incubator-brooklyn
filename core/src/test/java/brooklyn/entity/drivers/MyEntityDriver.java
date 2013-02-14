package brooklyn.entity.drivers;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;

import com.google.common.collect.Maps;

public class MyEntityDriver implements EntityDriver {
    private final Entity entity;
    private final Location location;
    private final Map<String, String> flags = Maps.newConcurrentMap();

    MyEntityDriver(Entity entity, Location location) {
        this.entity = entity;
        this.location = location;
    }
    
    public void setFlag(String key, String val) {
        flags.put(key, val);
    }
    
    @Override
    public EntityLocal getEntity() {
        System.out.println("Calling getEntity");
        return (EntityLocal) entity;
    }

    @Override
    public Location getLocation() {
        return location;
    }

    public String getDownloadFilename() {
        return flags.get("downloadFilename");
    }
    
    public String getDownloadFileSuffix() {
        return flags.get("downloadFileSuffix");
    }

}
