package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.collect.ImmutableMap;

public class HighAvailabilitySummary {

    public static class HaNodeSummary {
        private final String nodeId;
        private final URI nodeUri;
        private final String status;
        private final Long timestampUtc;
        
        public HaNodeSummary(
                @JsonProperty("nodeId") String nodeId,
                @JsonProperty("nodeUri") URI nodeUri,
                @JsonProperty("status") String status,
                @JsonProperty("timestampUtc") Long timestampUtc
            ) {
              this.nodeId = nodeId;
              this.nodeUri = nodeUri;
              this.status = status;
              this.timestampUtc = timestampUtc;
            }

            public String getNodeId() {
              return nodeId;
            }

            public URI getNodeUri() {
              return nodeUri;
            }

            public String getStatus() {
              return status;
            }
            
            public Long getTimestampUtc() {
              return timestampUtc;
            }
            
            @Override
            public boolean equals(Object o) {
              return (o instanceof HaNodeSummary) && nodeId.equals(((HaNodeSummary)o).getNodeId());
            }

            @Override
            public int hashCode() {
              return nodeId != null ? nodeId.hashCode() : 0;
            }

            @Override
            public String toString() {
              return "HighAvailabilitySummary{" +
                  "nodeId='" + nodeId + '\'' +
                  ", status='" + status + '\'' +
                  '}';
            }
    }
    
    private final String ownId;
    private final String masterId;
    private final Map<String, HaNodeSummary> nodes;
    private final Map<String, URI> links;

    public HighAvailabilitySummary(
        @JsonProperty("ownId") String ownId,
        @JsonProperty("masterId") String masterId,
        @JsonProperty("nodes") Map<String, HaNodeSummary> nodes,
        @JsonProperty("links") Map<String, URI> links
    ) {
      this.ownId = ownId;
      this.masterId = masterId;
      this.nodes = nodes;
      this.links = links == null ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
    }

    public String getOwnId() {
      return ownId;
    }

    public String getMasterId() {
      return masterId;
    }
    
    public Map<String, HaNodeSummary> getNodes() {
      return nodes;
    }
    
    public Map<String, URI> getLinks() {
      return links;
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof HighAvailabilitySummary) && ownId.equals(((HighAvailabilitySummary)o).getOwnId());
    }

    @Override
    public int hashCode() {
      return ownId != null ? ownId.hashCode() : 0;
    }

    @Override
    public String toString() {
      return "HighAvailabilitySummary{" +
          "ownId='" + ownId + '\'' +
          ", links=" + links +
          '}';
    }
}
