package brooklyn.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonProperty;

public class EntitySpec {

  private static final Map<String, String> emptyMap = ImmutableMap.<String, String>of();

  private final String name;
  private final String type;
  private final Map<String, String> config;

  public EntitySpec(String name, String type) {
    this(name, type, emptyMap);
  }

  public EntitySpec(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("config") Map<String, String> config
  ) {
    this.name = checkNotNull(name, "name");
    this.type = checkNotNull(type, "type");
    this.config = (config != null) ? ImmutableMap.copyOf(config) : ImmutableMap.<String, String>of();
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public Map<String, String> getConfig() {
    return config;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EntitySpec entitySpec = (EntitySpec) o;

    if (config != null ? !config.equals(entitySpec.config) : entitySpec.config != null)
      return false;
    if (name != null ? !name.equals(entitySpec.name) : entitySpec.name != null)
      return false;
    if (type != null ? !type.equals(entitySpec.type) : entitySpec.type != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (config != null ? config.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "EntitySpec{" +
        "name='" + name + '\'' +
        ", type='" + type + '\'' +
        ", config=" + config +
        '}';
  }
}
