/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.rebind.RebindManager.RebindFailureMode;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class RebindExceptionHandlerImpl implements RebindExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RebindExceptionHandlerImpl.class);

    protected final RebindManager.RebindFailureMode danglingRefFailureMode;
    protected final RebindManager.RebindFailureMode rebindFailureMode;
    protected final RebindFailureMode addPolicyFailureMode;
    protected final RebindFailureMode loadPolicyFailureMode;

    protected final Set<String> missingEntities = Sets.newConcurrentHashSet();
    protected final Set<String> missingLocations = Sets.newConcurrentHashSet();
    protected final Set<String> missingPolicies = Sets.newConcurrentHashSet();
    protected final Set<String> missingEnrichers = Sets.newConcurrentHashSet();
    protected final Set<String> creationFailedIds = Sets.newConcurrentHashSet();
    protected final Set<Exception> addPolicyFailures = Sets.newConcurrentHashSet();
    protected final Set<Exception> loadPolicyFailures = Sets.newConcurrentHashSet();
    protected final List<Exception> exceptions = Collections.synchronizedList(Lists.<Exception>newArrayList());
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private RebindManager.RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        private RebindManager.RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_AT_END;
        private RebindManager.RebindFailureMode addPolicyFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        private RebindManager.RebindFailureMode deserializePolicyFailureMode = RebindManager.RebindFailureMode.CONTINUE;

        public Builder danglingRefFailureMode(RebindManager.RebindFailureMode val) {
            danglingRefFailureMode = val;
            return this;
        }
        public Builder rebindFailureMode(RebindManager.RebindFailureMode val) {
            rebindFailureMode = val;
            return this;
        }
        public Builder addPolicyFailureMode(RebindManager.RebindFailureMode val) {
            addPolicyFailureMode = val;
            return this;
        }
        public Builder loadPolicyFailureMode(RebindManager.RebindFailureMode val) {
            deserializePolicyFailureMode = val;
            return this;
        }
        public RebindExceptionHandler build() {
            return new RebindExceptionHandlerImpl(this);
        }
    }

    public RebindExceptionHandlerImpl(Builder builder) {
        this.danglingRefFailureMode = checkNotNull(builder.danglingRefFailureMode, "danglingRefFailureMode");
        this.rebindFailureMode = checkNotNull(builder.rebindFailureMode, "rebindFailureMode");
        this.addPolicyFailureMode = checkNotNull(builder.addPolicyFailureMode, "addPolicyFailureMode");
        this.loadPolicyFailureMode = checkNotNull(builder.deserializePolicyFailureMode, "deserializePolicyFailureMode");
    }
    
    @Override
    public void onLoadMementoFailed(BrooklynObjectType type, String msg, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem loading memento: "+msg;
        
        switch (type) {
            case POLICY:
            case ENRICHER:
                switch (loadPolicyFailureMode) {
                    case FAIL_FAST:
                        throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
                    case FAIL_AT_END:
                        loadPolicyFailures.add(new IllegalStateException(errmsg, e));
                        break;
                    case CONTINUE:
                        LOG.warn(errmsg+"; continuing", e);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected state '"+loadPolicyFailureMode+"' for loadPolicyFailureMode");
                }
                break;
            default:
                exceptions.add(new IllegalStateException(errmsg, e));
                onErrorImpl(errmsg, e);
        }
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
    public void onCreateFailed(BrooklynObjectType type, String id, String instanceType, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem creating "+type+" "+id+" of type "+instanceType;
        creationFailedIds.add(id);
        exceptions.add(new IllegalStateException(errmsg, e));
        onErrorImpl(errmsg, e);
    }

    @Override
    public void onNotFound(BrooklynObjectType type, String id) {
        if (creationFailedIds.contains(id)) {
            // already know about this; ignore
        } else {
            String errmsg = type+" '"+id+"' not found";
            exceptions.add(new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }
    
    @Override
    public void onRebindFailed(BrooklynObjectType type, BrooklynObject instance, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding "+type+" "+instance.getId()+" ("+instance+")";
        
        switch (type) {
        case ENRICHER:
        case POLICY:
            switch (addPolicyFailureMode) {
            case FAIL_FAST:
                throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
            case FAIL_AT_END:
                addPolicyFailures.add(new IllegalStateException(errmsg, e));
                break;
            case CONTINUE:
                LOG.warn(errmsg+"; continuing", e);
                creationFailedIds.add(instance.getId());
                break;
            default:
                throw new IllegalStateException("Unexpected state '"+addPolicyFailureMode+"' for addPolicyFailureMode");
            }
            break;
        default:
            exceptions.add(new IllegalStateException(errmsg, e));
            onErrorImpl(errmsg, e);
            break;
        }
    }

    @Override
    public void onAddPolicyFailed(EntityLocal entity, Policy policy, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem adding policy "+policy.getId()+" ("+policy+") to entity "+entity.getId()+" ("+entity+")";
        
        switch (addPolicyFailureMode) {
        case FAIL_FAST:
            throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
        case FAIL_AT_END:
            addPolicyFailures.add(new IllegalStateException(errmsg, e));
            break;
        case CONTINUE:
            LOG.warn(errmsg+"; continuing", e);
            break;
        default:
            throw new IllegalStateException("Unexpected state '"+addPolicyFailureMode+"' for addPolicyFailureMode");
        }
    }

    @Override
    public void onAddEnricherFailed(EntityLocal entity, Enricher enricher, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem adding enricher "+enricher.getId()+" ("+enricher+") to entity "+entity.getId()+" ("+entity+")";

        switch (addPolicyFailureMode) {
        case FAIL_FAST:
            throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
        case FAIL_AT_END:
            addPolicyFailures.add(new IllegalStateException(errmsg, e));
            break;
        case CONTINUE:
            LOG.warn(errmsg+"; continuing", e);
            break;
        default:
            throw new IllegalStateException("Unexpected state '"+addPolicyFailureMode+"' for addPolicyFailureMode");
        }
    }

    @Override
    public void onManageFailed(BrooklynObjectType type, BrooklynObject instance, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem managing "+type+" "+instance.getId()+" ("+instance+")";
        
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
        if (addPolicyFailureMode != RebindManager.RebindFailureMode.CONTINUE) {
            allExceptions.addAll(addPolicyFailures);
        }
        if (loadPolicyFailureMode != RebindManager.RebindFailureMode.CONTINUE) {
            allExceptions.addAll(loadPolicyFailures);
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
