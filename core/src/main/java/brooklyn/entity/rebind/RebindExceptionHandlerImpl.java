package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Policy;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class RebindExceptionHandlerImpl implements RebindExceptionHandler {

    // TODO should we have just a single List<Exception> field, rather than separating out
    // all the exception types?
    
    private static final Logger LOG = LoggerFactory.getLogger(RebindExceptionHandlerImpl.class);

    public enum RebindFailureMode {
        FAIL_FAST,
        FAIL_AT_END,
        CONTINUE;
    }
    
    protected final RebindFailureMode danglingRefFailureMode;
    protected final RebindFailureMode rebindFailureMode;

    protected final Set<String> missingEntities = Sets.newLinkedHashSet();
    protected final Set<String> missingLocations = Sets.newLinkedHashSet();
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
    
    public RebindExceptionHandlerImpl(RebindFailureMode danglingRefFailureMode, RebindFailureMode rebindFailureMode) {
        this.danglingRefFailureMode = checkNotNull(danglingRefFailureMode, "danglingRefFailureMode");
        this.rebindFailureMode = checkNotNull(rebindFailureMode, "rebindFailureMode");
    }

    @Override
    public void onLoadBrooklynMementoFailure(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    @Override
    public void onLoadLocationMementoFailure(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    @Override
    public void onLoadEntityMementoFailure(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    @Override
    public void onLoadPolicyMementoFailure(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    protected void onLoadMementoFailure(String msg, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem loading mementos: "+msg;
        loadMementoFailures.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public Entity onDanglingEntityRef(String id) {
        missingEntities.add(id);
        if (danglingRefFailureMode == RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No entity found with id "+id);
        } else {
            LOG.warn("No entity found with id "+id+"; returning null");
            return null;
        }
    }

    @Override
    public Location onDanglingLocationRef(String id) {
        missingLocations.add(id);
        if (danglingRefFailureMode == RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No location found with id "+id);
        } else {
            LOG.warn("No location found with id "+id+"; returning null");
            return null;
        }
    }

    @Override
    public void onCreateLocationFailed(String id, String type, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem creating location "+id+" of type "+type;
        
        createLocationFailures.put(id, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onCreateEntityFailed(String id, String type, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem creating entity "+id+" of type "+type;
        
        createEntityFailures.put(id, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }


    @Override
    public void onCreatePolicyFailed(String id, String type, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem creating policy "+id+" of type "+type;
        
        createPolicyFailures.put(id, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onLocationNotFound(String id) {
        if (createLocationFailures.keySet().contains(id)) {
            // already know about this; ignore
        } else {
            String errmsg = "location '"+id+"' not found";
            locationNotFoundFailures.put(id, new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }
    
    @Override
    public void onEntityNotFound(String id) {
        if (createEntityFailures.keySet().contains(id)) {
            // already know about this; ignore
        } else {
            String errmsg = "entity '"+id+"' not found";
            entityNotFoundFailures.put(id, new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }
    
    @Override
    public void onPolicyNotFound(String id) {
        if (createPolicyFailures.keySet().contains(id)) {
            // already know about this; ignore
        } else {
            String errmsg = "policy'"+id+"' not found";
            locationNotFoundFailures.put(id, new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }

    @Override
    public void onRebindLocationFailed(Location location, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding location "+(location == null ? null : location.getId()+" ("+location+")");
        
        rebindLocationFailures.put(location, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onRebindEntityFailed(Entity entity, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding entity "+(entity == null ? null : entity.getId()+" ("+entity+")");
        
        rebindEntityFailures.put(entity, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onRebindPolicyFailed(Policy policy, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding plicy "+(policy == null ? null : policy.getId()+" ("+policy+")");
        
        rebindPolicyFailures.put(policy, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onManageLocationFailed(Location location, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem managing location "+(location == null ? null : location.getId()+" ("+location+")");
        
        manageLocationFailures.put(location, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onManageEntityFailed(Entity entity, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem managing entity "+(entity == null ? null : entity.getId()+" ("+entity+")");
        
        manageEntityFailures.put(entity, new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }
    
    protected void onErrorImpl(String errmsg) {
        onErrorImpl(errmsg, null);
    }
    
    protected void onErrorImpl(String errmsg, Exception e) {
        if (rebindFailureMode == RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
        } else {
            LOG.warn("Rebind: continuing after "+errmsg, e);
        }
    }
    
    @Override
    public void onDone() {
        onDoneImpl(null);
    }

    @Override
    public RuntimeException onFailed(Exception e) {
        onDoneImpl(e);
        throw new IllegalStateException("Rebind failed", e); // should have thrown exception above
    }
    
    protected void onDoneImpl(Exception e) {
        if (e != null) Exceptions.propagateIfFatal(e);
        
        List<Exception> exceptions = Lists.newArrayList();
        
        if (e != null) {
            exceptions.add(e);
        }
        if (danglingRefFailureMode != RebindFailureMode.CONTINUE) {
            if (missingEntities.size() > 0) {
                exceptions.add(new IllegalStateException("Missing referenced entit"+(missingEntities.size() == 1 ? "y" : "ies")+": "+missingEntities));
            }
            if (missingLocations.size() > 0) {
                exceptions.add(new IllegalStateException("Missing referenced location"+(missingEntities.size() == 1 ? "" : "s")+": "+missingLocations));
            }
        }
        if (rebindFailureMode != RebindFailureMode.CONTINUE) {
            exceptions.addAll(loadMementoFailures);
            exceptions.addAll(createLocationFailures.values());
            exceptions.addAll(createEntityFailures.values());
            exceptions.addAll(createPolicyFailures.values());
            exceptions.addAll(rebindLocationFailures.values());
            exceptions.addAll(rebindEntityFailures.values());
            exceptions.addAll(rebindPolicyFailures.values());
            exceptions.addAll(manageLocationFailures.values());
            exceptions.addAll(manageEntityFailures.values());
            exceptions.addAll(locationNotFoundFailures.values());
            exceptions.addAll(entityNotFoundFailures.values());
            exceptions.addAll(policyNotFoundFailures.values());
        }
        
        if (exceptions.isEmpty()) {
            return; // no errors
        } else {
            CompoundRuntimeException compoundException = new CompoundRuntimeException("Problem"+(exceptions.size() == 1 ? "" : "s")+" rebinding", exceptions);
            LOG.info("RebindManager failed (throwing): "+compoundException.toString());
            throw compoundException;
        }
    }
}
