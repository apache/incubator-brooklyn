package brooklyn.entity.rebind.plane.dto;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import brooklyn.management.ha.ManagementPlaneMemento;
import brooklyn.management.ha.ManagerMemento;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ManagementPlaneMementoImpl implements ManagementPlaneMemento, Serializable {

    private static final long serialVersionUID = -4207907303446336973L;

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected String masterNodeId;
        protected final Set<ManagerMemento> nodes = Sets.newLinkedHashSet();
        
        public Builder masterNodeId(String val) {
            masterNodeId = val; return this;
        }
        public Builder nodes(Iterable<ManagerMemento> vals) {
            checkState(!Iterables.contains(checkNotNull(vals, "nodes must not be null"), null),  "nodes must not contain null: %s", vals);
            Iterables.addAll(nodes, vals);
            return this;
        }
        public Builder node(ManagerMemento val) {
            nodes.add(checkNotNull(val, "node must not be null")); return this;
        }
        public ManagementPlaneMemento build() {
            return new ManagementPlaneMementoImpl(this);
        }
    }

    private String masterNodeId;
    private Map<String, ManagerMemento> nodes;
    
    private ManagementPlaneMementoImpl(Builder builder) {
        masterNodeId = builder.masterNodeId;
        nodes = Maps.newLinkedHashMap();
        for (ManagerMemento node : builder.nodes) {
            checkState(!nodes.containsKey(node.getNodeId()), "duplicate nodeId %s", node.getNodeId());
            nodes.put(node.getNodeId(), node);
        }
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
        return Objects.toStringHelper(this)
                .add("masterNodeId", masterNodeId)
                .add("nodes", nodes.keySet())
                .toString();
    }

    @Override
    public String toVerboseString() {
        return toString();
    }
}
