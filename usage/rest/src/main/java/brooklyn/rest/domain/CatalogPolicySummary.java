package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonProperty;

import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.rest.util.BrooklynRestResourceUtils;

import com.google.common.collect.ImmutableSet;

public class CatalogPolicySummary extends CatalogItemSummary {

    private final Set<ConfigSummary> config;

    public CatalogPolicySummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("config") Set<ConfigSummary> config,
            @JsonProperty("links") Map<String, URI> links
        ) {
        super(id, name, type, description, iconUrl, links);
        // TODO expose config from policies
        this.config = config;
    }

    public static CatalogPolicySummary from(BrooklynRestResourceUtils b, CatalogItem<? extends Entity> item) {
        Set<ConfigSummary> config = ImmutableSet.of();
        return new CatalogPolicySummary(item.getId(), item.getName(), item.getJavaType(), 
                item.getDescription(), item.getIconUrl(), config, 
                makeLinks(item));
    }

    public Set<ConfigSummary> getConfig() {
        return config;
    }

    @Override
    public String toString() {
        return super.toString()+"["+
                "config="+getConfig()+"]";
    }
}
