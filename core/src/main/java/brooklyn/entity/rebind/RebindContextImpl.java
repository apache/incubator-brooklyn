package brooklyn.entity.rebind;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Policy;

import com.google.common.collect.Maps;

public class RebindContextImpl implements RebindContext {

    private final Map<String, Entity> entities = Maps.newLinkedHashMap();
    private final Map<String, Location> locations = Maps.newLinkedHashMap();
    private final Map<String, Policy> policies = Maps.newLinkedHashMap();
    private final ClassLoader classLoader;
    
    public RebindContextImpl(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void registerEntity(Entity entity) {
        entities.put(entity.getId(), entity);
    }
    
    public void registerLocation(Location location) {
        locations.put(location.getId(), location);
    }
    
    public void registerPolicy(Policy policy) {
        policies.put(policy.getId(), policy);
    }
    
    @Override
    public Entity getEntity(String id) {
        return entities.get(id);
    }

    @Override
    public Location getLocation(String id) {
        return locations.get(id);
    }
    
    @Override
    public Policy getPolicy(String id) {
        return policies.get(id);
    }
    
    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return classLoader.loadClass(className);
    }
}
