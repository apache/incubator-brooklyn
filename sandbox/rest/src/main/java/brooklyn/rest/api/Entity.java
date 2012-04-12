package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.codehaus.jackson.annotate.JsonProperty;

public class Entity {

  private static final Map<String, String> emptyMap = ImmutableMap.<String, String>of();

  private final String name;
  private final Map<String, String> config;

  public Entity(String name) {
    this(name, emptyMap);
  }

  public Entity(@JsonProperty("name") String name, @JsonProperty("config") Map<String, String> config) {
    this.name = name;
    this.config = ImmutableMap.copyOf(config);
  }

  public String getName() {
    return name;
  }

  public Map<String, String> getConfig() {
    return config;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Entity entity = (Entity) o;

    if (config != null ? !config.equals(entity.config) : entity.config != null)
      return false;
    if (name != null ? !name.equals(entity.name) : entity.name != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (config != null ? config.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Entity{" +
        "name='" + name + '\'' +
        ", config=" + config +
        '}';
  }
}
