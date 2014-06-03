package brooklyn.entity.rebind;

import java.util.List;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Policy;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RecordingRebindExceptionHandler extends RebindExceptionHandlerImpl {

    protected final List<Exception> loadMementoFailures = Lists.newArrayList();
    protected final Map<String, Exception> createLocationFailures = Maps.newLinkedHashMap();
    protected final Map<String, Exception> createEntityFailures = Maps.newLinkedHashMap();
    protected final Map<String, Exception> createPolicyFailures = Maps.newLinkedHashMap();
    protected final Map<Location, Exception> rebindLocationFailures = Maps.newLinkedHashMap();
    protected final Map<Entity, Exception> rebindEntityFailures = Maps.newLinkedHashMap();
    protected final Map<Policy, Exception> rebindPolicyFailures = Maps.newLinkedHashMap();
    protected final Map<Location, Exception> manageLocationFailures = Maps.newLinkedHashMap();
    protected final Map<Entity, Exception> manageEntityFailures = Maps.newLinkedHashMap();
    protected final Map<String, Exception> locationNotFoundFailures = Maps.newLinkedHashMap();
    protected final Map<String, Exception> entityNotFoundFailures = Maps.newLinkedHashMap();
    protected final Map<String, Exception> policyNotFoundFailures = Maps.newLinkedHashMap();
    protected Exception failed;
    
    public RecordingRebindExceptionHandler(RebindManager.RebindFailureMode danglingRefFailureMode, RebindManager.RebindFailureMode rebindFailureMode) {
        super(danglingRefFailureMode, rebindFailureMode);
    }

    @Override
    public void onLoadBrooklynMementoFailed(String msg, Exception e) {
        loadMementoFailures.add(new IllegalStateException("problem loading mementos: "+msg, e));
        super.onLoadBrooklynMementoFailed(msg, e);
    }
    
    @Override
    public void onLoadLocationMementoFailed(String msg, Exception e) {
        loadMementoFailures.add(new IllegalStateException("problem loading mementos: "+msg, e));
        super.onLoadLocationMementoFailed(msg, e);
    }
    
    @Override
    public void onLoadEntityMementoFailed(String msg, Exception e) {
        loadMementoFailures.add(new IllegalStateException("problem loading mementos: "+msg, e));
        super.onLoadEntityMementoFailed(msg, e);
    }
    
    @Override
    public void onLoadPolicyMementoFailed(String msg, Exception e) {
        loadMementoFailures.add(new IllegalStateException("problem loading mementos: "+msg, e));
        super.onLoadPolicyMementoFailed(msg, e);
    }
    
    @Override
    public Entity onDanglingEntityRef(String id) {
        return super.onDanglingEntityRef(id);
    }

    @Override
    public Location onDanglingLocationRef(String id) {
        return super.onDanglingLocationRef(id);
    }

    @Override
    public void onCreateLocationFailed(String id, String type, Exception e) {
        createLocationFailures.put(id, new IllegalStateException("problem creating location "+id+" of type "+type, e));
        super.onCreateLocationFailed(id, type, e);
    }

    @Override
    public void onCreateEntityFailed(String id, String type, Exception e) {
        createEntityFailures.put(id, new IllegalStateException("problem creating entity "+id+" of type "+type, e));
        super.onCreateEntityFailed(id, type, e);
    }

    @Override
    public void onCreatePolicyFailed(String id, String type, Exception e) {
        createPolicyFailures.put(id, new IllegalStateException("problem creating policy "+id+" of type "+type, e));
        super.onCreatePolicyFailed(id, type, e);
    }

    @Override
    public void onLocationNotFound(String id) {
        locationNotFoundFailures.put(id, new IllegalStateException("location '"+id+"' not found"));
        super.onLocationNotFound(id);
    }
    
    @Override
    public void onEntityNotFound(String id) {
        entityNotFoundFailures.put(id, new IllegalStateException("entity '"+id+"' not found"));
        super.onEntityNotFound(id);
    }
    
    @Override
    public void onPolicyNotFound(String id) {
        locationNotFoundFailures.put(id, new IllegalStateException("policy'"+id+"' not found"));
        super.onPolicyNotFound(id);
    }

    @Override
    public void onRebindLocationFailed(Location location, Exception e) {
        rebindLocationFailures.put(location, new IllegalStateException("problem rebinding location "+location.getId()+" ("+location+")", e));
        super.onRebindLocationFailed(location, e);
    }

    @Override
    public void onRebindEntityFailed(Entity entity, Exception e) {
        rebindEntityFailures.put(entity, new IllegalStateException("problem rebinding entity "+entity.getId()+" ("+entity+")", e));
        super.onRebindEntityFailed(entity, e);
    }

    @Override
    public void onRebindPolicyFailed(Policy policy, Exception e) {
        rebindPolicyFailures.put(policy, new IllegalStateException("problem rebinding plicy "+policy.getId()+" ("+policy+")", e));
        super.onRebindPolicyFailed(policy, e);
    }

    @Override
    public void onManageLocationFailed(Location location, Exception e) {
        manageLocationFailures.put(location, new IllegalStateException("problem managing location "+location.getId()+" ("+location+")", e));
        super.onManageLocationFailed(location, e);
    }

    @Override
    public void onManageEntityFailed(Entity entity, Exception e) {
        manageEntityFailures.put(entity, new IllegalStateException("problem managing entity "+entity.getId()+" ("+entity+")", e));
        super.onManageEntityFailed(entity, e);
    }
    
    @Override
    public void onDone() {
        super.onDone();
    }

    @Override
    public RuntimeException onFailed(Exception e) {
        failed = e;
        return super.onFailed(e);
    }
}
