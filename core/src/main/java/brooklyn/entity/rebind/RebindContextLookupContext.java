package brooklyn.entity.rebind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

public class RebindContextLookupContext implements LookupContext {
    
    private static final Logger LOG = LoggerFactory.getLogger(RebindContextLookupContext.class);
    
    protected final RebindContext rebindContext;
    protected final RebindExceptionHandler exceptionHandler;
    
    public RebindContextLookupContext(RebindContext rebindContext, RebindExceptionHandler exceptionHandler) {
        this.rebindContext = rebindContext;
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override public Entity lookupEntity(Class<?> type, String id) {
        Entity result = rebindContext.getEntity(id);
        if (result == null) {
            result = exceptionHandler.onDanglingEntityRef(id);
        } else if (type != null && !type.isInstance(result)) {
            LOG.warn("Entity with id "+id+" does not match type "+type+"; returning "+result);
        }
        return result;
    }
    
    @Override public Location lookupLocation(Class<?> type, String id) {
        Location result = rebindContext.getLocation(id);
        if (result == null) {
            result = exceptionHandler.onDanglingLocationRef(id);
        } else if (type != null && !type.isInstance(result)) {
            LOG.warn("Location with id "+id+" does not match type "+type+"; returning "+result);
        }
        return result;
    }
    
    @Override public Policy lookupPolicy(Class<?> type, String id) {
        Policy result = rebindContext.getPolicy(id);
        if (result == null) {
            result = exceptionHandler.onDanglingPolicyRef(id);
        } else if (type != null && !type.isInstance(result)) {
            LOG.warn("Policy with id "+id+" does not match type "+type+"; returning "+result);
        }
        return result;
    }
    
    @Override public Enricher lookupEnricher(Class<?> type, String id) {
        Enricher result = rebindContext.getEnricher(id);
        if (result == null) {
            result = exceptionHandler.onDanglingEnricherRef(id);
        } else if (type != null && !type.isInstance(result)) {
            LOG.warn("Enricher with id "+id+" does not match type "+type+"; returning "+result);
        }
        return result;
    }
}