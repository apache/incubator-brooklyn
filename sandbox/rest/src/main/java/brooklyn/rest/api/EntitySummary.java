package brooklyn.rest.api;

import brooklyn.entity.Entity;
import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonProperty;

public class EntitySummary {

  private final String type;
  private final Map<String, URI> links;

  public EntitySummary(
      @JsonProperty("type") String type,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.type = type;
    this.links = ImmutableMap.copyOf(links);
  }

  public EntitySummary(Application application, Entity entity) {
    this.type = entity.getClass().getName();

    String applicationUri = "/v1/applications/" + application.getSpec().getName();
    String entityUri = applicationUri + "/entities/" + entity.getId();
    this.links = ImmutableMap.<String, URI>builder()
        .put("self", URI.create(entityUri))
        .put("catalog", URI.create("/v1/catalog/entities/" + type))
        .put("application", URI.create(applicationUri))
        .put("children", URI.create(entityUri + "/entities"))
        .put("effectors", URI.create(entityUri + "/effectors"))
        .put("sensors", URI.create(entityUri + "/sensors"))
        .build();
  }

  public String getType() {
    return type;
  }

  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EntitySummary that = (EntitySummary) o;

    if (links != null ? !links.equals(that.links) : that.links != null)
      return false;
    if (type != null ? !type.equals(that.type) : that.type != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + (links != null ? links.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "EntitySummary{" +
        "type='" + type + '\'' +
        ", links=" + links +
        '}';
  }
}
