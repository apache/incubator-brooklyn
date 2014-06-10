package brooklyn.management.ha;

import java.util.Map;

import com.google.common.collect.Maps;

public class MutableManagementPlaneSyncRecord implements ManagementPlaneSyncRecord {
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