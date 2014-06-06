package brooklyn.entity.rebind;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

public class RebindContextLookupContext implements LookupContext {
    
    private static final Logger LOG = LoggerFactory.getLogger(RebindContextLookupContext.class);
    
    @Nullable
    protected final ManagementContext managementContext;
    
    protected final RebindContext rebindContext;
    protected final RebindExceptionHandler exceptionHandler;
    
    public RebindContextLookupContext(RebindContext rebindContext, RebindExceptionHandler exceptionHandler) {
        this(null, rebindContext, exceptionHandler);
    }
    public RebindContextLookupContext(ManagementContext managementContext, RebindContext rebindContext, RebindExceptionHandler exceptionHandler) {
        this.managementContext = managementContext;
        this.rebindContext = rebindContext;
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override public ManagementContext lookupManagementContext() {
        return managementContext;
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