package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;

import brooklyn.management.ha.ManagementPlaneMemento;
import brooklyn.management.ha.ManagerMemento;
import brooklyn.rest.domain.HighAvailabilitySummary;
import brooklyn.rest.domain.HighAvailabilitySummary.HaNodeSummary;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class HighAvailabilityTransformer {

    public static HighAvailabilitySummary highAvailabilitySummary(String ownNodeId, ManagementPlaneMemento memento) {
        Map<String, HaNodeSummary> nodes = Maps.newLinkedHashMap();
        for (Map.Entry<String, ManagerMemento> entry : memento.getNodes().entrySet()) {
            nodes.put(entry.getKey(), haNodeSummary(entry.getValue()));
        }
        
        // TODO What links?
        ImmutableMap.Builder<String, URI> lb = ImmutableMap.<String, URI>builder();

        return new HighAvailabilitySummary(ownNodeId, memento.getMasterNodeId(), nodes, lb.build());
    }

    public static HaNodeSummary haNodeSummary(ManagerMemento memento) {
        String status = memento.getStatus() == null ? null : memento.getStatus().toString();
        return new HaNodeSummary(memento.getNodeId(), status, memento.getTimestampUtc());
    }
}
