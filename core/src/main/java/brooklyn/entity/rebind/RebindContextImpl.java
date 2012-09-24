package brooklyn.entity.rebind;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.mementos.BrooklynMemento;

import com.google.common.collect.Maps;

public class RebindContextImpl implements RebindContext {

    private final Map<String, Entity> entities = Maps.newLinkedHashMap();
    private final Map<String, Location> locations = Maps.newLinkedHashMap();
    private final BrooklynMemento memento;
    
    public RebindContextImpl(BrooklynMemento memento) {
        this.memento = memento;
//        for (Application app : applications) {
//            applicationIds.add(app.getId());
//        }
//        for (Entity entity : managementContext.getEntities()) {
//            entities.put(entity.getId(), entity.getMemento());
//            
//            for (Location location : entity.getLocations()) {
//                if (!locations.containsKey(location.getId())) {
//                    locations.put(location.getId(), location.getMemento());
//                }
//            }
//        }
    }

    public void registerEntity(String id, Entity entity) {
        entities.put(id, entity);
    }
    
    public void registerLocation(String id, Location location) {
        locations.put(id, location);
    }
    
    @Override
    public Entity getEntity(String id) {
        return entities.get(id);
    }

    @Override
    public Location getLocation(String id) {
        return locations.get(id);
    }
}
