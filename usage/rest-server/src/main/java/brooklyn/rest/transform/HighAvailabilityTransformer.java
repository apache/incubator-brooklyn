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
        return new HaNodeSummary(memento.getNodeId(), memento.getUri(), status, memento.getTimestampUtc());
    }
}
