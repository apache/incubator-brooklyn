package brooklyn.rest.domain;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import brooklyn.rest.util.JsonUtils;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.config.ConfigKey;

import javax.annotation.Nullable;

public abstract class ConfigSummary {

  private final String name;
  private final String type;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Object defaultValue;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String description;
  @JsonSerialize
  private final boolean reconfigurable;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String label;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Double priority;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final List<Map<String, String>> possibleValues;

  protected ConfigSummary(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("description") String description,
      @JsonProperty("defaultValue") Object defaultValue,
      @JsonProperty("reconfigurable") boolean reconfigurable,
      @JsonProperty("label") String label,
      @JsonProperty("priority") Double priority,
      @JsonProperty("possibleValues") List<Map<String, String>> possibleValues
  ) {
    this.name = name;
    this.type = type;
    this.description = description;
    this.defaultValue = defaultValue;
    this.reconfigurable = reconfigurable;
    this.label = label;
    this.priority = priority;
    this.possibleValues = possibleValues;
  }

  protected ConfigSummary(ConfigKey<?> config) {
    this(config, null, null);
  }

  protected ConfigSummary(ConfigKey<?> config, String label, Double priority) {
    this.name = config.getName();
    this.description = config.getDescription();
    this.reconfigurable = config.isReconfigurable();

    /* Use String, to guarantee it is serializable; otherwise get:
     *   No serializer found for class brooklyn.policy.autoscaling.AutoScalerPolicy$3 and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS) ) (through reference chain: java.util.ArrayList[9]->brooklyn.rest.domain.PolicyConfigSummary["defaultValue"])
     *   at org.codehaus.jackson.map.ser.impl.UnknownSerializer.failForEmpty(UnknownSerializer.java:52)
     */
    this.label = label;
    this.priority = priority;
    if (config.getType().isEnum()) {
      this.type = Enum.class.getName();
      this.defaultValue = (config.getDefaultValue() == null) ? null : ((Enum)config.getDefaultValue()).name();
      this.possibleValues = FluentIterable
          .from(Arrays.asList((Enum[])(config.getType().getEnumConstants())))
          .transform(new Function<Enum, Map<String, String>>() {
              @Nullable
              @Override
              public Map<String, String> apply(@Nullable Enum input) {
                  return ImmutableMap.of(
                    "value", input != null ? input.name() : null,
                    "description", input != null ? input.toString() : null
                );
            }
          })
          .toList();
    } else {
      this.type = config.getTypeName();
      this.defaultValue = (config.getDefaultValue() == null) ? null : JsonUtils.toJsonable(config.getDefaultValue());
      this.possibleValues = null;
    }
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
    // note constructor has converted to string, so this is safe for clients to use
    return defaultValue;
  }
  
  public String getLabel() {
    return label;
  }
  
  public Double getPriority() {
    return priority;
  }

  public List<Map<String, String>> getPossibleValues() {
    return possibleValues;
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
