package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

public class RebindContextLookupContext implements LookupContext {
    
    protected final RebindContext rebindContext;
    protected final RebindExceptionHandler exceptionHandler;
    
    public RebindContextLookupContext(RebindContext rebindContext, RebindExceptionHandler exceptionHandler) {
        this.rebindContext = rebindContext;
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override public Entity lookupEntity(String id) {
        Entity result = rebindContext.getEntity(id);
        if (result == null) {
            result = exceptionHandler.onDanglingEntityRef(id);
        }
        return result;
    }
    
    @Override public Location lookupLocation(String id) {
        Location result = rebindContext.getLocation(id);
        if (result == null) {
            result = exceptionHandler.onDanglingLocationRef(id);
        }
        return result;
    }
    
    @Override public Policy lookupPolicy(String id) {
        Policy result = rebindContext.getPolicy(id);
        if (result == null) {
            result = exceptionHandler.onDanglingPolicyRef(id);
        }
        return result;
    }
    
    @Override public Enricher lookupEnricher(String id) {
        Enricher result = rebindContext.getEnricher(id);
        if (result == null) {
            result = exceptionHandler.onDanglingEnricherRef(id);
        }
        return result;
    }
}