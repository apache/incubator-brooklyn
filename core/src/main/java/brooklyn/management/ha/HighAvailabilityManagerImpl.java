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
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.plane.dto.BasicManagementNodeSyncRecord;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneSyncRecordImpl;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneSyncRecordImpl.Builder;
import brooklyn.management.Task;
import brooklyn.management.ha.BasicMasterChooser.AlphabeticMasterChooser;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersister.Delta;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
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
 *   <li>calling {@link RebindManager#rebind()} to read all persisted entity state, and thus reconstitute the entities.
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
    
    public static interface PromotionListener {
        public void promotingToMaster();
    }
    
    private static final Logger LOG = LoggerFactory.getLogger(HighAvailabilityManagerImpl.class);

    private final ManagementContextInternal managementContext;
    private volatile String ownNodeId;
    private volatile ManagementPlaneSyncRecordPersister persister;
    private volatile PromotionListener promotionListener;
    private volatile MasterChooser masterChooser = new AlphabeticMasterChooser();
    private volatile Duration pollPeriod = Duration.of(5, TimeUnit.SECONDS);
    private volatile Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
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
    private volatile ManagementNodeState nodeState = ManagementNodeState.UNINITIALISED;

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
    
    public HighAvailabilityManagerImpl setPollPeriod(Duration val) {
        this.pollPeriod = checkNotNull(val, "pollPeriod");
        if (running && pollingTask != null) {
            pollingTask.cancel(true);
            registerPollTask();
        }
        return this;
    }

    public HighAvailabilityManagerImpl setMasterChooser(MasterChooser val) {
        this.masterChooser = checkNotNull(val, "masterChooser");
        return this;
    }

    public HighAvailabilityManagerImpl setHeartbeatTimeout(Duration val) {
        this.heartbeatTimeout = checkNotNull(val, "heartbeatTimeout");
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
     * If this is supplied, one must also set {@link ManagementPlaneSyncRecordPersisterToObjectStore#allowRemoteTimestampInMemento()}. */
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
        running = false;
        ownNodeId = managementContext.getManagementNodeId();
        // this is notionally the master, just not running; see javadoc for more info
        nodeState = ManagementNodeState.MASTER;
    }

    @Override
    public void start(HighAvailabilityMode startMode) {
        ownNodeId = managementContext.getManagementNodeId();
        nodeState = ManagementNodeState.STANDBY;
        running = true;
        
        // TODO Small race in that we first check, and then we'll do checkMaster() on first poll,
        // so another node could have already become master or terminated in that window.
        ManagementNodeSyncRecord existingMaster = hasHealthyMaster();
        
        switch (startMode) {
        case AUTO:
            // don't care; let's start and see if we promote ourselves
            publishAndCheck(true);
            if (nodeState == ManagementNodeState.STANDBY) {
                String masterNodeId = getManagementPlaneSyncState().getMasterNodeId();
                ManagementNodeSyncRecord masterNodeDetails = getManagementPlaneSyncState().getManagementNodes().get(masterNodeId);
                LOG.info("Management node "+ownNodeId+" started as HA STANDBY autodetected, master is "+masterNodeId+
                    (masterNodeDetails==null || masterNodeDetails.getUri()==null ? " (no url)" : " at "+masterNodeDetails.getUri()));
            } else {
                LOG.info("Management node "+ownNodeId+" started as HA MASTER autodetected");
            }
            break;
        case MASTER:
            if (existingMaster == null) {
                promoteToMaster();
                LOG.info("Management node "+ownNodeId+" started as HA MASTER explicitly");
            } else {
                throw new IllegalStateException("Master already exists; cannot start as master ("+existingMaster.toVerboseString()+")");
            }
            break;
        case STANDBY:
            if (existingMaster != null) {
                publishAndCheck(true);
                LOG.info("Management node "+ownNodeId+" started as HA STANDBY explicitly, status "+nodeState);
            } else {
                throw new IllegalStateException("No existing master; cannot start as standby");
            }
            break;
        default:
            throw new IllegalStateException("Unexpected high availability start-mode "+startMode+" for "+this);
        }
        
        registerPollTask();
    }

    @Override
    public void stop() {
        boolean wasRunning = running; // ensure idempotent
        
        running = false;
        nodeState = ManagementNodeState.TERMINATED;
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
    
    @Override
    public ManagementNodeState getNodeState() {
        return nodeState;
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
                return new BasicTask<Void>(job);
            }
        };
        
        if (pollPeriod==null || pollPeriod.equals(Duration.PRACTICALLY_FOREVER)) {
            // don't schedule - used for tests
            // (scheduling fires off one initial task in the background before the delay, 
            // which affects tests that want to know exactly when publishing happens;
            // TODO would be nice if scheduled task had a "no initial submission" flag )
        } else {
            ScheduledTask task = new ScheduledTask(MutableMap.of("period", pollPeriod), taskFactory);
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
    
    /**
     * Publishes (via {@link #persister}) the state of this management node with itself set to master.
     */
    protected synchronized void publishDemotionFromMasterOnFailure() {
        checkState(getNodeState() == ManagementNodeState.FAILED, "node status must be failed on publish, but is %s", getNodeState());
        publishDemotionFromMaster(true);
    }
    
    protected synchronized void publishDemotionFromMaster(boolean clearMaster) {
        checkState(getNodeState() != ManagementNodeState.MASTER, "node status must not be master when demoting", getNodeState());
        
        if (persister == null) {
            LOG.info("Cannot publish management-node health as no persister");
            return;
        }
        
        ManagementNodeSyncRecord memento = createManagementNodeSyncRecord(false);
        ManagementPlaneSyncRecordDeltaImpl.Builder deltaBuilder = ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(memento);
        if (clearMaster) deltaBuilder.clearMaster(ownNodeId);
        
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
    
    protected ManagementNodeState toNodeStateForPersistence(ManagementNodeState nodeState) {
        // uninitialized is set as null - TODO confirm that's necessary; nicer if we don't need this method at all
        if (nodeState == ManagementNodeState.UNINITIALISED) return null;
        return nodeState;
    }
    
    protected boolean isHeartbeatOk(ManagementNodeSyncRecord masterNode, ManagementNodeSyncRecord meNode) {
        if (masterNode==null || meNode==null) return false;
        Long timestampMaster = masterNode.getRemoteTimestamp();
        Long timestampMe = meNode.getRemoteTimestamp();
        if (timestampMaster==null || timestampMe==null) return false;
        return (timestampMe - timestampMaster) <= heartbeatTimeout.toMilliseconds();
    }
    
    protected ManagementNodeSyncRecord hasHealthyMaster() {
        ManagementPlaneSyncRecord memento = loadManagementPlaneSyncRecord(false);
        
        String nodeId = memento.getMasterNodeId();
        ManagementNodeSyncRecord masterMemento = (nodeId == null) ? null : memento.getManagementNodes().get(nodeId);
        
        boolean result = masterMemento != null && masterMemento.getStatus() == ManagementNodeState.MASTER
                && isHeartbeatOk(masterMemento, memento.getManagementNodes().get(ownNodeId));
        
        if (LOG.isDebugEnabled()) LOG.debug("Healthy-master check result={}; masterId={}; memento=",
                new Object[] {result, nodeId, (masterMemento == null ? "<none>" : masterMemento.toVerboseString())});
        
        return (result ? masterMemento : null);
    }
    
    /**
     * Looks up the state of all nodes in the management plane, and checks if the master is still ok.
     * If it's not then determines which node should be promoted to master. If it is ourself, then promotes.
     */
    protected void checkMaster(boolean initializing) {
        ManagementPlaneSyncRecord memento = loadManagementPlaneSyncRecord(false);
        
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
                    LOG.error("HA subsystem detected change of master, stolen from us ("+ownNodeId+"), deferring to "+currMasterNodeId);
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
            demoteToStandby();
            return;
        }
        
        // Need to choose a new master
        newMasterNodeRecord = masterChooser.choose(memento, heartbeatTimeout, ownNodeId);
        
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
                    heartbeatTimeout
                });
        }
        if (!initializing) {
            LOG.warn("HA subsystem detected change of master, from " 
                + currMasterNodeId + " (" + (currMasterNodeRecord==null ? "?" : currMasterNodeRecord.getRemoteTimestamp()) + ")"
                + " to "
                + (newMasterNodeId == null ? "<none>" :
                    (weAreNewMaster ? "us " : "")
                    + newMasterNodeId + " (" + newMasterNodeRecord.getRemoteTimestamp() + ")" 
                    + (newMasterNodeUri!=null ? " "+newMasterNodeUri : "")  ));
        }

        // New master is ourself: promote
        if (weAreNewMaster) {
            promoteToMaster();
        }
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
        nodeState = ManagementNodeState.MASTER;
        publishPromotionToMaster();
        try {
            managementContext.getRebindManager().rebind(managementContext.getCatalog().getRootClassLoader());
        } catch (Exception e) {
            LOG.error("Management node enountered problem during rebind when promoting self to master; demoting to FAILED and rethrowing: "+e);
            nodeState = ManagementNodeState.FAILED;
            publishDemotionFromMasterOnFailure();
            throw Exceptions.propagate(e);
        }
        managementContext.getRebindManager().start();
    }

    protected void demoteToStandby() {
        if (!running) {
            LOG.warn("Ignoring demote-from-master request, as HighAvailabilityManager is no longer running");
            return;
        }

        nodeState = ManagementNodeState.STANDBY;
        managementContext.getRebindManager().stop();
        for (Application app: managementContext.getApplications())
            Entities.unmanage(app);
        publishDemotionFromMaster(false);
    }

    /**
     * @param reportCleanedState - if true, the record for this mgmt node will be replaced with the
     * actual current status known in this JVM (may be more recent than what is persisted);
     * for most purposes there is little difference but in some cases the local node being updated
     * may be explicitly wanted or not wanted
     */
    protected ManagementPlaneSyncRecord loadManagementPlaneSyncRecord(boolean reportCleanedState) {
        if (disabled) {
            // if HA is disabled, then we are the only node - no persistence; just load a memento to describe this node
            Builder builder = ManagementPlaneSyncRecordImpl.builder()
                .node(createManagementNodeSyncRecord(true));
            if (getNodeState() == ManagementNodeState.MASTER) {
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
                
                if (reportCleanedState) {
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
                    if (getNodeState() == ManagementNodeState.MASTER) {
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
                .status(toNodeStateForPersistence(getNodeState()))
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
     * Gets the current time, using the {@link #tickerUtc}. Normally this is equivalent of {@link System#currentTimeMillis()},
     * but in test environments a custom {@link Ticker} can be injected via {@link #setTicker(Ticker)} to allow testing of
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
            if (!(input.getStatus() == ManagementNodeState.STANDBY || input.getStatus() == ManagementNodeState.MASTER)) return input;
            if (isHeartbeatOk(input, referenceNode)) return input;
            return BasicManagementNodeSyncRecord.builder()
                    .from(input)
                    .status(ManagementNodeState.FAILED)
                    .build();
        }
    }
}
