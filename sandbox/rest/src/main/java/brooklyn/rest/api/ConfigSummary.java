package brooklyn.rest.api;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.EntityLocal;

import com.google.common.collect.ImmutableMap;

public class ConfigSummary {

  private final String name;
  private final String type;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Object defaultValue;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String description;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Map<String, URI> links;

  public ConfigSummary(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("description") String description,
      @JsonProperty("defaultValue") Object defaultValue,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.name = name;
    this.type = type;
    this.description = description;
    this.defaultValue = defaultValue;
    this.links = links!=null ? ImmutableMap.copyOf(links) : null;
  }

  public ConfigSummary(Application application, EntityLocal entity, ConfigKey<?> config) {
    this.name = config.getName();
    this.type = config.getTypeName();
    this.description = config.getDescription();
    this.defaultValue = config.getDefaultValue();

    String applicationUri = "/v1/applications/" + application.getSpec().getName();
    String entityUri = applicationUri + "/entities/" + entity.getId();

    this.links = ImmutableMap.<String, URI>builder()
        .put("self", URI.create(entityUri + "/config/" + config.getName()))
        .put("application", URI.create(applicationUri))
        .put("entity", URI.create(entityUri))
        .build();
  }
  
  public static ConfigSummary forCatalog(ConfigKey<?> config) {
      return new ConfigSummary(config.getName(), config.getTypeName(), 
              config.getDescription(), config.getDefaultValue(), null);
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, URI> getLinks() {
    return links;
  }

  public Object getDefaultValue() {
    // TODO toString ?
    return defaultValue;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ConfigSummary that = (ConfigSummary) o;

    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    return result;
  }

  @Override
  public String toString() {
    return "ConfigSummary{" +
        "name='" + name + '\'' +
        ", type='" + type + '\'' +
        '}';
  }
}
