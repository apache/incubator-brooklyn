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
package org.apache.brooklyn.core.mgmt.ha;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ha.ManagementNodeSyncRecord;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecord;

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