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
import brooklyn.entity.rebind.plane.dto.BasicManagerMemento;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneMementoImpl;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneMementoImpl.Builder;
import brooklyn.management.Task;
import brooklyn.management.ha.BasicMasterChooser.AlphabeticMasterChooser;
import brooklyn.management.ha.ManagementPlaneMementoPersister.Delta;
import brooklyn.management.ha.ManagerMemento.HealthStatus;
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
 * The management nodes communicate their health/status via the {@link ManagementPlaneMementoPersister}.
 * For example, if using {@link ManagementPlaneMementoPersisterToMultiFile} with a shared NFS mount, 
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
    private volatile ManagementPlaneMementoPersister persister;
    private volatile PromotionListener promotionListener;
    private volatile MasterChooser masterChooser = new AlphabeticMasterChooser();
    private volatile Duration pollPeriod = Duration.of(5, TimeUnit.SECONDS);
    private volatile Duration heartbeatTimeout = Duration.THIRTY_SECONDS;
    private volatile Ticker ticker = Ticker.systemTicker();
    
    private volatile Task<?> pollingTask;
    private volatile boolean disabled;
    private volatile boolean running;
    private volatile NodeStatus nodeStatus = NodeStatus.UNINITIALISED;

    public HighAvailabilityManagerImpl(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
    }

    @Override
    public HighAvailabilityManagerImpl setPersister(ManagementPlaneMementoPersister persister) {
        this.persister = checkNotNull(persister, "persister");
        return this;
    }
    
    @Override
    public ManagementPlaneMementoPersister getPersister() {
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
        nodeStatus = NodeStatus.MASTER;
    }

    @Override
    public void start(HighAvailabilityMode startMode) {
        ownNodeId = managementContext.getManagementNodeId();
        nodeStatus = NodeStatus.STANDBY;
        running = true;
        
        // TODO Small race in that we first check, and then we'll do checkMater() on first poll,
        // so another node could have already become master or terminated in that window.
        ManagerMemento existingMaster = hasHealthyMaster();
        
        switch (startMode) {
        case AUTO:
            // don't care; let's start and see if we promote ourselves
            doPollTask();
            if (nodeStatus == NodeStatus.STANDBY) {
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
                LOG.info("Management node (with high availability mode 'standby') started; steatus "+nodeStatus);
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
        boolean wasRunning = (running); // ensure idempotent
        terminate();
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
    public void terminate() {
        running = false;
        nodeStatus = NodeStatus.TERMINATED;
        if (pollingTask != null) pollingTask.cancel(true);
    }
    
    @Override
    public NodeStatus getNodeStatus() {
        return nodeStatus;
    }

    @Override
    public ManagementPlaneMemento getManagementPlaneStatus() {
        return loadMemento();
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
        
        ManagerMemento memento = BasicManagerMemento.builder()
                .nodeId(managementContext.getManagementNodeId())
                .status(toHealthStatus(getNodeStatus()))
                .timestampUtc(currentTimeMillis())
                .build();

        Delta delta = ManagementPlaneMementoDeltaImpl.builder().node(memento).build();
        persister.delta(delta);
        if (LOG.isTraceEnabled()) LOG.trace("Published management-node health: {}", memento);
    }
    
    /**
     * Publishes (via {@link #persister}) the state of this management node with itself set to master.
     */
    protected synchronized void publishPromotionToMaster() {
        checkState(getNodeStatus() == NodeStatus.MASTER, "node status must be master on publish, but is %s", getNodeStatus());
        
        if (persister == null) {
            LOG.info("Cannot publish management-node health as no persister");
            return;
        }
        
        ManagerMemento memento = BasicManagerMemento.builder()
                .nodeId(managementContext.getManagementNodeId())
                .status(toHealthStatus(getNodeStatus()))
                .timestampUtc(currentTimeMillis())
                .build();

        Delta delta = ManagementPlaneMementoDeltaImpl.builder()
                .node(memento)
                .setMaster(ownNodeId)
                .build();
        persister.delta(delta);
        if (LOG.isTraceEnabled()) LOG.trace("Published management-node health: {}", memento);
    }
    
    protected HealthStatus toHealthStatus(NodeStatus nodeStatus) {
        switch (nodeStatus) {
            case MASTER:        return HealthStatus.MASTER;
            case STANDBY:       return HealthStatus.STANDBY;
            case TERMINATED:    return HealthStatus.TERMINATED;
            case UNINITIALISED: return null;
            default:            throw new IllegalStateException("Unexpected health status for management context "+nodeStatus);
        }
    }
    
    protected boolean isHeartbeatOk(ManagerMemento memento, long now) {
        long timestamp = memento.getTimestampUtc();
        return (now - timestamp) <= heartbeatTimeout.toMilliseconds();
    }
    
    protected ManagerMemento hasHealthyMaster() {
        long now = currentTimeMillis();
        ManagementPlaneMemento memento = loadMemento();
        
        String nodeId = memento.getMasterNodeId();
        ManagerMemento nodeMemento = (nodeId == null) ? null : memento.getNodes().get(nodeId);
        
        boolean result = nodeMemento != null && nodeMemento.getStatus() == HealthStatus.MASTER
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
        ManagementPlaneMemento memento = loadMemento();
        
        String masterNodeId = memento.getMasterNodeId();
        ManagerMemento masterNodeMemento = memento.getNodes().get(masterNodeId);
        ManagerMemento ownNodeMemento = memento.getNodes().get(ownNodeId);
        
        if (masterNodeMemento != null && masterNodeMemento.getStatus() == HealthStatus.MASTER && isHeartbeatOk(masterNodeMemento, now)) {
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
            LOG.error("This management node ("+ownNodeId+") supposed to be master but reportedly unhealthy? "
                    + "no-op as expect other node to fix: self="+ownNodeMemento.toVerboseString());
            return;
        }
        
        // Need to choose a new master
        String newMasterNodeId = masterChooser.choose(memento, heartbeatTimeout, ownNodeId, now).getNodeId();
        boolean newMasterIsSelf = ownNodeId.equals(newMasterNodeId);
        
        LOG.warn("Management node master-promotion required: newMaster={}; oldMaster={}; self={}; heartbeatTimeout={}", 
                new Object[] {
                        (newMasterNodeId == null ? "<none>" : newMasterNodeId),
                        (masterNodeMemento == null ? masterNodeId+" (no memento)": masterNodeMemento.toVerboseString()), 
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
        try {
            nodeStatus = NodeStatus.MASTER;
            publishPromotionToMaster();
            managementContext.getRebindManager().rebind();
            managementContext.getRebindManager().start();
        } catch (IOException e) {
            LOG.error("Error during rebind when promoting node to master", e);
        }
    }

    protected ManagementPlaneMemento loadMemento() {
        if (disabled) {
            // if HA is disabled, then we are the only node - no persistence; just load a memento to describe this node
            HealthStatus healthStatus = toHealthStatus(getNodeStatus());
            Builder builder = ManagementPlaneMementoImpl.builder()
                    .node(BasicManagerMemento.builder()
                            .brooklynVersion(BrooklynVersion.get())
                            .nodeId(ownNodeId)
                            .status(healthStatus)
                            .timestampUtc(currentTimeMillis())
                            .build());
            if (healthStatus == HealthStatus.MASTER) {
                builder.masterNodeId(ownNodeId);
            }
            return builder.build();
        }
        
        int maxLoadAttempts = 5;
        Exception lastException = null;
        for (int i = 0; i < maxLoadAttempts; i++) {
            try {
                return persister.loadMemento();
            } catch (IOException e) {
                if (i < (maxLoadAttempts - 1)) {
                    if (LOG.isDebugEnabled()) LOG.debug("Problem loading mangement-plane memento attempt "+(i+1)+"/"+maxLoadAttempts+"; retrying", e);
                }
                lastException = e;
            }
        }
        throw new IllegalStateException("Failed to load mangement-plane memento "+maxLoadAttempts+" consecutive times", lastException);
    }
    
    /**
     * Gets the current time, using the {@link #ticker}. Normally this is equivalent of {@link System#currentTimeMillis()},
     * but in test environments a custom {@link Ticker} can be injected via {@link #setTicker(Ticker)} to allow testing of
     * specific timing scenarios.
     */
    protected long currentTimeMillis() {
        return TimeUnit.NANOSECONDS.toMillis(ticker.read());
    }
}
