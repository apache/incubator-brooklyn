package brooklyn.management.ha;

import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

public class ImmutableManagementPlaneSyncRecord implements ManagementPlaneSyncRecord {
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
