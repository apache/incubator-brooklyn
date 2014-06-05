package brooklyn.management.ha;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.persister.MementoSerializer;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessor;
import brooklyn.entity.rebind.persister.RetryingMementoSerializer;
import brooklyn.entity.rebind.persister.XmlMementoSerializer;
import brooklyn.entity.rebind.plane.dto.ManagementPlaneSyncRecordImpl;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;

/**
 * Structure of files is:
 * <ul>
 *   <li>{@code plane/} - top-level directory
 *     <ul>
 *       <li>{@code master} - contains the id of the management-node that is currently master
 *       <li>{@code change.log} - log of changes made
 *       <li>{@code nodes/} - sub-directory, containing one file per management-node
 *         <ul>
 *           <li>{@code a9WiuVKp} - file named after the management-node's id, containing the management node's current state
 *           <li>{@code E1eDXQF3}
 *         </ul>
 *     </ul>
 * </ul>
 * 
 * All writes are done synchronously.
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public class ManagementPlaneSyncRecordPersisterToObjectStore implements ManagementPlaneSyncRecordPersister {

    // TODO Multiple node appending to change.log could cause strange interleaving, or perhaps even data loss?
    // But this file is not critical to functionality.

    // TODO Should ManagementPlaneSyncRecordPersister.Delta be different so can tell what is a significant event,
    // and thus log it in change.log - currently only subset of significant things being logged.

    private static final Logger LOG = LoggerFactory.getLogger(ManagementPlaneSyncRecordPersisterToObjectStore.class);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.TEN_SECONDS;
    public static final String NODES_SUB_PATH = "nodes";

    // TODO Leak if we go through lots of managers; but tiny!
    private final ConcurrentMap<String, StoreObjectAccessor> nodeWriters = Maps.newConcurrentMap();

    private final StoreObjectAccessor masterWriter;
    private final StoreObjectAccessor changeLogWriter;

    private final MementoSerializer<Object> serializer;

    private final PersistenceObjectStore objectStore;

    private static final int MAX_SERIALIZATION_ATTEMPTS = 5;

    private volatile boolean running = true;

    /**
     * @param objectStore the objectStore use to read/write management-plane data
     * @param classLoader ClassLoader to use when deserializing data
     */
    public ManagementPlaneSyncRecordPersisterToObjectStore(PersistenceObjectStore objectStore, ClassLoader classLoader) {
        this.objectStore = checkNotNull(objectStore, "objectStore");

        MementoSerializer<Object> rawSerializer = new XmlMementoSerializer<Object>(checkNotNull(classLoader, "classLoader"));
        this.serializer = new RetryingMementoSerializer<Object>(rawSerializer, MAX_SERIALIZATION_ATTEMPTS);

        objectStore.createSubPath(NODES_SUB_PATH);

        masterWriter = objectStore.newAccessor("/master");
        changeLogWriter = objectStore.newAccessor("/change.log");

        LOG.info("ManagementPlaneMemento-persister will use store "+objectStore);
    }

    @Override
    public void stop() {
        running = false;
        try {
            for (StoreObjectAccessor writer : nodeWriters.values()) {
                try {
                    writer.waitForWriteCompleted(SHUTDOWN_TIMEOUT);
                } catch (TimeoutException e) {
                    LOG.warn("Timeout during shutdown, waiting for write of "+writer+"; continuing");
                }
            }
            try {
                masterWriter.waitForWriteCompleted(SHUTDOWN_TIMEOUT);
            } catch (TimeoutException e) {
                LOG.warn("Timeout during shutdown, waiting for write of "+masterWriter+"; continuing");
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public ManagementPlaneSyncRecord loadSyncRecord() throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento from "+ objectStore.getSummaryName());
        }
        
        // Note this is called a lot - every time we check the heartbeats
        if (LOG.isTraceEnabled()) LOG.trace("Loading management-plane memento from {}", objectStore.getSummaryName());

        Stopwatch stopwatch = Stopwatch.createStarted();

        ManagementPlaneSyncRecordImpl.Builder builder = ManagementPlaneSyncRecordImpl.builder();

        // Be careful about order: if the master-file says nodeX then nodeX's file must have an up-to-date timestamp.
        // Therefore read master file first, followed by the other node-files.
        String masterNodeId = masterWriter.exists() ? masterWriter.read() : null;
        if (masterNodeId == null) {
            LOG.warn("No entity-memento deserialized from file "+masterWriter+"; ignoring and continuing");
        } else {
            builder.masterNodeId(masterNodeId);
        }

        // Load node-files
        List<String> nodeContents = objectStore.listContentsWithSubPath(NODES_SUB_PATH);
        LOG.info("Loading nodes from {}; {} nodes.",
                new Object[]{objectStore.getSummaryName(), nodeContents.size()});

        for (String nodeContent : nodeContents) {
            PersistenceObjectStore.StoreObjectAccessor objectAccessor = objectStore.newAccessor(nodeContent);
            ManagementNodeSyncRecord memento = (ManagementNodeSyncRecord) serializer.fromString(objectAccessor.read());
            if (memento == null) {
                LOG.warn("No manager-memento deserialized from " + nodeContent + " (possibly just stopped?); ignoring" +
                        " and continuing");
            } else {
                builder.node(memento);
            }
        }

        if (LOG.isDebugEnabled()) LOG.debug("Loaded management-plane memento; took {}", Time.makeTimeStringRounded(stopwatch.elapsed(TimeUnit.MILLISECONDS)));
        return builder.build();
    }
    
    @Override
    public void delta(Delta delta) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Persister not running; ignoring checkpointed delta of manager-memento");
            return;
        }
        if (LOG.isDebugEnabled()) LOG.debug("Checkpointed delta of manager-memento; updating {}", delta);
        
        for (ManagementNodeSyncRecord m : delta.getNodes()) {
            persist(m);
        }
        for (String id : delta.getRemovedNodeIds()) {
            deleteNode(id);
        }
        switch (delta.getMasterChange()) {
        case NO_CHANGE:
            break; // no-op
        case SET_MASTER:
            persistMaster(checkNotNull(delta.getNewMasterOrNull()));
            break;
        case CLEAR_MASTER:
            persistMaster(null);
            break; // no-op
        default:
            throw new IllegalStateException("Unknown state for master-change: "+delta.getMasterChange());
        }
    }

    private void persistMaster(String nodeId) {
        masterWriter.writeAsync(nodeId);
        try {
            masterWriter.waitForWriteCompleted(Duration.of(200, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        changeLogWriter.append(Time.makeDateString() + ": set master to " + nodeId + "\n");
        try {
            changeLogWriter.waitForWriteCompleted(Duration.of(200, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    @Override
    @VisibleForTesting
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        for (StoreObjectAccessor writer : nodeWriters.values()) {
            writer.waitForWriteCompleted(timeout);
        }
        masterWriter.waitForWriteCompleted(timeout);
    }

    private void persist(ManagementNodeSyncRecord node) {
        StoreObjectAccessor writer = getOrCreateNodeWriter(node.getNodeId());
        boolean fileExists = writer.exists();
        writer.writeAsync(serializer.toString(node));
        try {
            writer.waitForWriteCompleted(Duration.of(200, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
        if (!fileExists) {
            changeLogWriter.append(Time.makeDateString()+": created node "+node.getNodeId()+"\n");
        }
        if (node.getStatus() == ManagementNodeState.TERMINATED || node.getStatus() == ManagementNodeState.FAILED) {
            changeLogWriter.append(Time.makeDateString()+": set node "+node.getNodeId()+" status to "+node.getStatus()+"\n");
        }
    }
    
    private void deleteNode(String nodeId) {
        getOrCreateNodeWriter(nodeId).deleteAsync();
        changeLogWriter.append(Time.makeDateString()+": deleted node "+nodeId+"\n");
    }

    private StoreObjectAccessor getOrCreateNodeWriter(String nodeId) {
        PersistenceObjectStore.StoreObjectAccessor writer = nodeWriters.get(nodeId);
        if (writer == null) {
            nodeWriters.putIfAbsent(nodeId, objectStore.newAccessor(NODES_SUB_PATH+"/"+nodeId));
            writer = nodeWriters.get(nodeId);
        }
        return writer;
    }
    
}
