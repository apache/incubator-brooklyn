package brooklyn.entity.rebind;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

import com.google.common.collect.Maps;

public class RebindContextImpl implements RebindContext {

    private final Map<String, Entity> entities = Maps.newLinkedHashMap();
    private final Map<String, Location> locations = Maps.newLinkedHashMap();
    private final Map<String, Policy> policies = Maps.newLinkedHashMap();
    private final Map<String, Enricher> enrichers = Maps.newLinkedHashMap();
    private final ClassLoader classLoader;
    
    public RebindContextImpl(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void registerEntity(String id, Entity entity) {
        entities.put(id, entity);
    }
    
    public void registerLocation(String id, Location location) {
        locations.put(id, location);
    }
    
    public void registerPolicy(String id, Policy policy) {
        policies.put(id, policy);
    }
    
    public void registerEnricher(String id, Enricher enricher) {
        enrichers.put(id, enricher);
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
    public Enricher getEnricher(String id) {
        return enrichers.get(id);
    }
    
    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return classLoader.loadClass(className);
    }
}
