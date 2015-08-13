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
import org.apache.brooklyn.api.basic.BrooklynObject;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Feed;
import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.rebind.BrooklynObjectType;
import org.apache.brooklyn.api.entity.rebind.RebindContext;
import org.apache.brooklyn.api.entity.rebind.RebindExceptionHandler;
import org.apache.brooklyn.api.entity.rebind.RebindManager;
import org.apache.brooklyn.api.entity.rebind.RebindManager.RebindFailureMode;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.Policy;

import org.apache.brooklyn.location.Location;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.QuorumCheck;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/** Stateful handler, meant for a single rebind pass */
public class RebindExceptionHandlerImpl implements RebindExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RebindExceptionHandlerImpl.class);

    protected final RebindManager.RebindFailureMode danglingRefFailureMode;
    protected final RebindManager.RebindFailureMode rebindFailureMode;
    protected final RebindFailureMode addPolicyFailureMode;
    protected final RebindFailureMode loadPolicyFailureMode;
    protected final QuorumCheck danglingRefsQuorumRequiredHealthy;

    protected final Set<String> missingEntities = Sets.newConcurrentHashSet();
    protected final Set<String> missingLocations = Sets.newConcurrentHashSet();
    protected final Set<String> missingPolicies = Sets.newConcurrentHashSet();
    protected final Set<String> missingEnrichers = Sets.newConcurrentHashSet();
    protected final Set<String> missingFeeds = Sets.newConcurrentHashSet();
    protected final Set<String> missingCatalogItems = Sets.newConcurrentHashSet();
    protected final Set<String> creationFailedIds = Sets.newConcurrentHashSet();
    
    protected final Set<Exception> addPolicyFailures = Sets.newConcurrentHashSet();
    protected final Set<Exception> loadPolicyFailures = Sets.newConcurrentHashSet();
    
    protected final Set<String> warnings = Collections.synchronizedSet(Sets.<String>newLinkedHashSet());
    protected final Set<Exception> exceptions = Collections.synchronizedSet(Sets.<Exception>newLinkedHashSet());
    
    protected RebindContext context;
    protected boolean started = false;
    protected boolean done = false;
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private RebindManager.RebindFailureMode danglingRefFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        private RebindManager.RebindFailureMode rebindFailureMode = RebindManager.RebindFailureMode.FAIL_AT_END;
        private RebindManager.RebindFailureMode addPolicyFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        private RebindManager.RebindFailureMode deserializePolicyFailureMode = RebindManager.RebindFailureMode.CONTINUE;
        private QuorumCheck danglingRefsQuorumRequiredHealthy = RebindManagerImpl.DANGLING_REFERENCES_MIN_REQUIRED_HEALTHY.getDefaultValue();

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
        public Builder danglingRefQuorumRequiredHealthy(QuorumCheck val) {
            danglingRefsQuorumRequiredHealthy = val;
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
        this.danglingRefsQuorumRequiredHealthy = checkNotNull(builder.danglingRefsQuorumRequiredHealthy, "danglingRefsQuorumRequiredHealthy");
    }
    
    protected void warn(String message) {
        warn(message, null);
    }
    protected void warn(String message, Throwable optionalError) {
        if (optionalError==null) LOG.warn(message);
        else LOG.warn(message, optionalError);
        warnings.add(message);
    }

    @Override
    public void onStart(RebindContext context) {
        if (done) {
            throw new IllegalStateException(this+" has already been used on a finished run");
        }
        if (started) {
            throw new IllegalStateException(this+" has already been used on a started run");
        }
        this.context = context;
        started = true;
    }
    
    @Override
    public void onLoadMementoFailed(BrooklynObjectType type, String msg, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem loading memento: "+msg;
        
        switch (type) {
            case FEED:
            case POLICY:
            case ENRICHER:
                switch (loadPolicyFailureMode) {
                    case FAIL_FAST:
                        throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
                    case FAIL_AT_END:
                        loadPolicyFailures.add(new IllegalStateException(errmsg, e));
                        break;
                    case CONTINUE:
                        warn(errmsg+"; continuing: "+e, e);
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
            warn("No entity found with id "+id+"; dangling reference on rebind");
            return null;
        }
    }

    @Override
    public Location onDanglingLocationRef(String id) {
        missingLocations.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No location found with id "+id);
        } else {
            warn("No location found with id "+id+"; dangling reference on rebind");
            return null;
        }
    }

    @Override
    public Policy onDanglingPolicyRef(String id) {
        missingPolicies.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No policy found with id "+id);
        } else {
            warn("No policy found with id "+id+"; dangling reference on rebind");
            return null;
        }
    }

    @Override
    public Enricher onDanglingEnricherRef(String id) {
        missingEnrichers.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No enricher found with id "+id);
        } else {
            warn("No enricher found with id "+id+"; dangling reference on rebind");
            return null;
        }
    }

    @Override
    public Feed onDanglingFeedRef(String id) {
        missingFeeds.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No feed found with id "+id);
        } else {
            warn("No feed found with id "+id+"; dangling reference on rebind");
            return null;
        }
    }

    @Override
    public CatalogItem<?, ?> onDanglingCatalogItemRef(String id) {
        missingCatalogItems.add(id);
        if (danglingRefFailureMode == RebindManager.RebindFailureMode.FAIL_FAST) {
            throw new IllegalStateException("No catalog item found with id "+id);
        } else {
            warn("No catalog item found with id "+id+"; dangling reference on rebind");
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
            String errmsg = type.toCamelCase()+" '"+id+"' not found";
            exceptions.add(new IllegalStateException(errmsg));
            onErrorImpl(errmsg);
        }
    }
    
    @Override
    public void onRebindFailed(BrooklynObjectType type, BrooklynObject instance, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem rebinding "+type.toCamelCase()+" "+instance.getId()+" ("+instance+")";
        
        switch (type) {
        case FEED:
        case ENRICHER:
        case POLICY:
            switch (addPolicyFailureMode) {
            case FAIL_FAST:
                throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
            case FAIL_AT_END:
                addPolicyFailures.add(new IllegalStateException(errmsg, e));
                break;
            case CONTINUE:
                warn(errmsg+"; continuing", e);
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
            warn(errmsg+"; continuing", e);
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
            warn(errmsg+"; continuing", e);
            break;
        default:
            throw new IllegalStateException("Unexpected state '"+addPolicyFailureMode+"' for addPolicyFailureMode");
        }
    }

    @Override
    public void onAddFeedFailed(EntityLocal entity, Feed feed, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem adding feed "+feed.getId()+" ("+feed+") to entity "+entity.getId()+" ("+entity+")";

        switch (addPolicyFailureMode) {
        case FAIL_FAST:
            throw new IllegalStateException("Rebind: aborting due to "+errmsg, e);
        case FAIL_AT_END:
            addPolicyFailures.add(new IllegalStateException(errmsg, e));
            break;
        case CONTINUE:
            warn(errmsg+"; continuing", e);
            break;
        default:
            throw new IllegalStateException("Unexpected state '"+addPolicyFailureMode+"' for addPolicyFailureMode");
        }
    }

    @Override
    public void onManageFailed(BrooklynObjectType type, BrooklynObject instance, Exception e) {
        Exceptions.propagateIfFatal(e);
        String errmsg = "problem managing "+type.toCamelCase()+" "+instance.getId()+" ("+instance+")";
        
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
            if (Thread.currentThread().isInterrupted()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Rebind: while interrupted, received "+errmsg+"/"+e+"; throwing interruption", e);
                throw Exceptions.propagate(new InterruptedException("Detected interruption while not sleeping, due to secondary error rebinding: "+errmsg+"/"+e));
            }
            warn("Rebind: continuing after "+errmsg, e);
        }
    }
    
    @Override
    public void onDone() {
        onDoneImpl(null);
    }

    @Override
    public RuntimeException onFailed(Exception e) {
        if (done) 
            throw Exceptions.propagate(e);
        
        onDoneImpl(e);
        exceptions.add(e);
        
        throw new IllegalStateException("Rebind failed", e); // should have thrown exception above
    }
    
    protected void onDoneImpl(Exception e) {
        Exceptions.propagateIfFatal(e);
        
        List<Exception> allExceptions = Lists.newArrayList();
        
        if (done) {
            allExceptions.add(new IllegalStateException(this+" has already been informed of rebind done"));
        }
        done = true;
        
        List<String> danglingIds = MutableList.copyOf(missingEntities).appendAll(missingLocations).appendAll(missingPolicies).appendAll(missingEnrichers).appendAll(missingFeeds).appendAll(missingCatalogItems);
        int totalDangling = danglingIds.size();
        if (totalDangling>0) {
            int totalFound = context.getAllBrooklynObjects().size();
            int totalItems = totalFound + totalDangling;
            if (context==null) {
                allExceptions.add(new IllegalStateException("Dangling references ("+totalDangling+" of "+totalItems+") present without rebind context"));
            } else {
                if (!danglingRefsQuorumRequiredHealthy.isQuorate(totalFound, totalItems)) {
                    warn("Dangling item"+Strings.s(totalDangling)+" ("+totalDangling+" of "+totalItems+") found on rebind exceeds quorum, assuming failed: "+danglingIds);
                    allExceptions.add(new IllegalStateException("Too many dangling references: "+totalDangling+" of "+totalItems));
                } else {
                    LOG.info("Dangling item"+Strings.s(totalDangling)+" ("+totalDangling+" of "+totalItems+") found on rebind, assuming deleted: "+danglingIds);
                }
            }
        }
        
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
            if (!missingEntities.isEmpty()) {
                allExceptions.add(new IllegalStateException("Missing referenced entit" + Strings.ies(missingEntities) + ": " + missingEntities));
            }
            if (!missingLocations.isEmpty()) {
                allExceptions.add(new IllegalStateException("Missing referenced location" + Strings.s(missingLocations) + ": " + missingLocations));
            }
            if (!missingPolicies.isEmpty()) {
                allExceptions.add(new IllegalStateException("Missing referenced polic" + Strings.ies(missingPolicies) + ": " + ": " + missingPolicies));
            }
            if (!missingEnrichers.isEmpty()) {
                allExceptions.add(new IllegalStateException("Missing referenced enricher" + Strings.s(missingEnrichers) + ": " + missingEnrichers));
            }
            if (!missingFeeds.isEmpty()) {
                allExceptions.add(new IllegalStateException("Missing referenced feed" + Strings.s(missingFeeds) + ": " + missingFeeds));
            }
            if (!missingCatalogItems.isEmpty()) {
                allExceptions.add(new IllegalStateException("Missing referenced catalog item" + Strings.s(missingCatalogItems) + ": " + missingCatalogItems));
            }
        }
        if (rebindFailureMode != RebindManager.RebindFailureMode.CONTINUE) {
            allExceptions.addAll(exceptions);
        }
        if (!started) {
            allExceptions.add(new IllegalStateException(this+" was not informed of start of rebind run"));
        }

        if (allExceptions.isEmpty()) {
            return; // no errors
        } else {
            RuntimeException compoundException = Exceptions.create("Failure rebinding", allExceptions);
            LOG.debug(compoundException.getMessage()+" (rethrowing)");
            throw compoundException;
        }
    }
    
    @Override
    public List<Exception> getExceptions() {
        return ImmutableList.copyOf(exceptions);
    }
    
    @Override
    public List<String> getWarnings() {
        return ImmutableList.copyOf(warnings);
    }
    
}
