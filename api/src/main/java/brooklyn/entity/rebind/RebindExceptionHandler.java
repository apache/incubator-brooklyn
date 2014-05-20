package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Policy;

public interface RebindExceptionHandler {

    void onLoadBrooklynMementoFailure(String msg, Exception e);
    
    void onLoadLocationMementoFailure(String msg, Exception e);

    void onLoadEntityMementoFailure(String msg, Exception e);
    
    void onLoadPolicyMementoFailure(String msg, Exception e);
    
    Entity onDanglingEntityRef(String id);

    Location onDanglingLocationRef(String id);

    void onCreateLocationFailed(String locId, String locType, Exception e);

    void onCreateEntityFailed(String entityId, String entityType, Exception e);

    void onCreatePolicyFailed(String id, String type, Exception e);

    void onLocationNotFound(String id);
    
    void onEntityNotFound(String id);
    
    void onPolicyNotFound(String id);
    
    void onRebindEntityFailed(Entity entity, Exception e);

    void onRebindLocationFailed(Location location, Exception e);

    void onRebindPolicyFailed(Policy policy, Exception e);

    void onManageLocationFailed(Location location, Exception e);

    void onManageEntityFailed(Entity entity, Exception e);

    void onDone();
    
    RuntimeException onFailed(Exception e);
}
