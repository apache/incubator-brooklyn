package brooklyn.management.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.internal.NonDeploymentManagementContext.NonDeploymentManagementContextMode;

import com.google.common.base.Throwables;

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
 * When an entity's management migrates, it invoked onManagementStoppingHere() at the old location,
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
    
    protected transient ManagementContext managementContext;
    protected transient SubscriptionContext subscriptionContext;
    protected transient ExecutionContext executionContext;
    
    // TODO the application
    
    protected boolean managementContextUsable = false;
    protected boolean currentlyDeployed = false;
    protected boolean everDeployed = false;
    
    public boolean isDeployed() { return currentlyDeployed; }
    public boolean isNoLongerManaged() {
        return wasDeployed() && !isDeployed();
    }
    public boolean wasDeployed() { return everDeployed; }

    
    public void onManagementStarting(ManagementTransitionInfo info) {
        synchronized (this) {
            assert nonDeploymentManagementContext!=null && nonDeploymentManagementContext.getMode()==NonDeploymentManagementContextMode.PRE_MANAGEMENT : "Already managed: "+entity+" ("+nonDeploymentManagementContext+")";
            assert managementContext==null || managementContext.equals(info.getManagementContext()) : "Already has management context: "+managementContext+"; can't set "+info.getManagementContext();
            this.managementContext = info.getManagementContext();
            nonDeploymentManagementContext.setMode(NonDeploymentManagementContext.NonDeploymentManagementContextMode.MANAGEMENT_STARTING);
        }
        
        // TODO framework starting events - phase 1, including rebind
//        - establish hierarchy (child, groups, etc; construction if necessary on rebind)
//        - set location
//        - set local config values
//        - set saved sensor values
//        - register subscriptions -- BUT nothing is allowed to execute
//        [these operations may be done before we invoke starting also; above can happen in any order;
//        sensor _publications_ and executor submissions are queued]
//        then:  set the management context and the entity is "managed" from the perspective of external viewers (ManagementContext.isManaged(entity) returns true)
        
        synchronized (this) {
            nonDeploymentManagementContext.getSubscriptionManager().setDelegate((AbstractSubscriptionManager) managementContext.getSubscriptionManager());
            nonDeploymentManagementContext.getSubscriptionManager().startDelegatingForSubscribing();
        }
    }

    public void onManagementStarted(ManagementTransitionInfo info) {
        synchronized (this) {
            assert nonDeploymentManagementContext!=null && nonDeploymentManagementContext.getMode()==NonDeploymentManagementContextMode.MANAGEMENT_STARTING : "Already managed: "+entity+" ("+nonDeploymentManagementContext+")";
            assert managementContext == info.getManagementContext() : "Already has management context: "+managementContext+"; can't set "+info.getManagementContext();
            nonDeploymentManagementContext.setMode(NonDeploymentManagementContext.NonDeploymentManagementContextMode.MANAGEMENT_STARTED);
            managementContextUsable = true;
//        - set derived/inherited config values
//        - publish all queued sensors
//        - start all queued executions (e.g. subscription delivery)
//        [above happens in exactly this order, at each entity]
//        then: the entity internally knows it fully managed (ManagementSupport.isManaged() returns true -- though not sure we need that);
//            subsequent sensor events and executions occur directly (no queueing)
        nonDeploymentManagementContext.getSubscriptionManager().startDelegatingForPublishing();
        //TODO more of the above
        
        // TODO custom started activities
        }
        
        entity.onManagementBecomingMaster();
        synchronized (this) {
            nonDeploymentManagementContext = null;
            currentlyDeployed = true;
            everDeployed = true;
        }
    }
    
    public void onManagementStopping(ManagementTransitionInfo info) {
        synchronized (this) {
            assert managementContext == info.getManagementContext();
            while (nonDeploymentManagementContext!=null && nonDeploymentManagementContext.getMode()==NonDeploymentManagementContextMode.MANAGEMENT_STARTING) {
                // still becoming managed
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    Throwables.propagate(e);
                }
            }
            if (nonDeploymentManagementContext==null) {
                nonDeploymentManagementContext = new NonDeploymentManagementContext(entity, NonDeploymentManagementContextMode.MANAGEMENT_STOPPING);
            } else {
                // already stopped? or not started?
                nonDeploymentManagementContext.setMode(NonDeploymentManagementContext.NonDeploymentManagementContextMode.MANAGEMENT_STOPPING);
            }
        }
        // TODO custom stopping activities
        // TODO framework stopping events - no more sensors, executions, etc
        
        if (entity.getOwner()!=null) entity.getOwner().removeOwnedChild(entity);
        // new subscriptions will be queued / not allowed
        nonDeploymentManagementContext.getSubscriptionManager().stopDelegatingForSubscribing();
        // new publications will be queued / not allowed
        nonDeploymentManagementContext.getSubscriptionManager().stopDelegatingForPublishing();
        
        entity.onManagementNoLongerMaster();
    }
    
    public void onManagementStopped(ManagementTransitionInfo info) {
        synchronized (this) {
            assert managementContext == info.getManagementContext();
            managementContextUsable = false;
            currentlyDeployed = false;
            executionContext = null;
            subscriptionContext = null;
        }
        
        // TODO framework stopped activities, e.g. serialize state ?
        entity.invalidateReferences();
        
        synchronized (this) {
            managementContext = null;
            nonDeploymentManagementContext.setMode(NonDeploymentManagementContext.NonDeploymentManagementContextMode.MANAGEMENT_STOPPED);
        }
    }

    public synchronized ManagementContext getManagementContext(boolean returnNonDeploymentIfNotDeployed) {
        if (managementContextUsable) return managementContext;
        return returnNonDeploymentIfNotDeployed ? nonDeploymentManagementContext : null;
    }
    public synchronized ExecutionContext getExecutionContext() {
        if (executionContext!=null) return executionContext;
        if (managementContextUsable) {
            executionContext = managementContext.getExecutionContext(entity);
            return executionContext;
        }
        return nonDeploymentManagementContext.getExecutionContext(entity);
    }
    public synchronized SubscriptionContext getSubscriptionContext() {
        if (subscriptionContext!=null) return subscriptionContext;
        if (managementContextUsable) {
            subscriptionContext = managementContext.getSubscriptionContext(entity);
            return subscriptionContext;
        }
        return nonDeploymentManagementContext.getSubscriptionContext(entity);
    }

    public synchronized void attemptLegacyAutodeployment(String effectorName) {
        if (managementContext!=null) {
            log.warn("Autodeployment suggested but not required for "+entity+"."+effectorName);
            return;
        }
        if (entity instanceof Application) {
            log.warn("Autodeployment with new management context triggered for "+entity+"."+effectorName+" -- will not be supported in future. Explicit manage call required.");
            new LocalManagementContext().manage(entity);
            return;
        }
        if ("start".equals(effectorName)) {
            Entity e=entity;
            if (e.getOwner()!=null && ((AbstractEntity)e.getOwner()).getManagementSupport().isDeployed()) { 
                log.warn("Autodeployment in parent's management context triggered for "+entity+"."+effectorName+" -- will not be supported in future. Explicit manage call required.");
                ((AbstractEntity)e.getOwner()).getManagementSupport().getManagementContext(false).manage(entity);
                return;
            }
        }
        log.warn("Autodeployment not available for "+entity+"."+effectorName);
    }
}
