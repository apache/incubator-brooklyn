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
