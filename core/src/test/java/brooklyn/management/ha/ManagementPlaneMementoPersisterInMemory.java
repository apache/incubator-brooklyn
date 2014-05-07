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

public class ManagementPlaneMementoPersisterInMemory implements ManagementPlaneMementoPersister {

    private static final Logger LOG = LoggerFactory.getLogger(ManagementPlaneMementoPersisterInMemory.class);

    private final MutableManagementPlaneMemento memento = new MutableManagementPlaneMemento();
    
    private volatile boolean running = true;
    
    @Override
    public synchronized void stop() {
        running = false;
    }

    @Override
    public synchronized ManagementPlaneMemento loadMemento() throws IOException {
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
        
        for (ManagerMemento m : delta.getNodes()) {
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

    public static class MutableManagementPlaneMemento implements ManagementPlaneMemento {
        private String masterNodeId;
        private Map<String, ManagerMemento> nodes = Maps.newConcurrentMap();

        @Override
        public String getMasterNodeId() {
            return masterNodeId;
        }

        @Override
        public Map<String, ManagerMemento> getNodes() {
            return nodes;
        }

        @Override
        public String toVerboseString() {
            return toString();
        }

        public ImmutableManagementPlaneMemento snapshot() {
            return new ImmutableManagementPlaneMemento(masterNodeId, nodes);
        }
        
        public void setMasterNodeId(String masterNodeId) {
            this.masterNodeId = masterNodeId;
        }
        
        public void addNode(ManagerMemento memento) {
            nodes.put(memento.getNodeId(), memento);
        }
        
        public void deleteNode(String nodeId) {
            nodes.remove(nodeId);
        }
    }
    
    public static class ImmutableManagementPlaneMemento implements ManagementPlaneMemento {
        private final String masterNodeId;
        private final Map<String, ManagerMemento> nodes;

        ImmutableManagementPlaneMemento(String masterNodeId, Map<String, ManagerMemento> nodes) {
            this.masterNodeId = masterNodeId;
            this.nodes = ImmutableMap.copyOf(nodes);
        }
        
        @Override
        public String getMasterNodeId() {
            return masterNodeId;
        }

        @Override
        public Map<String, ManagerMemento> getNodes() {
            return nodes;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("master", masterNodeId).add("nodes", nodes.keySet()).toString();
        }
        
        @Override
        public String toVerboseString() {
            return Objects.toStringHelper(this).add("master", masterNodeId).add("nodes", nodes).toString();
        }
    }
}
