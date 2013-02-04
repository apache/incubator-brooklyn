package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.config.ConfigKey;

public abstract class ConfigSummary {

  private final String name;
  private final String type;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Object defaultValue;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String description;
  @JsonSerialize
  private final boolean reconfigurable;

  protected ConfigSummary(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("description") String description,
      @JsonProperty("defaultValue") Object defaultValue,
      @JsonProperty("reconfigurable") boolean reconfigurable,
      @JsonProperty("links") Map<String, URI> links
  ) {
    this.name = name;
    this.type = type;
    this.description = description;
    this.defaultValue = defaultValue;
    this.reconfigurable = reconfigurable;
  }

  protected ConfigSummary(ConfigKey<?> config) {
    this.name = config.getName();
    this.type = config.getTypeName();
    this.description = config.getDescription();
    this.defaultValue = config.getDefaultValue();
    this.reconfigurable = config.isReconfigurable();
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

  public boolean isReconfigurable() {
    return reconfigurable;
  }
  
  public Object getDefaultValue() {
      // TODO toString ?
      return defaultValue;
    }
    
  public abstract Map<String, URI> getLinks();

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
  public abstract String toString();
}
