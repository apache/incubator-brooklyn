package brooklyn.entity.rebind.plane.dto;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.Map;

import brooklyn.management.ha.ManagementNodeSyncRecord;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class ManagementPlaneSyncRecordImpl implements ManagementPlaneSyncRecord, Serializable {

    private static final long serialVersionUID = -4207907303446336973L;

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String masterNodeId;
        protected final Map<String,ManagementNodeSyncRecord> nodes = MutableMap.of();
        
        public Builder masterNodeId(String val) {
            masterNodeId = val; return this;
        }
        public Builder nodes(Iterable<ManagementNodeSyncRecord> vals) {
            checkState(!Iterables.contains(checkNotNull(vals, "nodes must not be null"), null),  "nodes must not contain null: %s", vals);
            for (ManagementNodeSyncRecord val: vals) nodes.put(val.getNodeId(), val);
            return this;
        }
        public Builder node(ManagementNodeSyncRecord val) {
            checkNotNull(val, "node must not be null"); 
            nodes.put(val.getNodeId(), val);
            return this;
        }
        public ManagementPlaneSyncRecord build() {
            return new ManagementPlaneSyncRecordImpl(this);
        }
    }

    private String masterNodeId;
    private Map<String, ManagementNodeSyncRecord> managementNodes;
    
    private ManagementPlaneSyncRecordImpl(Builder builder) {
        masterNodeId = builder.masterNodeId;
        managementNodes = Maps.newLinkedHashMap();
        for (ManagementNodeSyncRecord node : builder.nodes.values()) {
            checkState(!managementNodes.containsKey(node.getNodeId()), "duplicate nodeId %s", node.getNodeId());
            managementNodes.put(node.getNodeId(), node);
        }
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
        return Objects.toStringHelper(this)
                .add("masterNodeId", masterNodeId)
                .add("nodes", managementNodes.keySet())
                .toString();
    }

    @Override
    public String toVerboseString() {
        return toString();
    }
}
