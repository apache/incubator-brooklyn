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
package brooklyn.management.ha;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogDto;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.persister.BrooklynPersistenceUtils;
import brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneSyncRecordImpl;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneSyncRecordImpl.Builder;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.management.ha.BasicMasterChooser.AlphabeticMasterChooser;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersister.Delta;
import brooklyn.management.internal.LocalEntityManager;
import brooklyn.management.internal.LocationManagerInternal;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.management.internal.ManagementTransitionInfo.ManagementTransitionMode;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Ticker;
import com.google.common.collect.Iterables;

/**
 * This is the guts of the high-availability solution in Brooklyn.
 * <p>
 * Multiple brooklyn nodes can be started to form a single management plane, where one node is 
 * designated master and the others are "warm standbys". On termination or failure of the master,
 * the standbys deterministically decide which standby should become master (see {@link MasterChooser}).
 * That standby promotes itself.
 * <p>
 * The management nodes communicate their health/status via the {@link ManagementPlaneSyncRecordPersister}.
 * For example, if using {@link ManagementPlaneSyncRecordPersisterToObjectStore} with a shared blobstore or 
 * filesystem/NFS mount, then each management-node periodically writes its state. 
 * This acts as a heartbeat, being read by the other management-nodes.
 * <p>
 * Promotion to master involves:
 * <ol>
 *   <li>notifying the other management-nodes that it is now master
 *   <li>calling {@link RebindManager#rebind(ClassLoader, brooklyn.entity.rebind.RebindExceptionHandler, ManagementNodeState)} to read all persisted entity state, and thus reconstitute the entities.
 * </ol>
 * <p>
 * Future improvements in this area will include brooklyn-managing-brooklyn to decide + promote
 * the standby.
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public class HighAvailabilityManagerImpl implements HighAvailabilityManager {

    // TODO Improve mechanism for injecting configuration options (such as heartbeat and timeouts).
    // For example, read from brooklyn.properties?
    // But see BrooklynLauncher.haHeartbeatPeriod and .haHeartbeatTimeout, which are always injected.
    // So perhaps best to read brooklyn.properties there? Would be nice to avoid the cast to 
    // HighAvailabilityManagerImpl though.
    
    // TODO There is a race if you start multiple nodes simultaneously.
    // They may not have seen each other's heartbeats yet, so will all claim mastery!
    // But this should be resolved shortly afterwards.

    // TODO Should we pass in a classloader on construction, so it can be passed to {@link RebindManager#rebind(ClassLoader)}

    public final ConfigKey<Duration> POLL_PERIOD = ConfigKeys.newConfigKey(Duration.class, "brooklyn.ha.pollPeriod",
        "How often nodes should poll to detect whether master is healthy", Duration.seconds(5));
    public final ConfigKey<Duration> HEARTBEAT_TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "brooklyn.ha.heartbeatTimeout",
        "Maximum allowable time for detection of a peer's heartbeat; if no sign of master after this time, "
        + "another node may promote itself", Duration.THIRTY_SECONDS);
    
    @VisibleForTesting /* only used in tests currently */
    public static interface PromotionListener {
        public void promotingToMaster();
    }
    
    private static final Logger LOG = LoggerFactory.getLogger(HighAvailabilityManagerImpl.class);

    private final ManagementContextInternal managementContext;
    private volatile String ownNodeId;
    private volatile ManagementPlaneSyncRecordPersister persister;
    private volatile PromotionListener promotionListener;
    private volatile MasterChooser masterChooser = new AlphabeticMasterChooser();
    private volatile Ticker localTickerUtc = new Ticker() {
        // strictly not a ticker because returns millis UTC, but it works fine even so
        @Override
        public long read() {
            return System.currentTimeMillis();
        }
    };
    private volatile Ticker optionalRemoteTickerUtc = null;
    
    private volatile Task<?> pollingTask;
    private volatile boolean disabled;
    private volatile boolean running;
    private volatile ManagementNodeState nodeState = ManagementNodeState.INITIALIZING;
    private volatile boolean nodeStateTransitionComplete = false;
    private volatile long priority = 0;
    
    private volatile transient Duration pollPeriodLocalOverride;
    private volatile transient Duration heartbeatTimeoutOverride;

    private volatile ManagementPlaneSyncRecord lastSyncRecord;
    
    public HighAvailabilityManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
    }

    @Override
    public HighAvailabilityManagerImpl setPersister(ManagementPlaneSyncRecordPersister persister) {
        this.persister = checkNotNull(persister, "persister");
        return this;
    }
    
    @Override
    public ManagementPlaneSyncRecordPersister getPersister() {
        return persister;
    }
    
    protected synchronized Duration getPollPeriod() {
        if (pollPeriodLocalOverride!=null) return pollPeriodLocalOverride;
        return managementContext.getBrooklynProperties().getConfig(POLL_PERIOD);
    }
    
    /** Overrides {@link #POLL_PERIOD} from brooklyn config, 
     * including e.g. {@link Duration#PRACTICALLY_FOREVER} to disable polling;
     * or <code>null</code> to clear a local override */
    public HighAvailabilityManagerImpl setPollPeriod(Duration val) {
        synchronized (this) {
            this.pollPeriodLocalOverride = val;
        }
        if (running) {
            registerPollTask();
        }
        return this;
    }

    public HighAvailabilityManagerImpl setMasterChooser(MasterChooser val) {
        this.masterChooser = checkNotNull(val, "masterChooser");
        return this;
    }

    public synchronized Duration getHeartbeatTimeout() {
        if (heartbeatTimeoutOverride!=null) return heartbeatTimeoutOverride;
        return managementContext.getBrooklynProperties().getConfig(HEARTBEAT_TIMEOUT);
    }
    
    /** Overrides {@link #HEARTBEAT_TIMEOUT} from brooklyn config, 
     * including e.g. {@link Duration#PRACTICALLY_FOREVER} to prevent failover due to heartbeat absence;
     * or <code>null</code> to clear a local override */
    public synchronized HighAvailabilityManagerImpl setHeartbeatTimeout(Duration val) {
        this.heartbeatTimeoutOverride = val;
        return this;
    }

    /** A ticker that reads in milliseconds, for populating local timestamps.
     * Defaults to System.currentTimeMillis(); may be overridden e.g. for testing. */
    public HighAvailabilityManagerImpl setLocalTicker(Ticker val) {
        this.localTickerUtc = checkNotNull(val);
        return this;
    }

    /** A ticker that reads in milliseconds, for overriding remote timestamps.
     * Defaults to null which means to use the remote timestamp. 
     * Only for testing as this records the remote timestamp in the object.
     * <p>
     * If this is supplied, one must also set {@link ManagementPlaneSyncRecordPersisterToObjectStore#useRemoteTimestampInMemento()}. */
    @VisibleForTesting
    public HighAvailabilityManagerImpl setRemoteTicker(Ticker val) {
        this.optionalRemoteTickerUtc = val;
        return this;
    }

    public HighAvailabilityManagerImpl setPromotionListener(PromotionListener val) {
        this.promotionListener = checkNotNull(val, "promotionListener");
        return this;
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void disabled() {
        disabled = true;
        ownNodeId = managementContext.getManagementNodeId();
        // this is notionally the master, just not running; see javadoc for more info
        stop(ManagementNodeState.MASTER);
        
    }

    @Override
    public void start(HighAvailabilityMode startMode) {
        nodeStateTransitionComplete = true;
        // always start in standby; it may get promoted to master or hot_standby in this method
        // (depending on startMode; but for startMode STANDBY or HOT_STANDBY it will not promote until the next election)
        nodeState = ManagementNodeState.STANDBY;
        disabled = false;
        running = true;
        changeMode(startMode, true, true);
    }
    
    @Override
    public void changeMode(HighAvailabilityMode startMode) {
        changeMode(startMode, false, false);
    }
    
    @VisibleForTesting
    @Beta
    public void changeMode(HighAvailabilityMode startMode, boolean preventElectionOnExplicitStandbyMode, boolean failOnExplicitStandbyModeIfNoMaster) {
        if (!running) {
            // if was not running then start as disabled mode, then proceed as normal
            LOG.info("HA changing mode to "+startMode+" from "+nodeState+" when not running, forcing an intermediate start as DISABLED then will convert to "+startMode);
            start(HighAvailabilityMode.DISABLED);
        }
        if (getNodeState()==ManagementNodeState.FAILED || getNodeState()==ManagementNodeState.INITIALIZING) {
            if (startMode!=HighAvailabilityMode.DISABLED) {
                // if coming from FAILED (or INITIALIZING because we skipped start call) then treat as cold standby
                nodeState = ManagementNodeState.STANDBY; 
            }
        }
        
        ownNodeId = managementContext.getManagementNodeId();
        // TODO Small race in that we first check, and then we'll do checkMaster() on first poll,
        // so another node could have already become master or terminated in that window.
        ManagementNodeSyncRecord existingMaster = hasHealthyMaster();
        boolean weAreMaster = existingMaster!=null && ownNodeId.equals(existingMaster.getNodeId());
        
        // catch error in some tests where mgmt context has a different mgmt context
        if (managementContext.getHighAvailabilityManager()!=this)
            throw new IllegalStateException("Cannot start an HA manager on a management context with a different HA manager!");
        
        if (weAreMaster) {
            // demotion may be required; do this before triggering an election
            switch (startMode) {
            case MASTER:
            case AUTO:
            case DISABLED:
                // no action needed, will do anything necessary below
                break;
            case HOT_STANDBY: demoteToStandby(true); break;
            case STANDBY: demoteToStandby(false); break;
            default:
                throw new IllegalStateException("Unexpected high availability mode "+startMode+" requested for "+this);
            }
        }
        
        // now do election
        switch (startMode) {
        case AUTO:
            // don't care; let's start and see if we promote ourselves
            publishAndCheck(true);
            if (nodeState == ManagementNodeState.STANDBY || nodeState == ManagementNodeState.HOT_STANDBY) {
                ManagementPlaneSyncRecord newState = loadManagementPlaneSyncRecord(true);;
                String masterNodeId = newState.getMasterNodeId();
                ManagementNodeSyncRecord masterNodeDetails = newState.getManagementNodes().get(masterNodeId);
                LOG.info("Management node "+ownNodeId+" running as HA " + nodeState + " autodetected, " +
                    (Strings.isBlank(masterNodeId) ? "no master currently (other node should promote itself soon)" : "master "
                        + (existingMaster==null ? "(new) " : "")
                        + "is "+masterNodeId +
                        (masterNodeDetails==null || masterNodeDetails.getUri()==null ? " (no url)" : " at "+masterNodeDetails.getUri())));
            } else if (nodeState == ManagementNodeState.MASTER) {
                LOG.info("Management node "+ownNodeId+" running as HA MASTER autodetected");
            } else {
                throw new IllegalStateException("Management node "+ownNodeId+" set to HA AUTO, encountered unexpected mode "+nodeState);
            }
            break;
        case MASTER:
            if (existingMaster == null) {
                promoteToMaster();
                LOG.info("Management node "+ownNodeId+" running as HA MASTER explicitly");
            } else if (!weAreMaster) {
                throw new IllegalStateException("Master already exists; cannot run as master (master "+existingMaster.toVerboseString()+"); "
                    + "to trigger a promotion, set a priority and demote the current master");
            } else {
                LOG.info("Management node "+ownNodeId+" already running as HA MASTER, when set explicitly");
            }
            break;
        case STANDBY:
        case HOT_STANDBY:
            if (!preventElectionOnExplicitStandbyMode)
                publishAndCheck(true);
            if (failOnExplicitStandbyModeIfNoMaster && existingMaster==null) {
                LOG.error("Management node "+ownNodeId+" detected no master when "+startMode+" requested and existing master required; failing.");
                throw new IllegalStateException("No existing master; cannot start as "+startMode);
            }
            
            String message = "Management node "+ownNodeId+" running as HA "+getNodeState()+" (";
            if (getNodeState().toString().equals(startMode.toString()))
                message += "explicitly requested";
            else if (startMode==HighAvailabilityMode.HOT_STANDBY && getNodeState()==ManagementNodeState.STANDBY)
                message += "caller requested "+startMode+", will attempt rebind for HOT_STANDBY next";
            else
                message += "caller requested "+startMode;
            
            if (getNodeState()==ManagementNodeState.MASTER) {
                message += " but election re-promoted this node)";
            } else {
                ManagementPlaneSyncRecord newState = loadManagementPlaneSyncRecord(true);
                if (Strings.isBlank(newState.getMasterNodeId())) {
                    message += "); no master currently (subsequent election may repair)";
                } else {
                    message += "); master "+newState.getMasterNodeId();
                }
            }
            LOG.info(message);
            break;
        case DISABLED:
            // safe just to run even if we weren't master
            LOG.info("Management node "+ownNodeId+" HA DISABLED (was "+nodeState+")");
            demoteToFailed();
            if (pollingTask!=null) pollingTask.cancel(true);
            break;
        default:
            throw new IllegalStateException("Unexpected high availability mode "+startMode+" requested for "+this);
        }
        
        if (startMode==HighAvailabilityMode.AUTO) {
            if (BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY)) {
                startMode = HighAvailabilityMode.HOT_STANDBY;
            } else {
                startMode = HighAvailabilityMode.STANDBY;
            }
        }
        if (nodeState==ManagementNodeState.STANDBY && startMode==HighAvailabilityMode.HOT_STANDBY) {
            // if it should be hot standby, then we need to promote
            nodeStateTransitionComplete = false;
            // inform the world that we are transitioning (not eligible for promotion while going in to hot standby)
            publishHealth();
            try {
                attemptHotStandby();
                nodeStateTransitionComplete = true;
                publishHealth();
                
                if (getNodeState()==ManagementNodeState.HOT_STANDBY) {
                    LOG.info("Management node "+ownNodeId+" now running as HA "+ManagementNodeState.HOT_STANDBY+"; "
                        + managementContext.getApplications().size()+" application"+Strings.s(managementContext.getApplications().size())+" loaded");
                } else {
                    LOG.warn("Management node "+ownNodeId+" unable to promote to "+ManagementNodeState.HOT_STANDBY+" (currently "+getNodeState()+"); "
                        + "(see log for further details)");
                }
            } catch (Exception e) {
                LOG.warn("Management node "+ownNodeId+" unable to promote to "+ManagementNodeState.HOT_STANDBY+" (currently "+getNodeState()+"); rethrowing: "+Exceptions.collapseText(e));
                throw Exceptions.propagate(e);
            }
        } else {
            nodeStateTransitionComplete = true;
        }
        if (startMode!=HighAvailabilityMode.DISABLED)
            registerPollTask();
    }

    @Override
    public void setPriority(long priority) {
        this.priority = priority;
        if (persister!=null) publishHealth();
    }
    
    @Override
    public long getPriority() {
        return priority;
    }
    
    @Override
    public void stop() {
        LOG.debug("Stopping "+this);
        stop(ManagementNodeState.TERMINATED);
    }
    
    private void stop(ManagementNodeState newState) {
        boolean wasRunning = running;
        
        running = false;
        nodeState = newState;
        if (pollingTask != null) pollingTask.cancel(true);
        
        if (wasRunning) {
            try {
                publishHealth();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                LOG.error("Problem publishing manager-node health on termination (continuing)", e);
            }
        }
    }
    
    /** returns the node state this node is trying to be in */
    public ManagementNodeState getTransitionTargetNodeState() {
        return nodeState;
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public ManagementNodeState getNodeState() {
        if (nodeState==ManagementNodeState.FAILED) return nodeState;
        // if target is master then we claim already being master, to prevent other nodes from taking it
        // (we may fail subsequently of course)
        if (nodeState==ManagementNodeState.MASTER) return nodeState;
        
        // for backwards compatibility; remove in 0.8.0
        if (nodeState==ManagementNodeState.UNINITIALISED) return ManagementNodeState.INITIALIZING;
        
        if (!nodeStateTransitionComplete) return ManagementNodeState.INITIALIZING;
        return nodeState;
    }

    public ManagementPlaneSyncRecord getLastManagementPlaneSyncRecord() {
        return lastSyncRecord;
    }
    
    @Override
    public ManagementPlaneSyncRecord getManagementPlaneSyncState() {
        return loadManagementPlaneSyncRecord(true);
    }

    @SuppressWarnings("unchecked")
    protected void registerPollTask() {
        final Runnable job = new Runnable() {
            @Override public void run() {
                try {
                    publishAndCheck(false);
                } catch (Exception e) {
                    if (running) {
                        LOG.error("Problem in HA-poller: "+e, e);
                    } else {
                        if (LOG.isDebugEnabled()) LOG.debug("Problem in HA-poller, but no longer running: "+e, e);
                    }
                } catch (Throwable t) {
                    LOG.error("Problem in HA-poller: "+t, t);
                    throw Exceptions.propagate(t);
                }
            }
        };
        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<?> call() {
                return Tasks.builder().dynamic(false).body(job).name("HA poller task").tag(BrooklynTaskTags.TRANSIENT_TASK_TAG)
                    .description("polls HA status to see whether this node should promote").build();
            }
        };
        
        LOG.debug("Registering poll task for "+this+", period "+getPollPeriod());
        if (getPollPeriod().equals(Duration.PRACTICALLY_FOREVER)) {
            // don't schedule - used for tests
            // (scheduling fires off one initial task in the background before the delay, 
            // which affects tests that want to know exactly when publishing happens;
            // TODO would be nice if scheduled task had a "no initial submission" flag )
        } else {
            if (pollingTask!=null) pollingTask.cancel(true);
            
            ScheduledTask task = new ScheduledTask(MutableMap.of("period", getPollPeriod(), "displayName", "scheduled:[HA poller task]"), taskFactory);
            pollingTask = managementContext.getExecutionManager().submit(task);
        }
    }
    
    /** invoked manually when initializing, and periodically thereafter */
    @VisibleForTesting
    public synchronized void publishAndCheck(boolean initializing) {
        publishHealth();
        checkMaster(initializing);
    }
    
    protected synchronized void publishHealth() {
        if (persister == null) {
            LOG.info("Cannot publish management-node health as no persister");
            return;
        }
        
        ManagementNodeSyncRecord memento = createManagementNodeSyncRecord(false);
        Delta delta = ManagementPlaneSyncRecordDeltaImpl.builder().node(memento).build();
        persister.delta(delta);
        if (LOG.isTraceEnabled()) LOG.trace("Published management-node health: {}", memento);
    }
    
    protected synchronized void publishDemotion(boolean demotingFromMaster) {
        checkState(getNodeState() != ManagementNodeState.MASTER, "node status must not be master when demoting", getNodeState());
        
        if (persister == null) {
            LOG.info("Cannot publish management-node health as no persister");
            return;
        }
        
        ManagementNodeSyncRecord memento = createManagementNodeSyncRecord(false);
        ManagementPlaneSyncRecordDeltaImpl.Builder deltaBuilder = ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(memento);
        if (demotingFromMaster) {
            deltaBuilder.clearMaster(ownNodeId);
        }
        
        Delta delta = deltaBuilder.build();
        persister.delta(delta);
        if (LOG.isTraceEnabled()) LOG.trace("Published management-node health: {}", memento);
    }
    
    /**
     * Publishes (via {@link #persister}) the state of this management node with itself set to master.
     */
    protected synchronized void publishPromotionToMaster() {
        checkState(getNodeState() == ManagementNodeState.MASTER, "node status must be master on publish, but is %s", getNodeState());
        
        if (persister == null) {
            LOG.info("Cannot publish management-node health as no persister");
            return;
        }
        
        ManagementNodeSyncRecord memento = createManagementNodeSyncRecord(false);
        Delta delta = ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(memento)
                .setMaster(ownNodeId)
                .build();
        persister.delta(delta);
        if (LOG.isTraceEnabled()) LOG.trace("Published management-node health: {}", memento);
    }
    
    protected boolean isHeartbeatOk(ManagementNodeSyncRecord masterNode, ManagementNodeSyncRecord meNode) {
        if (masterNode==null) return false;
        if (meNode==null) {
            // we can't confirm it's healthy, but it appears so as far as we can tell
            return true;
        }
        Long timestampMaster = masterNode.getRemoteTimestamp();
        Long timestampMe = meNode.getRemoteTimestamp();
        if (timestampMaster==null || timestampMe==null) return false;
        return (timestampMe - timestampMaster) <= getHeartbeatTimeout().toMilliseconds();
    }
    
    protected ManagementNodeSyncRecord hasHealthyMaster() {
        ManagementPlaneSyncRecord memento = loadManagementPlaneSyncRecord(false);
        
        String nodeId = memento.getMasterNodeId();
        ManagementNodeSyncRecord masterMemento = (nodeId == null) ? null : memento.getManagementNodes().get(nodeId);
        
        ManagementNodeSyncRecord ourMemento = memento.getManagementNodes().get(ownNodeId);
        boolean result = masterMemento != null && masterMemento.getStatus() == ManagementNodeState.MASTER
                && isHeartbeatOk(masterMemento, ourMemento);
        
        if (LOG.isDebugEnabled()) LOG.debug("Healthy-master check result={}; masterId={}; masterMemento={}; ourMemento={}",
                new Object[] {result, nodeId, (masterMemento == null ? "<none>" : masterMemento.toVerboseString()), (ourMemento == null ? "<none>" : ourMemento.toVerboseString())});
        
        return (result ? masterMemento : null);
    }
    
    /**
     * Looks up the state of all nodes in the management plane, and checks if the master is still ok.
     * If it's not then determines which node should be promoted to master. If it is ourself, then promotes.
     */
    protected void checkMaster(boolean initializing) {
        ManagementPlaneSyncRecord memento = loadManagementPlaneSyncRecord(false);
        
        if (getNodeState() == ManagementNodeState.FAILED) {
            // if we have failed then no point in checking who is master
            // (if somehow this node is subsequently clearFailure() then it will resume)
            return;
        }
        
        String currMasterNodeId = memento.getMasterNodeId();
        ManagementNodeSyncRecord currMasterNodeRecord = memento.getManagementNodes().get(currMasterNodeId);
        ManagementNodeSyncRecord ownNodeRecord = memento.getManagementNodes().get(ownNodeId);
        
        ManagementNodeSyncRecord newMasterNodeRecord = null;
        boolean demotingSelfInFavourOfOtherMaster = false;
        
        if (currMasterNodeRecord != null && currMasterNodeRecord.getStatus() == ManagementNodeState.MASTER && isHeartbeatOk(currMasterNodeRecord, ownNodeRecord)) {
            // master seems healthy
            if (ownNodeId.equals(currMasterNodeId)) {
                if (LOG.isTraceEnabled()) LOG.trace("Existing master healthy (us): master={}", currMasterNodeRecord.toVerboseString());
                return;
            } else {
                if (ownNodeRecord!=null && ownNodeRecord.getStatus() == ManagementNodeState.MASTER) {
                    LOG.error("Management node "+ownNodeId+" detected master change, stolen from us, deferring to "+currMasterNodeId);
                    newMasterNodeRecord = currMasterNodeRecord;
                    demotingSelfInFavourOfOtherMaster = true;
                } else {
                    if (LOG.isTraceEnabled()) LOG.trace("Existing master healthy (remote): master={}", currMasterNodeRecord.toVerboseString());
                    return;
                }
            }
        } else if (ownNodeRecord == null || !isHeartbeatOk(ownNodeRecord, ownNodeRecord)) {
            // our heartbeats are also out-of-date! perhaps something wrong with persistence? just log, and don't over-react!
            if (ownNodeRecord == null) {
                LOG.error("No management node memento for self ("+ownNodeId+"); perhaps persister unwritable? "
                        + "Master ("+currMasterNodeId+") reported failed but no-op as cannot tell conclusively");
            } else {
                LOG.error("This management node ("+ownNodeId+") memento heartbeats out-of-date; perhaps perister unwritable? "
                        + "Master ("+currMasterNodeId+") reported failed but no-op as cannot tell conclusively"
                        + ": self="+ownNodeRecord.toVerboseString());
            }
            return;
        } else if (ownNodeId.equals(currMasterNodeId)) {
            // we are supposed to be the master, but seem to be unhealthy!
            LOG.warn("This management node ("+ownNodeId+") supposed to be master but reportedly unhealthy? "
                    + "no-op as expect other node to fix: self="+ownNodeRecord.toVerboseString());
            return;
        }
        
        if (demotingSelfInFavourOfOtherMaster) {
            LOG.debug("Master-change for this node only, demoting "+ownNodeRecord.toVerboseString()+" in favour of official master "+newMasterNodeRecord.toVerboseString());
            demoteToStandby(BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY));
            return;
        } else {
            LOG.debug("Detected master heartbeat timeout. Initiating a new master election. Master was " + currMasterNodeRecord);
        }
        
        // Need to choose a new master
        newMasterNodeRecord = masterChooser.choose(memento, getHeartbeatTimeout(), ownNodeId);
        
        String newMasterNodeId = (newMasterNodeRecord == null) ? null : newMasterNodeRecord.getNodeId();
        URI newMasterNodeUri = (newMasterNodeRecord == null) ? null : newMasterNodeRecord.getUri();
        boolean weAreNewMaster = ownNodeId.equals(newMasterNodeId);
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Management node master-change required: newMaster={}; oldMaster={}; plane={}, self={}; heartbeatTimeout={}", 
                new Object[] {
                    (newMasterNodeRecord == null ? "<none>" : newMasterNodeRecord.toVerboseString()),
                    (currMasterNodeRecord == null ? currMasterNodeId+" (no memento)": currMasterNodeRecord.toVerboseString()),
                    memento,
                    ownNodeRecord.toVerboseString(), 
                    getHeartbeatTimeout()
                });
        }
        if (!initializing) {
            String message = "Management node "+ownNodeId+" detected ";
            if (weAreNewMaster) message += "we should be master, changing from ";
            else message += "master change, from ";
            message +=currMasterNodeId + " (" + (currMasterNodeRecord==null ? "?" : timestampString(currMasterNodeRecord.getRemoteTimestamp())) + ")"
                + " to "
                + (newMasterNodeId == null ? "<none>" :
                    (weAreNewMaster ? "us " : "")
                    + newMasterNodeId + " (" + timestampString(newMasterNodeRecord.getRemoteTimestamp()) + ")" 
                    + (newMasterNodeUri!=null ? " "+newMasterNodeUri : "")  );
            LOG.warn(message);
        }

        // New master is ourself: promote
        if (weAreNewMaster) {
            promoteToMaster();
        }
    }
    
    private static String timestampString(Long remoteTimestamp) {
        if (remoteTimestamp==null) return null;
        return remoteTimestamp+" / "+Time.makeTimeStringRounded( Duration.sinceUtc(remoteTimestamp))+" ago";
    }

    protected void promoteToMaster() {
        if (!running) {
            LOG.warn("Ignoring promote-to-master request, as HighAvailabilityManager is no longer running");
            return;
        }
        
        if (promotionListener != null) {
            try {
                promotionListener.promotingToMaster();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                LOG.warn("Problem in promption-listener (continuing)", e);
            }
        }
        boolean wasHotStandby = nodeState==ManagementNodeState.HOT_STANDBY;
        nodeState = ManagementNodeState.MASTER;
        publishPromotionToMaster();
        try {
            if (wasHotStandby) {
                // could just promote the standby items; but for now we stop the old read-only and re-load them, to make sure nothing has been missed
                // TODO ideally there'd be an incremental rebind as well as an incremental persist
                managementContext.getRebindManager().stopReadOnly();
                clearManagedItems(ManagementTransitionMode.REBINDING_DESTROYED);
            }
            managementContext.getRebindManager().rebind(managementContext.getCatalog().getRootClassLoader(), null, nodeState);
        } catch (Exception e) {
            LOG.error("Management node enountered problem during rebind when promoting self to master; demoting to FAILED and rethrowing: "+e);
            demoteToFailed();
            throw Exceptions.propagate(e);
        }
        managementContext.getRebindManager().start();
    }
    
    protected void backupOnDemotionIfNeeded() {
        if (managementContext.getBrooklynProperties().getConfig(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED_ON_DEMOTION)) {
            BrooklynPersistenceUtils.createBackup(managementContext, "demotion", MementoCopyMode.LOCAL);
        }
    }

    protected void demoteToFailed() {
        // TODO merge this method with the one below
        boolean wasMaster = nodeState == ManagementNodeState.MASTER;
        if (wasMaster) backupOnDemotionIfNeeded();
        ManagementTransitionMode mode = (wasMaster ? ManagementTransitionMode.REBINDING_NO_LONGER_PRIMARY : ManagementTransitionMode.REBINDING_DESTROYED);
        nodeState = ManagementNodeState.FAILED;
        onDemotionStopItems(mode);
        nodeStateTransitionComplete = true;
        publishDemotion(wasMaster);
    }
    
    protected void demoteToStandby(boolean hot) {
        if (!running) {
            LOG.warn("Ignoring demote-from-master request, as HighAvailabilityManager is no longer running");
            return;
        }
        boolean wasMaster = nodeState == ManagementNodeState.MASTER;
        if (wasMaster) backupOnDemotionIfNeeded();
        ManagementTransitionMode mode = (wasMaster ? ManagementTransitionMode.REBINDING_NO_LONGER_PRIMARY : ManagementTransitionMode.REBINDING_DESTROYED);

        nodeStateTransitionComplete = false;
        nodeState = ManagementNodeState.STANDBY;
        onDemotionStopItems(mode);
        nodeStateTransitionComplete = true;
        publishDemotion(wasMaster);
        
        if (hot) {
            nodeStateTransitionComplete = false;
            attemptHotStandby();
            nodeStateTransitionComplete = true;
            publishHealth();
        }
    }
    
    protected void onDemotionStopItems(ManagementTransitionMode mode) {
        // stop persistence and remove all apps etc
        managementContext.getRebindManager().stopPersistence();
        managementContext.getRebindManager().stopReadOnly();
        clearManagedItems(mode);
        
        // tasks are cleared as part of unmanaging entities above
    }

    /** clears all managed items from the management context; same items destroyed as in the course of a rebind cycle */
    protected void clearManagedItems(ManagementTransitionMode mode) {
        for (Application app: managementContext.getApplications()) {
            if (((EntityInternal)app).getManagementSupport().isDeployed()) {
                ((EntityInternal)app).getManagementContext().getEntityManager().unmanage(app);
            }
        }
        // for normal management, call above will remove; for read-only, etc, let's do what's below:
        for (Entity entity: managementContext.getEntityManager().getEntities()) {
            ((LocalEntityManager)managementContext.getEntityManager()).unmanage(entity, mode);
        }
    
        // again, for locations, call unmanage on parents first
        for (Location loc: managementContext.getLocationManager().getLocations()) {
            if (loc.getParent()==null)
                ((LocationManagerInternal)managementContext.getLocationManager()).unmanage(loc, mode);
        }
        for (Location loc: managementContext.getLocationManager().getLocations()) {
            ((LocationManagerInternal)managementContext.getLocationManager()).unmanage(loc, mode);
        }
        
        ((BasicBrooklynCatalog)managementContext.getCatalog()).reset(CatalogDto.newEmptyInstance("<reset-by-ha-status-change>"));
    }
    
    /** starts hot standby, in foreground; the caller is responsible for publishing health afterwards.
     * @return whether hot standby was possible (if not, errors should be stored elsewhere) */
    protected boolean attemptHotStandby() {
        try {
            Preconditions.checkState(nodeStateTransitionComplete==false, "Must be in transitioning state to go into hot standby");
            nodeState = ManagementNodeState.HOT_STANDBY;
            managementContext.getRebindManager().startReadOnly();
            
            return true;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            LOG.warn("Unable to promote "+ownNodeId+" to hot standby, switching to FAILED: "+e, e);
            demoteToFailed();
            return false;
        }
    }
    
    @Override
    public ManagementPlaneSyncRecord loadManagementPlaneSyncRecord(boolean useLocalKnowledgeForThisNode) {
        ManagementPlaneSyncRecord record = loadManagementPlaneSyncRecordInternal(useLocalKnowledgeForThisNode);
        lastSyncRecord = record;
        return record; 
    }
    
    private ManagementPlaneSyncRecord loadManagementPlaneSyncRecordInternal(boolean useLocalKnowledgeForThisNode) {
        if (disabled) {
            // if HA is disabled, then we are the only node - no persistence; just load a memento to describe this node
            Builder builder = ManagementPlaneSyncRecordImpl.builder()
                .node(createManagementNodeSyncRecord(true));
            if (getTransitionTargetNodeState() == ManagementNodeState.MASTER) {
                builder.masterNodeId(ownNodeId);
            }
            return builder.build();
        }
        if (persister == null) {
            // e.g. web-console may be polling before we've started up
            LOG.debug("High availablity manager has no persister; returning empty record");
            return ManagementPlaneSyncRecordImpl.builder().build();
        }
        
        int maxLoadAttempts = 5;
        Exception lastException = null;
        for (int i = 0; i < maxLoadAttempts; i++) {
            try {
                ManagementPlaneSyncRecord result = persister.loadSyncRecord();
                
                if (useLocalKnowledgeForThisNode) {
                    // Report this node's most recent state, and detect AWOL nodes
                    ManagementNodeSyncRecord me = BasicManagementNodeSyncRecord.builder()
                        .from(result.getManagementNodes().get(ownNodeId), true)
                        .from(createManagementNodeSyncRecord(false), true)
                        .build();
                    Iterable<ManagementNodeSyncRecord> allNodes = result.getManagementNodes().values();
                    if (me.getRemoteTimestamp()!=null)
                        allNodes = Iterables.transform(allNodes, new MarkAwolNodes(me));
                    Builder builder = ManagementPlaneSyncRecordImpl.builder()
                        .masterNodeId(result.getMasterNodeId())
                        .nodes(allNodes);
                    builder.node(me);
                    if (getTransitionTargetNodeState() == ManagementNodeState.MASTER) {
                        builder.masterNodeId(ownNodeId);
                    }
                    result = builder.build();
                }
                return result;
            } catch (IOException e) {
                if (i < (maxLoadAttempts - 1)) {
                    if (LOG.isDebugEnabled()) LOG.debug("Problem loading mangement-plane memento attempt "+(i+1)+"/"+maxLoadAttempts+"; retrying", e);
                }
                lastException = e;
            }
        }
        throw new IllegalStateException("Failed to load mangement-plane memento "+maxLoadAttempts+" consecutive times", lastException);
    }

    protected ManagementNodeSyncRecord createManagementNodeSyncRecord(boolean useLocalTimestampAsRemoteTimestamp) {
        long timestamp = currentTimeMillis();
        brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord.Builder builder = BasicManagementNodeSyncRecord.builder()
                .brooklynVersion(BrooklynVersion.get())
                .nodeId(ownNodeId)
                .status(getNodeState())
                .priority(getPriority())
                .localTimestamp(timestamp)
                .uri(managementContext.getManagementNodeUri().orNull());
        if (useLocalTimestampAsRemoteTimestamp)
            builder.remoteTimestamp(timestamp);
        else if (optionalRemoteTickerUtc!=null) {
            builder.remoteTimestamp(optionalRemoteTickerUtc.read());
        }
        return builder.build();
    }
    
    /**
     * Gets the current time, using the {@link #localTickerUtc}. Normally this is equivalent of {@link System#currentTimeMillis()},
     * but in test environments a custom {@link Ticker} can be injected via {@link #setLocalTicker(Ticker)} to allow testing of
     * specific timing scenarios.
     */
    protected long currentTimeMillis() {
        return localTickerUtc.read();
    }

    /**
     * Infers the health of a node - if it last reported itself as healthy (standby or master), but we haven't heard 
     * from it in a long time then report that node as failed; otherwise report its health as-is.
     */
    private class MarkAwolNodes implements Function<ManagementNodeSyncRecord, ManagementNodeSyncRecord> {
        private final ManagementNodeSyncRecord referenceNode;
        private MarkAwolNodes(ManagementNodeSyncRecord referenceNode) {
            this.referenceNode = referenceNode;
        }
        @Nullable
        @Override
        public ManagementNodeSyncRecord apply(@Nullable ManagementNodeSyncRecord input) {
            if (input == null) return null;
            if (!(input.getStatus() == ManagementNodeState.STANDBY || input.getStatus() == ManagementNodeState.HOT_STANDBY || input.getStatus() == ManagementNodeState.MASTER)) return input;
            if (isHeartbeatOk(input, referenceNode)) return input;
            return BasicManagementNodeSyncRecord.builder()
                    .from(input)
                    .status(ManagementNodeState.FAILED)
                    .build();
        }
    }
    
    @Override
    public String toString() {
        return super.toString()+"[node:"+ownNodeId+";running="+running+"]";
    }
}
