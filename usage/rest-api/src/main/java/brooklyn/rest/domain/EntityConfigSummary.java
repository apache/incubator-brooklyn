package brooklyn.rest.domain;

import brooklyn.config.ConfigKey;
import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class EntityConfigSummary extends ConfigSummary {

  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Map<String, URI> links;

  public EntityConfigSummary(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("description") String description,
      @JsonProperty("defaultValue") Object defaultValue,
      @JsonProperty("reconfigurable") boolean reconfigurable,
      @JsonProperty("label") String label,
      @JsonProperty("priority") Double priority,
      @JsonProperty("possibleValues") List<Map<String, String>> possibleValues,
      @JsonProperty("links") Map<String, URI> links
  ) {
    super(name, type, description, defaultValue, reconfigurable, label, priority, possibleValues);
    this.links = links!=null ? ImmutableMap.copyOf(links) : null;
  }

  public EntityConfigSummary(ConfigKey<?> config, String label, Double priority, Map<String, URI> links) {
    super(config, label, priority);
    this.links = links!=null ? ImmutableMap.copyOf(links) : null;
  }

  @Override
  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public String toString() {
    return "EntityConfigSummary{" +
        "name='" + getName() + '\'' +
        ", type='" + getType() + '\'' +
        '}';
  }

}
