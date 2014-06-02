package brooklyn.management.ha;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.BrooklynVersion;
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
import com.google.common.base.Ticker;

/**
 * This is the guts of the high-availability solution in Brooklyn.
 * <p>
 * Multiple brooklyn nodes can be started to form a single management plane, where one node is 
 * designated master and the others are "warm standbys". On termination or failure of the master,
 * the standbys deterministically decide which standby should become master (see {@link MasterChooser}).
 * That standby promotes itself.
 * <p>
 * The management nodes communicate their health/status via the {@link ManagementPlaneSyncRecordPersister}.
 * For example, if using {@link ManagementPlaneSyncRecordPersisterToMultiFile} with a shared NFS mount, 
 * then each management-node periodically writes its state. This acts as a heartbeat, being read by
 * the other management-nodes.
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

    // TODO Should detect if multiple active nodes believe that they are master (possibly in MasterChooser?),
    // and respond accordingly.
    // Could support "demotingFromMaster" (e.g. restart management context?!).
    
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
    private volatile Ticker ticker = new Ticker() {
            @Override
            public long read() {
                return System.currentTimeMillis();
            }
        };
    
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

    /** A ticker that reads in milliseconds */
    public HighAvailabilityManagerImpl setTicker(Ticker val) {
        this.ticker = checkNotNull(val, "ticker");
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
            doPollTask();
            if (nodeState == ManagementNodeState.STANDBY) {
                LOG.info("Management node (with high availability mode 'auto') started as standby");
            } else {
                LOG.info("Management node (with high availability mode 'auto') started as master");
            }
            break;
        case MASTER:
            if (existingMaster == null) {
                promoteToMaster();
                LOG.info("Management node (with high availability mode 'master') started as master");
            } else {
                throw new IllegalStateException("Master already exists; cannot start as master ("+existingMaster.toVerboseString()+")");
            }
            break;
        case STANDBY:
            if (existingMaster != null) {
                doPollTask();
                LOG.info("Management node (with high availability mode 'standby') started; status "+nodeState);
            } else {
                throw new IllegalStateException("No existing master; cannot start as standby");
            }
            break;
        default:
            throw new IllegalStateException("Unexpected high availability start-mode "+startMode);
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
                    doPollTask();
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
        
        ScheduledTask task = new ScheduledTask(MutableMap.of("period", pollPeriod), taskFactory);
        pollingTask = managementContext.getExecutionManager().submit(task);
    }
    
    protected synchronized void doPollTask() {
        publishHealth();
        checkMaster();
    }
    
    protected synchronized void publishHealth() {
        if (persister == null) {
            LOG.info("Cannot publish management-node health as no persister");
            return;
        }
        
        ManagementNodeSyncRecord memento = createManagementNodeSyncRecord();
        Delta delta = ManagementPlaneSyncRecordDeltaImpl.builder().node(memento).build();
        persister.delta(delta);
        if (LOG.isTraceEnabled()) LOG.trace("Published management-node health: {}", memento);
    }
    
    /**
     * Publishes (via {@link #persister}) the state of this management node with itself set to master.
     */
    protected synchronized void publishDemotionFromMasterOnFailure() {
        checkState(getNodeState() == ManagementNodeState.FAILED, "node status must be failed on publish, but is %s", getNodeState());
        
        if (persister == null) {
            LOG.info("Cannot publish management-node health as no persister");
            return;
        }
        
        ManagementNodeSyncRecord memento = createManagementNodeSyncRecord();
        Delta delta = ManagementPlaneSyncRecordDeltaImpl.builder()
                .node(memento)
                .clearMaster(ownNodeId)
                .build();
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
        
        ManagementNodeSyncRecord memento = createManagementNodeSyncRecord();
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
    
    protected boolean isHeartbeatOk(ManagementNodeSyncRecord memento, long now) {
        long timestamp = memento.getTimestampUtc();
        return (now - timestamp) <= heartbeatTimeout.toMilliseconds();
    }
    
    protected ManagementNodeSyncRecord hasHealthyMaster() {
        long now = currentTimeMillis();
        ManagementPlaneSyncRecord memento = loadManagementPlaneSyncRecord(false);
        
        String nodeId = memento.getMasterNodeId();
        ManagementNodeSyncRecord nodeMemento = (nodeId == null) ? null : memento.getManagementNodes().get(nodeId);
        
        boolean result = nodeMemento != null && nodeMemento.getStatus() == ManagementNodeState.MASTER
                && isHeartbeatOk(nodeMemento, now);
        
        if (LOG.isDebugEnabled()) LOG.debug("Healthy-master check result={}; masterId={}; memento=",
                new Object[] {result, nodeId, (nodeMemento == null ? "<none>" : nodeMemento.toVerboseString())});
        
        return (result ? nodeMemento : null);
    }
    
    /**
     * Looks up the state of all nodes in the management plane, and checks if the master is still ok.
     * If it's not then determines which node should be promoted to master. If it is ourself, then promotes.
     */
    protected void checkMaster() {
        long now = currentTimeMillis();
        ManagementPlaneSyncRecord memento = loadManagementPlaneSyncRecord(false);
        
        String masterNodeId = memento.getMasterNodeId();
        ManagementNodeSyncRecord masterNodeMemento = memento.getManagementNodes().get(masterNodeId);
        ManagementNodeSyncRecord ownNodeMemento = memento.getManagementNodes().get(ownNodeId);
        
        if (masterNodeMemento != null && masterNodeMemento.getStatus() == ManagementNodeState.MASTER && isHeartbeatOk(masterNodeMemento, now)) {
            // master still seems healthy
            if (LOG.isTraceEnabled()) LOG.trace("Existing master healthy: master={}", masterNodeMemento.toVerboseString());
            return;
        } else if (ownNodeMemento == null || !isHeartbeatOk(ownNodeMemento, now)) {
            // our heartbeats are also out-of-date! perhaps something wrong with persistence? just log, and don't over-react!
            if (ownNodeMemento == null) {
                LOG.error("No management node memento for self ("+ownNodeId+"); perhaps perister unwritable? "
                        + "Master ("+masterNodeId+") reported failed but no-op as cannot tell conclusively");
            } else {
                LOG.error("This management node ("+ownNodeId+") memento heartbeats out-of-date; perhaps perister unwritable? "
                        + "Master ("+masterNodeId+") reported failed but no-op as cannot tell conclusively"
                        + ": self="+ownNodeMemento.toVerboseString());
            }
            return;
        } else if (ownNodeId.equals(masterNodeId)) {
            // we are supposed to be the master, but seem to be unhealthy!
            LOG.warn("This management node ("+ownNodeId+") supposed to be master but reportedly unhealthy? "
                    + "no-op as expect other node to fix: self="+ownNodeMemento.toVerboseString());
            return;
        }
        
        // Need to choose a new master
        ManagementNodeSyncRecord newMasterRecord = masterChooser.choose(memento, heartbeatTimeout, ownNodeId, now);
        String newMasterNodeId = (newMasterRecord == null) ? null : newMasterRecord.getNodeId();
        boolean newMasterIsSelf = ownNodeId.equals(newMasterNodeId);
        
        LOG.warn("Management node master-promotion required: newMaster={}; oldMaster={}; plane={}, self={}; heartbeatTimeout={}", 
                new Object[] {
                        (newMasterNodeId == null ? "<none>" : newMasterNodeId),
                        (masterNodeMemento == null ? masterNodeId+" (no memento)": masterNodeMemento.toVerboseString()),
                        memento,
                        ownNodeMemento.toVerboseString(), 
                        heartbeatTimeout
                });

        // New master is ourself: promote
        if (newMasterIsSelf) {
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
            managementContext.getRebindManager().rebind();
        } catch (Exception e) {
            LOG.info("Problem during rebind when promoting node to master; demoting to failed and rethrowing): "+e);
            nodeState = ManagementNodeState.FAILED;
            publishDemotionFromMasterOnFailure();
            throw Exceptions.propagate(e);
        }
        managementContext.getRebindManager().start();
    }

    /**
     * @param replaceLocalNodeWithCurrentRecord - if true, the record for this mgmt node will be replaced with the
     * actual current status known in this JVM (may be more recent than what is on disk);
     * normally there is no reason to care because data is persisted to disk immediately
     * after any significant change, but for fringe cases this is perhaps more accurate (perhaps remove in time?)
     */
    protected ManagementPlaneSyncRecord loadManagementPlaneSyncRecord(boolean replaceLocalNodeWithCurrentRecord) {
        if (disabled) {
            // if HA is disabled, then we are the only node - no persistence; just load a memento to describe this node
            Builder builder = ManagementPlaneSyncRecordImpl.builder()
                .node(createManagementNodeSyncRecord());
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
                
                if (replaceLocalNodeWithCurrentRecord) {
                    Builder builder = ManagementPlaneSyncRecordImpl.builder()
                        .masterNodeId(result.getMasterNodeId())
                        .nodes(result.getManagementNodes().values())
                        .node(createManagementNodeSyncRecord());
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

    protected ManagementNodeSyncRecord createManagementNodeSyncRecord() {
        return BasicManagementNodeSyncRecord.builder()
                .brooklynVersion(BrooklynVersion.get())
                .nodeId(ownNodeId)
                .status(toNodeStateForPersistence(getNodeState()))
                .timestampUtc(currentTimeMillis())
                .build();
    }
    
    /**
     * Gets the current time, using the {@link #ticker}. Normally this is equivalent of {@link System#currentTimeMillis()},
     * but in test environments a custom {@link Ticker} can be injected via {@link #setTicker(Ticker)} to allow testing of
     * specific timing scenarios.
     */
    protected long currentTimeMillis() {
        return ticker.read();
    }
}
