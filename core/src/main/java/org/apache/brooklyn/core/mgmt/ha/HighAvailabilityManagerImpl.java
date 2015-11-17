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
package org.apache.brooklyn.core.mgmt.ha;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityManager;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeSyncRecord;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecord;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecordPersister;
import org.apache.brooklyn.api.mgmt.ha.MementoCopyMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecordPersister.Delta;
import org.apache.brooklyn.api.mgmt.rebind.RebindManager;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.BrooklynVersion;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.catalog.internal.CatalogDto;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.ha.BasicMasterChooser.AlphabeticMasterChooser;
import org.apache.brooklyn.core.mgmt.ha.dto.BasicManagementNodeSyncRecord;
import org.apache.brooklyn.core.mgmt.ha.dto.ManagementPlaneSyncRecordImpl;
import org.apache.brooklyn.core.mgmt.ha.dto.ManagementPlaneSyncRecordImpl.Builder;
import org.apache.brooklyn.core.mgmt.internal.BrooklynObjectManagementMode;
import org.apache.brooklyn.core.mgmt.internal.LocalEntityManager;
import org.apache.brooklyn.core.mgmt.internal.LocationManagerInternal;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.internal.ManagementTransitionMode;
import org.apache.brooklyn.core.mgmt.persist.BrooklynPersistenceUtils;
import org.apache.brooklyn.core.mgmt.persist.PersistenceActivityMetrics;
import org.apache.brooklyn.core.mgmt.persist.BrooklynPersistenceUtils.CreateBackupMode;
import org.apache.brooklyn.core.mgmt.rebind.RebindManagerImpl;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.ScheduledTask;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.ReferenceWithError;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
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
 *   <li>calling {@link RebindManager#rebind(ClassLoader, org.apache.brooklyn.api.mgmt.rebind.RebindExceptionHandler, ManagementNodeState)} to read all persisted entity state, and thus reconstitute the entities.
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

    public final ConfigKey<Duration> POLL_PERIOD = ConfigKeys.newConfigKey(Duration.class, "brooklyn.ha.pollPeriod",
        "How often nodes should poll to detect whether master is healthy", Duration.seconds(1));
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
    
    private final static int MAX_NODE_STATE_HISTORY = 200;
    private final List<Map<String,Object>> nodeStateHistory = MutableList.of();
    
    private volatile transient Duration pollPeriodLocalOverride;
    private volatile transient Duration heartbeatTimeoutOverride;

    private volatile ManagementPlaneSyncRecord lastSyncRecord;
    
    private volatile PersistenceActivityMetrics managementStateWritePersistenceMetrics = new PersistenceActivityMetrics();
    private volatile PersistenceActivityMetrics managementStateReadPersistenceMetrics = new PersistenceActivityMetrics();
    private final long startTimeUtc;

    public HighAvailabilityManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
        startTimeUtc = localTickerUtc.read();
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
        this.pollPeriodLocalOverride = val;
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
    public HighAvailabilityManagerImpl setHeartbeatTimeout(Duration val) {
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
    public void changeMode(HighAvailabilityMode startMode, boolean preventElectionOnExplicitStandbyMode, boolean failOnExplicitModesIfUnusual) {
        if (!running) {
            // if was not running then start as disabled mode, then proceed as normal
            LOG.info("HA changing mode to "+startMode+" from "+getInternalNodeState()+" when not running, forcing an intermediate start as DISABLED then will convert to "+startMode);
            start(HighAvailabilityMode.DISABLED);
        }
        if (getNodeState()==ManagementNodeState.FAILED || getNodeState()==ManagementNodeState.INITIALIZING) {
            if (startMode!=HighAvailabilityMode.DISABLED) {
                // if coming from FAILED (or INITIALIZING because we skipped start call) then treat as initializing
                setInternalNodeState(ManagementNodeState.INITIALIZING);
            }
        }
        
        ownNodeId = managementContext.getManagementNodeId();
        // TODO Small race in that we first check, and then we'll do checkMaster() on first poll,
        // so another node could have already become master or terminated in that window.
        ManagementNodeSyncRecord existingMaster = hasHealthyMaster();
        boolean weAreRecognisedAsMaster = existingMaster!=null && ownNodeId.equals(existingMaster.getNodeId());
        boolean weAreMasterLocally = getInternalNodeState()==ManagementNodeState.MASTER;
        
        // catch error in some tests where mgmt context has a different mgmt context
        if (managementContext.getHighAvailabilityManager()!=this)
            throw new IllegalStateException("Cannot start an HA manager on a management context with a different HA manager!");
        
        if (weAreMasterLocally) {
            // demotion may be required; do this before triggering an election
            switch (startMode) {
            case MASTER:
            case AUTO:
            case DISABLED:
                // no action needed, will do anything necessary below (or above)
                break;
            case HOT_STANDBY: 
            case HOT_BACKUP: 
            case STANDBY: 
                demoteTo(ManagementNodeState.of(startMode).get()); break;
            default:
                throw new IllegalStateException("Unexpected high availability mode "+startMode+" requested for "+this);
            }
        }
        
        ManagementNodeState oldState = getInternalNodeState();
        
        // now do election
        switch (startMode) {
        case AUTO:
            // don't care; let's start and see if we promote ourselves
            if (getInternalNodeState()==ManagementNodeState.INITIALIZING) {
                setInternalNodeState(ManagementNodeState.STANDBY);
            }
            publishAndCheck(true);
            switch (getInternalNodeState()) {
            case HOT_BACKUP:
                if (!nodeStateTransitionComplete) throw new IllegalStateException("Cannot switch to AUTO when in the middle of a transition to "+getInternalNodeState());
                // else change us to standby, desiring to go to hot standby, and continue to below
                setInternalNodeState(ManagementNodeState.STANDBY);
                startMode = HighAvailabilityMode.HOT_BACKUP;
            case HOT_STANDBY:
            case STANDBY:
                if (getInternalNodeState()==ManagementNodeState.STANDBY && oldState==ManagementNodeState.INITIALIZING && startMode!=HighAvailabilityMode.HOT_BACKUP
                        && BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY)) {
                    // auto requested; not promoted; so it should become hot standby
                    startMode = HighAvailabilityMode.HOT_STANDBY;
                }
                ManagementPlaneSyncRecord newState = loadManagementPlaneSyncRecord(true);
                String masterNodeId = newState.getMasterNodeId();
                ManagementNodeSyncRecord masterNodeDetails = newState.getManagementNodes().get(masterNodeId);
                LOG.info("Management node "+ownNodeId+" running as HA " + getInternalNodeState() + " autodetected"
                        + (startMode == HighAvailabilityMode.HOT_STANDBY || startMode == HighAvailabilityMode.HOT_BACKUP ? 
                            " (will change to "+startMode+")" : "")
                        + ", " +
                    (Strings.isBlank(masterNodeId) ? "no master currently (other node should promote itself soon)" : "master "
                        + (existingMaster==null ? "(new) " : "")
                        + "is "+masterNodeId +
                        (masterNodeDetails==null || masterNodeDetails.getUri()==null ? " (no url)" : " at "+masterNodeDetails.getUri())));
                break;
            case MASTER:
                LOG.info("Management node "+ownNodeId+" running as HA MASTER autodetected");
                break;
            default:
                throw new IllegalStateException("Management node "+ownNodeId+" set to HA AUTO, encountered unexpected mode "+getInternalNodeState());
            }
            break;
        case MASTER:
            if (!failOnExplicitModesIfUnusual || existingMaster==null) {
                promoteToMaster();
                if (existingMaster!=null) {
                    LOG.info("Management node "+ownNodeId+" running as HA MASTER explicitly");
                } else {
                    LOG.info("Management node "+ownNodeId+" running as HA MASTER explicitly, stealing from "+existingMaster);
                }
            } else if (!weAreRecognisedAsMaster) {
                throw new IllegalStateException("Master already exists; cannot run as master (master "+existingMaster.toVerboseString()+"); "
                    + "to trigger a promotion, set a priority and demote the current master");
            } else {
                LOG.info("Management node "+ownNodeId+" already running as HA MASTER, when set explicitly");
            }
            break;
        case HOT_BACKUP:
            setInternalNodeState(ManagementNodeState.HOT_BACKUP);
            // then continue into next block
        case STANDBY:
        case HOT_STANDBY:
            if (startMode!=HighAvailabilityMode.HOT_BACKUP) {
                if (ManagementNodeState.isHotProxy(getInternalNodeState()) && startMode==HighAvailabilityMode.HOT_STANDBY) {
                    // if was hot_backup, we can immediately go hot_standby
                    setInternalNodeState(ManagementNodeState.HOT_STANDBY);
                } else {
                    // from any other state, set standby, then perhaps switch to hot_standby later on (or might become master in the next block)
                    setInternalNodeState(ManagementNodeState.STANDBY);
                }
            }
            if (ManagementNodeState.isStandby(getInternalNodeState())) {
                if (!preventElectionOnExplicitStandbyMode) {
                    publishAndCheck(true);
                }
                if (failOnExplicitModesIfUnusual && existingMaster==null) {
                    LOG.error("Management node "+ownNodeId+" detected no master when "+startMode+" requested and existing master required; failing.");
                    throw new IllegalStateException("No existing master; cannot start as "+startMode);
                }
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
                    message += "); no master currently"; 
                    if (startMode != HighAvailabilityMode.HOT_BACKUP) message += " (subsequent election may repair)";
                } else {
                    message += "); master "+newState.getMasterNodeId();
                }
            }
            LOG.info(message);
            break;
        case DISABLED:
            // safe just to run even if we weren't master
            LOG.info("Management node "+ownNodeId+" HA DISABLED (was "+getInternalNodeState()+")");
            demoteTo(ManagementNodeState.FAILED);
            if (pollingTask!=null) pollingTask.cancel(true);
            break;
        default:
            throw new IllegalStateException("Unexpected high availability mode "+startMode+" requested for "+this);
        }
        
        if ((startMode==HighAvailabilityMode.HOT_STANDBY || startMode==HighAvailabilityMode.HOT_BACKUP)) {
            if (!ManagementNodeState.isHotProxy(oldState)) {
                // now transition to hot proxy
                nodeStateTransitionComplete = false;
                if (startMode==HighAvailabilityMode.HOT_STANDBY) {
                    // if it should be hot standby, then we may need to promote
                    // inform the world that we are transitioning (but not eligible for promotion while going in to hot standby)
                    // (no harm in doing this twice)
                    publishHealth();
                }
                try {
                    activateHotProxy(ManagementNodeState.of(startMode).get()).get();
                    // error above now throws
                    nodeStateTransitionComplete = true;
                    publishHealth();

                    if (getNodeState()==ManagementNodeState.HOT_STANDBY || getNodeState()==ManagementNodeState.HOT_BACKUP) {
                        LOG.info("Management node "+ownNodeId+" now running as HA "+getNodeState()+"; "
                            + managementContext.getApplications().size()+" application"+Strings.s(managementContext.getApplications().size())+" loaded");
                    } else {
                        // shouldn't come here, we should have gotten an error above
                        LOG.warn("Management node "+ownNodeId+" unable to promote to "+startMode+" (currently "+getNodeState()+"); "
                            + "(see log for further details)");
                    }
                } catch (Exception e) {
                    LOG.warn("Management node "+ownNodeId+" unable to promote to "+startMode+" (currently "+getNodeState()+"); rethrowing: "+Exceptions.collapseText(e));
                    nodeStateTransitionComplete = true;
                    throw Exceptions.propagate(e);
                }
            } else {
                // transitioning among hot proxy states - tell the rebind manager
                managementContext.getRebindManager().stopReadOnly();
                managementContext.getRebindManager().startReadOnly(ManagementNodeState.of(startMode).get());
                nodeStateTransitionComplete = true;
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
        setInternalNodeState(newState);
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
        return getInternalNodeState();
    }
    
    protected ManagementNodeState getInternalNodeState() {
        return nodeState;
    }
    
    protected void setInternalNodeState(ManagementNodeState newState) {
        ManagementNodeState oldState = getInternalNodeState();
        synchronized (nodeStateHistory) {
            if (this.nodeState != newState) {
                nodeStateHistory.add(0, MutableMap.<String,Object>of("state", newState, "timestamp", currentTimeMillis()));
                while (nodeStateHistory.size()>MAX_NODE_STATE_HISTORY) {
                    nodeStateHistory.remove(nodeStateHistory.size()-1);
                }
            }
            ((RebindManagerImpl)managementContext.getRebindManager()).setAwaitingInitialRebind(running &&
                (ManagementNodeState.isHotProxy(newState) || newState==ManagementNodeState.MASTER));
            this.nodeState = newState;
        }
        
        if (ManagementNodeState.isHotProxy(oldState) && !ManagementNodeState.isHotProxy(newState)) {
            // could perhaps promote standby items on some transitions; but for now we stop the old read-only and re-load them
            // TODO ideally there'd be an incremental rebind as well as an incremental persist
            managementContext.getRebindManager().stopReadOnly();
            clearManagedItems(ManagementTransitionMode.transitioning(BrooklynObjectManagementMode.LOADED_READ_ONLY, BrooklynObjectManagementMode.UNMANAGED_PERSISTED));
        }
    }

    @Override
    public ManagementNodeState getNodeState() {
        ManagementNodeState myNodeState = getInternalNodeState();
        if (myNodeState==ManagementNodeState.FAILED) return getInternalNodeState();
        // if target is master then we claim already being master, to prevent other nodes from taking it
        // (we may fail subsequently of course)
        if (myNodeState==ManagementNodeState.MASTER) return myNodeState;
        
        if (!nodeStateTransitionComplete) return ManagementNodeState.INITIALIZING;
        return myNodeState;
    }

    public ManagementPlaneSyncRecord getLastManagementPlaneSyncRecord() {
        return lastSyncRecord;
    }
    
    @SuppressWarnings("unchecked")
    protected void registerPollTask() {
        final Runnable job = new Runnable() {
            private boolean lastFailed;
            
            @Override public void run() {
                try {
                    publishAndCheck(false);
                    lastFailed = false;
                } catch (Exception e) {
                    if (running) {
                        if (lastFailed) {
                            if (LOG.isDebugEnabled()) LOG.debug("Recurring problem in HA-poller: "+e, e);
                        } else {
                            LOG.error("Problem in HA-poller: "+e, e);
                            lastFailed = true;
                        }
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
                return Tasks.builder().dynamic(false).body(job).displayName("HA poller task").tag(BrooklynTaskTags.TRANSIENT_TASK_TAG)
                    .description("polls HA status to see whether this node should promote").build();
            }
        };
        
        Duration pollPeriod = getPollPeriod();
        LOG.debug("Registering poll task for "+this+", period "+pollPeriod);
        if (pollPeriod.equals(Duration.PRACTICALLY_FOREVER)) {
            // don't schedule - used for tests
            // (scheduling fires off one initial task in the background before the delay, 
            // which affects tests that want to know exactly when publishing happens;
            // TODO would be nice if scheduled task had a "no initial submission" flag )
        } else {
            if (pollingTask!=null) pollingTask.cancel(true);
            
            ScheduledTask task = new ScheduledTask(MutableMap.of("period", pollPeriod, "displayName", "scheduled:[HA poller task]"), taskFactory);
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
        
        Stopwatch timer = Stopwatch.createStarted();
        try {
            ManagementNodeSyncRecord memento = createManagementNodeSyncRecord(false);
            Delta delta = ManagementPlaneSyncRecordDeltaImpl.builder().node(memento).build();
            persister.delta(delta);
            managementStateWritePersistenceMetrics.noteSuccess(Duration.of(timer));
            if (LOG.isTraceEnabled()) LOG.trace("Published management-node health: {}", memento);
        } catch (Throwable t) {
            managementStateWritePersistenceMetrics.noteFailure(Duration.of(timer));
            managementStateWritePersistenceMetrics.noteError(t.toString());
            LOG.debug("Error publishing management-node health (rethrowing): "+t);
            throw Exceptions.propagate(t);
        }
    }
    
    public void publishClearNonMaster() {
        ManagementPlaneSyncRecord plane = getLastManagementPlaneSyncRecord();
        if (plane==null || persister==null) {
            LOG.warn("Cannot clear HA node records; HA not active (or not yet loaded)");
            return;
        }
        org.apache.brooklyn.core.mgmt.ha.ManagementPlaneSyncRecordDeltaImpl.Builder db = ManagementPlaneSyncRecordDeltaImpl.builder();
        for (Map.Entry<String,ManagementNodeSyncRecord> node: plane.getManagementNodes().entrySet()) {
            // only keep a node if it both claims master and is recognised as master;
            // else ex-masters who died are kept around!
            if (!ManagementNodeState.MASTER.equals(node.getValue().getStatus()) || 
                    !Objects.equal(plane.getMasterNodeId(), node.getValue().getNodeId())) {
                db.removedNodeId(node.getKey());
            }
        }
        persister.delta(db.build());
        // then get, so model is updated
        loadManagementPlaneSyncRecord(true);
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
        
        if (getNodeState()==ManagementNodeState.FAILED || getNodeState()==ManagementNodeState.HOT_BACKUP) {
            // if failed or hot backup then we can't promote ourselves, so no point in checking who is master
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
            demoteTo(
                BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY) ?
                    ManagementNodeState.HOT_STANDBY : ManagementNodeState.STANDBY);
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
        String message = "Management node "+ownNodeId+" detected ";
        String currMasterSummary = currMasterNodeId + "(" + (currMasterNodeRecord==null ? "<none>" : timestampString(currMasterNodeRecord.getRemoteTimestamp())) + ")";
        if (weAreNewMaster && (ownNodeRecord.getStatus() == ManagementNodeState.MASTER)) {
            LOG.warn(message + "we must reassert master status, as was stolen and then failed at "+
                (currMasterNodeRecord==null ? "a node which has gone away" : currMasterSummary));
            publishPromotionToMaster();
            publishHealth();
            return;
        }
        
        if (!initializing) {
            if (weAreNewMaster) {
                message += "we should be master, changing from ";
            }
            else if (currMasterNodeRecord==null && newMasterNodeId==null) message += "master change attempted but no candidates ";
            else message += "master change, from ";
            message += currMasterSummary + " to "
                + (newMasterNodeId == null ? "<none>" :
                    (weAreNewMaster ? "us " : "")
                    + newMasterNodeId + " (" + timestampString(newMasterNodeRecord.getRemoteTimestamp()) + ")" 
                    + (newMasterNodeUri!=null ? " "+newMasterNodeUri : "")  );
            // always log, if you're looking at a standby node it's useful to see the new master's URL
            LOG.info(message);
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
            LOG.warn("Ignoring promote-to-master request, as HighAvailabilityManager is not running");
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
        setInternalNodeState(ManagementNodeState.MASTER);
        publishPromotionToMaster();
        try {
            managementContext.getRebindManager().rebind(managementContext.getCatalogClassLoader(), null, getInternalNodeState());
        } catch (Exception e) {
            LOG.error("Management node "+managementContext.getManagementNodeId()+" enountered problem during rebind when promoting self to master; demoting to FAILED and rethrowing: "+e);
            demoteTo(ManagementNodeState.FAILED);
            throw Exceptions.propagate(e);
        }
        managementContext.getRebindManager().start();
    }
    
    protected void backupOnDemotionIfNeeded() {
        if (managementContext.getBrooklynProperties().getConfig(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED_ON_DEMOTION)) {
            BrooklynPersistenceUtils.createBackup(managementContext, CreateBackupMode.DEMOTION, MementoCopyMode.LOCAL);
        }
    }

    /** @deprecated since 0.7.0, use {@link #demoteTo(ManagementNodeState)} */ @Deprecated
    protected void demoteToFailed() {
        demoteTo(ManagementNodeState.FAILED);
    }
    /** @deprecated since 0.7.0, use {@link #demoteTo(ManagementNodeState)} */ @Deprecated
    protected void demoteToStandby(boolean hot) {
        demoteTo(hot ? ManagementNodeState.HOT_STANDBY : ManagementNodeState.STANDBY);
    }
    
    protected void demoteTo(ManagementNodeState toState) {
        if (toState!=ManagementNodeState.FAILED && !running) {
            LOG.warn("Ignoring demote-from-master request, as HighAvailabilityManager is no longer running");
            return;
        }
        boolean wasMaster = (getInternalNodeState() == ManagementNodeState.MASTER);
        if (wasMaster) backupOnDemotionIfNeeded();
        // TODO target may be RO ?
        ManagementTransitionMode mode = ManagementTransitionMode.transitioning(
            wasMaster ? BrooklynObjectManagementMode.MANAGED_PRIMARY : BrooklynObjectManagementMode.LOADED_READ_ONLY,
            BrooklynObjectManagementMode.UNMANAGED_PERSISTED);

        nodeStateTransitionComplete = false;
        
        switch (toState) {
        case FAILED: 
        case HOT_BACKUP:
        case STANDBY:
            setInternalNodeState(toState); break;
        case HOT_STANDBY:
            setInternalNodeState(ManagementNodeState.STANDBY); break;
        default:
            throw new IllegalStateException("Illegal target state: "+toState);
        }
        onDemotionStopItems(mode);
        nodeStateTransitionComplete = true;
        publishDemotion(wasMaster);
        
        if (toState==ManagementNodeState.HOT_BACKUP || toState==ManagementNodeState.HOT_STANDBY) {
            nodeStateTransitionComplete = false;
            try {
                activateHotProxy(toState).get();
            } finally {
                nodeStateTransitionComplete = true;
            }
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
        // start with the root applications
        for (Application app: managementContext.getApplications()) {
            if (((EntityInternal)app).getManagementSupport().isDeployed()) {
                ((LocalEntityManager)((EntityInternal)app).getManagementContext().getEntityManager()).unmanage(app, mode);
            }
        }
        // for active management, call above will remove recursively at present,
        // but for read-only, and if we stop recursively, go through them all
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
    
    /** @deprecated since 0.7.0, use {@link #activateHotProxy(ManagementNodeState)} */ @Deprecated
    protected boolean attemptHotStandby() {
        return activateHotProxy(ManagementNodeState.HOT_STANDBY).getWithoutError();
    }
    
    /** Starts hot standby or hot backup, in foreground
     * <p>
     * In the case of the former, the caller is responsible for publishing health afterwards,
     * but if it fails, this method will {@link #demoteTo(ManagementNodeState)} {@link ManagementNodeState#FAILED}.
     * <p>
     * @return whether the requested {@link ManagementNodeState} was possible;
     * (if not, errors should be stored elsewhere), callers may want to rethrow */
    protected ReferenceWithError<Boolean> activateHotProxy(ManagementNodeState toState) {
        try {
            Preconditions.checkState(nodeStateTransitionComplete==false, "Must be in transitioning state to go into "+toState);
            setInternalNodeState(toState);
            managementContext.getRebindManager().startReadOnly(toState);
            
            return ReferenceWithError.newInstanceWithoutError(true);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            LOG.warn("Unable to change "+ownNodeId+" to "+toState+", switching to FAILED: "+e, e);
            demoteTo(ManagementNodeState.FAILED);
            return ReferenceWithError.newInstanceThrowingError(false, e);
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
        Stopwatch timer = Stopwatch.createStarted();

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
                
                if (i>0) {
                    managementStateReadPersistenceMetrics.noteError("Succeeded only on attempt "+(i+1)+": "+lastException);
                }
                managementStateReadPersistenceMetrics.noteSuccess(Duration.of(timer));
                return result;
            } catch (IOException e) {
                if (i < (maxLoadAttempts - 1)) {
                    if (LOG.isDebugEnabled()) LOG.debug("Problem loading mangement-plane memento attempt "+(i+1)+"/"+maxLoadAttempts+"; retrying", e);
                }
                lastException = e;
            }
        }
        String message = "Failed to load mangement-plane memento "+maxLoadAttempts+" consecutive times";
        managementStateReadPersistenceMetrics.noteError(message+": "+lastException);
        managementStateReadPersistenceMetrics.noteFailure(Duration.of(timer));

        throw new IllegalStateException(message, lastException);
    }

    protected ManagementNodeSyncRecord createManagementNodeSyncRecord(boolean useLocalTimestampAsRemoteTimestamp) {
        long timestamp = currentTimeMillis();
        org.apache.brooklyn.core.mgmt.ha.dto.BasicManagementNodeSyncRecord.Builder builder = BasicManagementNodeSyncRecord.builder()
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
            if (!(input.getStatus() == ManagementNodeState.STANDBY || input.getStatus() == ManagementNodeState.HOT_STANDBY || input.getStatus() == ManagementNodeState.MASTER || input.getStatus() == ManagementNodeState.HOT_BACKUP)) return input;
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
    
    @Override
    public Map<String,Object> getMetrics() {
        Map<String,Object> result = MutableMap.of();
        
        result.put("state", getNodeState());
        result.put("uptime", Time.makeTimeStringRounded(Duration.millis(currentTimeMillis()-startTimeUtc)));
        result.put("currentTimeUtc", currentTimeMillis());
        result.put("startTimeUtc", startTimeUtc);
        result.put("highAvailability", MutableMap.<String,Object>of(
            "priority", getPriority(),
            "pollPeriod", getPollPeriod().toMilliseconds(),
            "heartbeatTimeout", getHeartbeatTimeout().toMilliseconds(),
            "history", nodeStateHistory));
        
        result.putAll(managementContext.getRebindManager().getMetrics());
        result.put("managementStatePersistence", 
            MutableMap.of("read", managementStateReadPersistenceMetrics, "write", managementStateWritePersistenceMetrics));
        
        return result;
    }
    
    @Override
    public long getLastStateChange() {
        if (nodeStateHistory.size() > 0) {
            return (Long)nodeStateHistory.get(0).get("timestamp");
        } else {
            return 0;
        }
    }
    
}
