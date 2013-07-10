package brooklyn.rest.domain;

import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;

import java.net.URI;
import java.util.Map;

public class EntitySummary {

  private final String id;
  private final String name;
  private final String type;
  private final Map<String, URI> links;

  public EntitySummary(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.type = type;
    this.id = id;
    this.name = name;
    this.links = ImmutableMap.copyOf(links);
  }

  public String getType() {
    return type;
  }

  public String getId() {
    return id;
  }
  
  public String getName() {
    return name;
  }
  
  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof EntitySummary) && id.equals(((EntitySummary)o).getId());
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "EntitySummary{" +
        "id='" + id + '\'' +
        ", links=" + links +
        '}';
  }
}
