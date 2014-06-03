package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class RebindExceptionHandlerImpl implements RebindExceptionHandler {

    // TODO should we have just a single List<Exception> field, rather than separating out
    // all the exception types?
    
    private static final Logger LOG = LoggerFactory.getLogger(RebindExceptionHandlerImpl.class);

    protected final RebindManager.RebindFailureMode danglingRefFailureMode;
    protected final RebindManager.RebindFailureMode rebindFailureMode;

    protected final Set<String> missingEntities = Sets.newLinkedHashSet();
    protected final Set<String> missingLocations = Sets.newLinkedHashSet();
    protected final Set<String> missingPolicies = Sets.newLinkedHashSet();
    protected final Set<String> missingEnrichers = Sets.newLinkedHashSet();
    protected final Set<String> creationFailedEntities = Sets.newLinkedHashSet();
    protected final Set<String> creationFailedLocations = Sets.newLinkedHashSet();
    protected final Set<String> creationFailedPolicies = Sets.newLinkedHashSet();
    protected final List<Exception> exceptions = Lists.newArrayList();
    
    public RebindExceptionHandlerImpl(RebindManager.RebindFailureMode danglingRefFailureMode, RebindManager.RebindFailureMode rebindFailureMode) {
        this.danglingRefFailureMode = checkNotNull(danglingRefFailureMode, "danglingRefFailureMode");
        this.rebindFailureMode = checkNotNull(rebindFailureMode, "rebindFailureMode");
    }

    @Override
    public void onLoadBrooklynMementoFailed(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    @Override
    public void onLoadLocationMementoFailed(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    @Override
    public void onLoadEntityMementoFailed(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    @Override
    public void onLoadPolicyMementoFailed(String msg, Exception e) {
        onLoadMementoFailure(msg, e);
    }
    
    protected void onLoadMementoFailure(String msg, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem loading mementos: "+msg;
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public Entity onDanglingEntityRef(String id) {
        missingEntities.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No entity found with id "+id);
        } else {
            LOG.warn("No entity found with id "+id+"; returning null");
            return null;
        }
    }

    @Override
    public Location onDanglingLocationRef(String id) {
        missingLocations.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No location found with id "+id);
        } else {
            LOG.warn("No location found with id "+id+"; returning null");
            return null;
        }
    }

    @Override
    public Policy onDanglingPolicyRef(String id) {
        missingPolicies.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No policy found with id "+id);
        } else {
            LOG.warn("No policy found with id "+id+"; returning null");
            return null;
        }
    }

    @Override
    public Enricher onDanglingEnricherRef(String id) {
        missingEnrichers.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No enricher found with id "+id);
        } else {
            LOG.warn("No enricher found with id "+id+"; returning null");
            return null;
        }
    }

    @Override
    public void onCreateLocationFailed(String id, String type, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem creating location "+id+" of type "+type;
        creationFailedLocations.add(id);
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onCreateEntityFailed(String id, String type, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem creating entity "+id+" of type "+type;
        creationFailedEntities.add(id);
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }


    @Override
    public void onCreatePolicyFailed(String id, String type, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem creating policy "+id+" of type "+type;
        creationFailedPolicies.add(id);
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onLocationNotFound(String id) {
        if (creationFailedLocations.contains(id)) {
            // already know about this; ignore
        } else {
            String errmsg = "location '"+id+"' not found";
            exceptions.add(new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }
    
    @Override
    public void onEntityNotFound(String id) {
        if (creationFailedEntities.contains(id)) {
            // already know about this; ignore
        } else {
            String errmsg = "entity '"+id+"' not found";
            exceptions.add(new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }
    
    @Override
    public void onPolicyNotFound(String id) {
        if (creationFailedPolicies.contains(id)) {
            // already know about this; ignore
        } else {
            String errmsg = "policy'"+id+"' not found";
            exceptions.add(new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }

    @Override
    public void onRebindLocationFailed(Location location, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding location "+location.getId()+" ("+location+")";
        
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onRebindEntityFailed(Entity entity, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding entity "+entity.getId()+" ("+entity+")";
        
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onRebindPolicyFailed(Policy policy, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding plicy "+policy.getId()+" ("+policy+")";
        
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onManageLocationFailed(Location location, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem managing location "+location.getId()+" ("+location+")";
        
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onManageEntityFailed(Entity entity, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem managing entity "+entity.getId()+" ("+entity+")";
        
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }
    
    protected void onErrorImpl(String errmsg) {
        onErrorImpl(errmsg, null);
    }
    
    protected void onErrorImpl(String errmsg, Exception e) {
        if (rebindFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
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
        
        List<Exception> allExceptions = Lists.newArrayList();
        
        if (e != null) {
            allExceptions.add(e);
        }
        if (danglingRefFailureMode != RebindManager.RebindFailureMode.CONTINUE) {
            if (missingEntities.size() > 0) {
                allExceptions.add(new IllegalStateException("Missing referenced entit"+(missingEntities.size() == 1 ? "y" : "ies")+": "+missingEntities));
            }
            if (missingLocations.size() > 0) {
                allExceptions.add(new IllegalStateException("Missing referenced location"+(missingLocations.size() == 1 ? "" : "s")+": "+missingLocations));
            }
            if (missingPolicies.size() > 0) {
                allExceptions.add(new IllegalStateException("Missing referenced polic"+(missingPolicies.size() == 1 ? "y" : "ies")+": "+missingPolicies));
            }
            if (missingEnrichers.size() > 0) {
                allExceptions.add(new IllegalStateException("Missing referenced enricher"+(missingEnrichers.size() == 1 ? "" : "s")+": "+missingEnrichers));
            }
        }
        if (rebindFailureMode != RebindManager.RebindFailureMode.CONTINUE) {
            allExceptions.addAll(exceptions);
        }
        
        if (allExceptions.isEmpty()) {
            return; // no errors
        } else {
            CompoundRuntimeException compoundException = new CompoundRuntimeException("Problem"+(allExceptions.size() == 1 ? "" : "s")+" rebinding", allExceptions);
            LOG.info("RebindManager failed (throwing): "+compoundException.toString());
            throw compoundException;
        }
    }
}
