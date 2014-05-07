package brooklyn.management.ha;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class ManagementPlaneSyncRecordPersisterInMemory implements ManagementPlaneSyncRecordPersister {

    private static final Logger LOG = LoggerFactory.getLogger(ManagementPlaneSyncRecordPersisterInMemory.class);

    private final MutableManagementPlaneSyncRecord memento = new MutableManagementPlaneSyncRecord();
    
    private volatile boolean running = true;
    
    @Override
    public synchronized void stop() {
        running = false;
    }

    @Override
    public ManagementPlaneSyncRecord loadSyncRecord() throws IOException {
        if (!running) {
            throw new IllegalStateException("Persister not running; cannot load memento");
        }
        
        return memento.snapshot();
    }
    
    @VisibleForTesting
    @Override
    public synchronized void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        // The synchronized is sufficient - guarantee that no concurrent calls
        return;
    }

    @Override
    public synchronized void delta(Delta delta) {
        if (!running) {
            if (LOG.isDebugEnabled()) LOG.debug("Persister not running; ignoring checkpointed delta of manager-memento");
            return;
        }
        
        for (ManagementNodeSyncRecord m : delta.getNodes()) {
            memento.addNode(m);
        }
        for (String id : delta.getRemovedNodeIds()) {
            memento.deleteNode(id);
        }
        switch (delta.getMasterChange()) {
        case NO_CHANGE:
            break; // no-op
        case SET_MASTER:
            memento.setMasterNodeId(checkNotNull(delta.getNewMasterOrNull()));
            break;
        case CLEAR_MASTER:
            memento.setMasterNodeId(null);
            break; // no-op
        default:
            throw new IllegalStateException("Unknown state for master-change: "+delta.getMasterChange());
        }
    }

    public static class MutableManagementPlaneSyncRecord implements ManagementPlaneSyncRecord {
        private String masterNodeId;
        private Map<String, ManagementNodeSyncRecord> managementNodes = Maps.newConcurrentMap();

        @Override
        public String getMasterNodeId() {
            return masterNodeId;
        }

        @Override
        public Map<String, ManagementNodeSyncRecord> getManagementNodes() {
            return managementNodes;
        }

        @Override
        public String toVerboseString() {
            return toString();
        }

        public ImmutableManagementPlaneSyncRecord snapshot() {
            return new ImmutableManagementPlaneSyncRecord(masterNodeId, managementNodes);
        }
        
        public void setMasterNodeId(String masterNodeId) {
            this.masterNodeId = masterNodeId;
        }
        
        public void addNode(ManagementNodeSyncRecord memento) {
            managementNodes.put(memento.getNodeId(), memento);
        }
        
        public void deleteNode(String nodeId) {
            managementNodes.remove(nodeId);
        }
    }
    
    public static class ImmutableManagementPlaneSyncRecord implements ManagementPlaneSyncRecord {
        private final String masterNodeId;
        private final Map<String, ManagementNodeSyncRecord> managementNodes;

        ImmutableManagementPlaneSyncRecord(String masterNodeId, Map<String, ManagementNodeSyncRecord> nodes) {
            this.masterNodeId = masterNodeId;
            this.managementNodes = ImmutableMap.copyOf(nodes);
        }
        
        @Override
        public String getMasterNodeId() {
            return masterNodeId;
        }

        @Override
        public Map<String, ManagementNodeSyncRecord> getManagementNodes() {
            return managementNodes;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("master", masterNodeId).add("nodes", managementNodes.keySet()).toString();
        }
        
        @Override
        public String toVerboseString() {
            return Objects.toStringHelper(this).add("master", masterNodeId).add("nodes", managementNodes).toString();
        }
    }
}
