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
package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;

import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.ha.ManagementNodeSyncRecord;
import brooklyn.rest.domain.HighAvailabilitySummary;
import brooklyn.rest.domain.HighAvailabilitySummary.HaNodeSummary;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class HighAvailabilityTransformer {

    public static HighAvailabilitySummary highAvailabilitySummary(String ownNodeId, ManagementPlaneSyncRecord memento) {
        Map<String, HaNodeSummary> nodes = Maps.newLinkedHashMap();
        for (Map.Entry<String, ManagementNodeSyncRecord> entry : memento.getManagementNodes().entrySet()) {
            nodes.put(entry.getKey(), haNodeSummary(entry.getValue()));
        }
        
        // TODO What links?
        ImmutableMap.Builder<String, URI> lb = ImmutableMap.<String, URI>builder();

        return new HighAvailabilitySummary(ownNodeId, memento.getMasterNodeId(), nodes, lb.build());
    }

    public static HaNodeSummary haNodeSummary(ManagementNodeSyncRecord memento) {
        String status = memento.getStatus() == null ? null : memento.getStatus().toString();
        return new HaNodeSummary(memento.getNodeId(), memento.getUri(), status, memento.getLocalTimestamp(), memento.getRemoteTimestamp());
    }
}
