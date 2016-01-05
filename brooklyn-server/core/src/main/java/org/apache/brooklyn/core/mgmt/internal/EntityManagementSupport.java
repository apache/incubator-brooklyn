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
package org.apache.brooklyn.core.mgmt.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.SubscriptionContext;
import org.apache.brooklyn.api.mgmt.entitlement.EntitlementManager;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.Feed;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements.EntityAndItem;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements.StringAndArgument;
import org.apache.brooklyn.core.mgmt.internal.NonDeploymentManagementContext.NonDeploymentManagementContextMode;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

/**
 * Encapsulates management activities at an entity.
 * <p>
 * On entity deployment, ManagementContext.manage(entity) causes
 * <p>
 * * onManagementStarting(ManagementContext)
 * * onManagementStartingSubscriptions()
 * * onManagementStartingSensorEmissions()
 * * onManagementStartingExecutions()
 * * onManagementStarted() - when all the above is said and done
 * * onManagementStartingHere();
 * <p>
 * on unmanage it hits onManagementStoppingHere() then onManagementStopping().
 * <p>
 * When an entity's management migrates, it invokes onManagementStoppingHere() at the old location,
 * then onManagementStartingHere() at the new location.
 */
public class EntityManagementSupport {

    private static final Logger log = LoggerFactory.getLogger(EntityManagementSupport.class);
    
    public EntityManagementSupport(AbstractEntity entity) {
        this.entity = entity;
        nonDeploymentManagementContext = new NonDeploymentManagementContext(entity, NonDeploymentManagementContextMode.PRE_MANAGEMENT);
    }

    protected transient AbstractEntity entity;
    NonDeploymentManagementContext nonDeploymentManagementContext;
    
    protected transient ManagementContext initialManagementContext;
    protected transient ManagementContext managementContext;
    protected transient SubscriptionContext subscriptionContext;
    protected transient ExecutionContext executionContext;
    
    protected final AtomicBoolean managementContextUsable = new AtomicBoolean(false);
    protected final AtomicBoolean currentlyDeployed = new AtomicBoolean(false);
    protected final AtomicBoolean everDeployed = new AtomicBoolean(false);
    protected Boolean readOnly = null;
    protected final AtomicBoolean managementFailed = new AtomicBoolean(false);
    
    private volatile EntityChangeListener entityChangeListener = EntityChangeListener.NOOP;

    /**
     * Whether this entity is managed (i.e. "onManagementStarting" has been called, so the framework knows about it,
     * and it has not been unmanaged).
     */
    public boolean isDeployed() {
        return currentlyDeployed.get();
    }

    public boolean isNoLongerManaged() {
        return wasDeployed() && !isDeployed();
    }

    /** whether entity has ever been deployed (managed) */
    public boolean wasDeployed() {
        return everDeployed.get();
    }
    
    @Beta
    public void setReadOnly(boolean isReadOnly) {
        if (isDeployed())
            throw new IllegalStateException("Cannot set read only after deployment");
        this.readOnly = isReadOnly;
    }

    /** Whether the entity and its adjuncts should be treated as read-only;
     * may be null briefly when initializing if RO status is unknown. */
    @Beta
    public Boolean isReadOnlyRaw() {
        return readOnly;
    }

    /** Whether the entity and its adjuncts should be treated as read-only;
     * error if initializing and RO status is unknown. */
    @Beta
    public boolean isReadOnly() {
        Preconditions.checkNotNull(readOnly, "Read-only status of %s not yet known", entity);
        return readOnly;
    }

    /**
     * Whether the entity's management lifecycle is complete (i.e. both "onManagementStarting" and "onManagementStarted" have
     * been called, and it is has not been unmanaged). 
     */
    public boolean isFullyManaged() {
        return (nonDeploymentManagementContext == null) && currentlyDeployed.get();
    }

    public synchronized void setManagementContext(ManagementContextInternal val) {
        if (initialManagementContext != null) {
            throw new IllegalStateException("Initial management context is already set for "+entity+"; cannot change");
        }
        if (managementContext != null && !managementContext.equals(val)) {
            throw new IllegalStateException("Management context is already set for "+entity+"; cannot change");
        }
        
        this.initialManagementContext = checkNotNull(val, "managementContext");
        if (nonDeploymentManagementContext != null) {
            nonDeploymentManagementContext.setManagementContext(val);
        }
    }
    
    public void onRebind(ManagementTransitionInfo info) {
        nonDeploymentManagementContext.setMode(NonDeploymentManagementContextMode.MANAGEMENT_REBINDING);
    }
    
    public void onManagementStarting(ManagementTransitionInfo info) {
        try {
            synchronized (this) {
                boolean alreadyManaging = isDeployed();
                
                if (alreadyManaging) {
                    log.warn("Already managed: "+entity+" ("+nonDeploymentManagementContext+"); onManagementStarting is no-op");
                } else if (nonDeploymentManagementContext == null || !nonDeploymentManagementContext.getMode().isPreManaged()) {
                    throw new IllegalStateException("Not in expected pre-managed state: "+entity+" ("+nonDeploymentManagementContext+")");
                }
                if (managementContext != null && !managementContext.equals(info.getManagementContext())) {
                    throw new IllegalStateException("Already has management context: "+managementContext+"; can't set "+info.getManagementContext());
                }
                if (initialManagementContext != null && !initialManagementContext.equals(info.getManagementContext())) {
                    throw new IllegalStateException("Already has different initial management context: "+initialManagementContext+"; can't set "+info.getManagementContext());
                }
                if (alreadyManaging) {
                    return;
                }
                
                this.managementContext = info.getManagementContext();
                nonDeploymentManagementContext.setMode(NonDeploymentManagementContextMode.MANAGEMENT_STARTING);
                
                if (!isReadOnly()) {
                    nonDeploymentManagementContext.getSubscriptionManager().setDelegate((AbstractSubscriptionManager) managementContext.getSubscriptionManager());
                    nonDeploymentManagementContext.getSubscriptionManager().startDelegatingForSubscribing();
                }
    
                managementContextUsable.set(true);
                currentlyDeployed.set(true);
                everDeployed.set(true);
                
                entityChangeListener = new EntityChangeListenerImpl();
            }
            
            /*
             * TODO framework starting events - phase 1, including rebind
             *  - establish hierarchy (child, groups, etc; construction if necessary on rebind)
             *  - set location
             *  - set local config values
             *  - set saved sensor values
             *  - register subscriptions -- BUT nothing is allowed to execute
             *  [these operations may be done before we invoke starting also; above can happen in any order;
             *  sensor _publications_ and executor submissions are queued]
             *  then:  set the management context and the entity is "managed" from the perspective of external viewers (ManagementContext.isManaged(entity) returns true)
             */
            
            if (!isReadOnly()) {
                entity.onManagementStarting();
            }
        } catch (Throwable t) {
            managementFailed.set(true);
            throw Exceptions.propagate(t);
        }
    }

    @SuppressWarnings("deprecation")
    public void onManagementStarted(ManagementTransitionInfo info) {
        try {
            synchronized (this) {
                boolean alreadyManaged = isFullyManaged();
                
                if (alreadyManaged) {
                    log.warn("Already managed: "+entity+" ("+nonDeploymentManagementContext+"); onManagementStarted is no-op");
                } else if (nonDeploymentManagementContext == null || nonDeploymentManagementContext.getMode() != NonDeploymentManagementContextMode.MANAGEMENT_STARTING) {
                    throw new IllegalStateException("Not in expected \"management starting\" state: "+entity+" ("+nonDeploymentManagementContext+")");
                }
                if (managementContext != info.getManagementContext()) {
                    throw new IllegalStateException("Already has management context: "+managementContext+"; can't set "+info.getManagementContext());
                }
                if (alreadyManaged) {
                    return;
                }
                
                nonDeploymentManagementContext.setMode(NonDeploymentManagementContextMode.MANAGEMENT_STARTED);
                
                /*
                 * - set derived/inherited config values
                 * - publish all queued sensors
                 * - start all queued executions (e.g. subscription delivery)
                 * [above happens in exactly this order, at each entity]
                 * then: the entity internally knows it fully managed (ManagementSupport.isManaged() returns true -- though not sure we need that);
                 * subsequent sensor events and executions occur directly (no queueing)
                 */
                
                if (!isReadOnly()) {
                    nonDeploymentManagementContext.getSubscriptionManager().startDelegatingForPublishing();
                }
                
                // TODO more of the above
                // TODO custom started activities
                // (elaborate or remove ^^^ ? -AH, Sept 2014)
            }
            
            if (!isReadOnly()) {
                entity.onManagementBecomingMaster();
                entity.onManagementStarted();
            }
            
            synchronized (this) {
                nonDeploymentManagementContext = null;
            }
        } catch (Throwable t) {
            managementFailed.set(true);
            throw Exceptions.propagate(t);
        }
    }
    
    @SuppressWarnings("deprecation")
    public void onManagementStopping(ManagementTransitionInfo info) {
        synchronized (this) {
            if (managementContext != info.getManagementContext()) {
                throw new IllegalStateException("onManagementStopping encountered different management context for "+entity+
                    (!wasDeployed() ? " (wasn't deployed)" : !isDeployed() ? " (no longer deployed)" : "")+
                    ": "+managementContext+"; expected "+info.getManagementContext()+" (may be a pre-registered entity which was never properly managed)");
            }
            Stopwatch startTime = Stopwatch.createStarted();
            while (!managementFailed.get() && nonDeploymentManagementContext!=null && 
                    nonDeploymentManagementContext.getMode()==NonDeploymentManagementContextMode.MANAGEMENT_STARTING) {
                // still becoming managed
                try {
                    if (startTime.elapsed(TimeUnit.SECONDS) > 30) {
                        // emergency fix, 30s timeout for management starting
                        log.error("Management stopping event "+info+" in "+this+" timed out waiting for start; proceeding to stopping");
                        break;
                    }
                    wait(100);
                } catch (InterruptedException e) {
                    Exceptions.propagate(e);
                }
            }
            if (nonDeploymentManagementContext==null) {
                nonDeploymentManagementContext = new NonDeploymentManagementContext(entity, NonDeploymentManagementContextMode.MANAGEMENT_STOPPING);
            } else {
                // already stopped? or not started?
                nonDeploymentManagementContext.setMode(NonDeploymentManagementContextMode.MANAGEMENT_STOPPING);
            }
        }
        // TODO custom stopping activities
        // TODO framework stopping events - no more sensors, executions, etc
        // (elaborate or remove ^^^ ? -AH, Sept 2014)
        
        if (!isReadOnly() && info.getMode().isDestroying()) {
            // if we support remote parent of local child, the following call will need to be properly remoted
            if (entity.getParent()!=null) entity.getParent().removeChild(entity.getProxyIfAvailable());
        }
        // new subscriptions will be queued / not allowed
        nonDeploymentManagementContext.getSubscriptionManager().stopDelegatingForSubscribing();
        // new publications will be queued / not allowed
        nonDeploymentManagementContext.getSubscriptionManager().stopDelegatingForPublishing();
        
        if (!isReadOnly()) {
            entity.onManagementNoLongerMaster();
            entity.onManagementStopped();
        }
    }
    
    public void onManagementStopped(ManagementTransitionInfo info) {
        synchronized (this) {
            if (managementContext == null && nonDeploymentManagementContext.getMode() == NonDeploymentManagementContextMode.MANAGEMENT_STOPPED) {
                return;
            }
            if (managementContext != info.getManagementContext()) {
                throw new IllegalStateException("Has different management context: "+managementContext+"; expected "+info.getManagementContext());
            }
            getSubscriptionContext().unsubscribeAll();
            entityChangeListener = EntityChangeListener.NOOP;
            managementContextUsable.set(false);
            currentlyDeployed.set(false);
            executionContext = null;
            subscriptionContext = null;
        }
        
        // TODO framework stopped activities, e.g. serialize state ?
        entity.invalidateReferences();
        
        synchronized (this) {
            managementContext = null;
            nonDeploymentManagementContext.setMode(NonDeploymentManagementContextMode.MANAGEMENT_STOPPED);
        }
    }

    @VisibleForTesting
    @Beta
    public boolean isManagementContextReal() {
        return managementContextUsable.get();
    }
    
    public synchronized ManagementContext getManagementContext() {
        return (managementContextUsable.get()) ? managementContext : nonDeploymentManagementContext;
    }    
    
    public synchronized ExecutionContext getExecutionContext() {
        if (executionContext!=null) return executionContext;
        if (managementContextUsable.get()) {
            executionContext = managementContext.getExecutionContext(entity);
            return executionContext;
        }
        return nonDeploymentManagementContext.getExecutionContext(entity);
    }
    public synchronized SubscriptionContext getSubscriptionContext() {
        if (subscriptionContext!=null) return subscriptionContext;
        if (managementContextUsable.get()) {
            subscriptionContext = managementContext.getSubscriptionContext(entity);
            return subscriptionContext;
        }
        return nonDeploymentManagementContext.getSubscriptionContext(entity);
    }
    public synchronized EntitlementManager getEntitlementManager() {
        return getManagementContext().getEntitlementManager();
    }

    public void attemptLegacyAutodeployment(String effectorName) {
        synchronized (this) {
            if (managementContext != null) {
                log.warn("Autodeployment suggested but not required for " + entity + "." + effectorName);
                return;
            }
            if (entity instanceof Application) {
                log.warn("Autodeployment with new management context triggered for " + entity + "." + effectorName + " -- will not be supported in future. Explicit manage call required.");
                if (initialManagementContext != null) {
                    initialManagementContext.getEntityManager().manage(entity);
                } else {
                    Entities.startManagement(entity);
                }
                return;
            }
        }
        if ("start".equals(effectorName)) {
            Entity e=entity;
            if (e.getParent()!=null && ((EntityInternal)e.getParent()).getManagementSupport().isDeployed()) { 
                log.warn("Autodeployment in parent's management context triggered for "+entity+"."+effectorName+" -- will not be supported in future. Explicit manage call required.");
                ((EntityInternal)e.getParent()).getManagementContext().getEntityManager().manage(entity);
                return;
            }
        }
        log.warn("Autodeployment not available for "+entity+"."+effectorName);
    }
    
    public EntityChangeListener getEntityChangeListener() {
        return entityChangeListener;
    }
    
    private class EntityChangeListenerImpl implements EntityChangeListener {
        @Override
        public void onChanged() {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
        @Override
        public void onChildrenChanged() {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
        @Override
        public void onLocationsChanged() {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
        @Override
        public void onTagsChanged() {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
        @Override
        public void onMembersChanged() {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
        @Override
        public void onPolicyAdded(Policy policy) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
            getManagementContext().getRebindManager().getChangeListener().onManaged(policy);
        }
        @Override
        public void onEnricherAdded(Enricher enricher) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
            getManagementContext().getRebindManager().getChangeListener().onManaged(enricher);
        }
        @Override
        public void onFeedAdded(Feed feed) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
            getManagementContext().getRebindManager().getChangeListener().onManaged(feed);
        }
        @Override
        public void onPolicyRemoved(Policy policy) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
            getManagementContext().getRebindManager().getChangeListener().onUnmanaged(policy);
        }
        @Override
        public void onEnricherRemoved(Enricher enricher) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
            getManagementContext().getRebindManager().getChangeListener().onUnmanaged(enricher);
        }
        @Override
        public void onFeedRemoved(Feed feed) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
            getManagementContext().getRebindManager().getChangeListener().onUnmanaged(feed);
        }
        @Override
        public void onAttributeChanged(AttributeSensor<?> attribute) {
            // TODO Could make this more efficient by inspecting the attribute to decide if needs persisted
            // immediately, or not important, or transient (e.g. do we really need to persist 
            // request-per-second count for rebind purposes?!)
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
        @Override
        public void onConfigChanged(ConfigKey<?> key) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
        @Override
        public void onEffectorStarting(Effector<?> effector, Object parameters) {
            Entitlements.checkEntitled(getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(entity, StringAndArgument.of(effector.getName(), parameters)));
        }
        @Override
        public void onEffectorCompleted(Effector<?> effector) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(entity);
        }
    }

    @Override
    public String toString() {
        return super.toString()+"["+(entity==null ? "null" : entity.getId())+"]";
    }
}
