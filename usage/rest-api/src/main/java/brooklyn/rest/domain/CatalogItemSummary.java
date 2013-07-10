package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

/** variant of Catalog*ItemDto objects for JS/JSON serialization;
 * see also, subclasses */
public class CatalogItemSummary {

    private final String id;
    private final String type;
    private final String name;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String description;
    @JsonSerialize(include=Inclusion.NON_EMPTY)
    private final String iconUrl;
    
    private final Map<String, URI> links;

    public CatalogItemSummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("iconUrl") String iconUrl,
            @JsonProperty("links") Map<String, URI> links
        ) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.iconUrl = iconUrl;
        this.links = ImmutableMap.copyOf(links);
    }
    
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public Map<String, URI> getLinks() {
        return links;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("id", id).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, name, type);
    }
    
    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }
    
}
