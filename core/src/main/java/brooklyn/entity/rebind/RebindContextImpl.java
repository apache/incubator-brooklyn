package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

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
    private final RebindExceptionHandler exceptionHandler;
    
    public RebindContextImpl(RebindExceptionHandler exceptionHandler, ClassLoader classLoader) {
        this.exceptionHandler = checkNotNull(exceptionHandler, "exceptionHandler");
        this.classLoader = checkNotNull(classLoader, "classLoader");
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
    
    public void unregisterPolicy(Policy policy) {
        policies.remove(policy.getId());
    }

    public void unregisterEnricher(Enricher enricher) {
        enrichers.remove(enricher.getId());
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

    @Override
    public RebindExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }
}
