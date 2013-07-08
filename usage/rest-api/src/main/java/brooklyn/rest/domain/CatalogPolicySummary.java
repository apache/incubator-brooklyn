package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;

public class CatalogPolicySummary extends CatalogItemSummary {

    private final Set<PolicyConfigSummary> config;

    public CatalogPolicySummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("config") Set<PolicyConfigSummary> config,
            @JsonProperty("links") Map<String, URI> links
        ) {
        super(id, name, type, description, iconUrl, links);
        // TODO expose config from policies
        this.config = config;
    }
    
    public Set<PolicyConfigSummary> getConfig() {
        return config;
    }

    @Override
    public String toString() {
        return super.toString()+"["+
                "config="+getConfig()+"]";
    }
}
