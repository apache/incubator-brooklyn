package brooklyn.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

public class Application {

  private String name;
  private Set<Entity> entities;
  private Set<String> locations;

  public Application(
      @JsonProperty("name") String name,
      @JsonProperty("entities") Set<Entity> entities,
      @JsonProperty("locations") Set<String> locations
  ) {
    this.name = checkNotNull(name, "name");
    this.entities = ImmutableSet.copyOf(entities);
    this.locations = ImmutableSet.copyOf(locations);
  }

  public String getName() {
    return name;
  }

  public Set<Entity> getEntities() {
    return entities;
  }

  public Set<String> getLocations() {
    return locations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Application that = (Application) o;

    if (entities != null ? !entities.equals(that.entities) : that.entities != null)
      return false;
    if (locations != null ? !locations.equals(that.locations) : that.locations != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (entities != null ? entities.hashCode() : 0);
    result = 31 * result + (locations != null ? locations.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Application{" +
        "name='" + name + '\'' +
        ", entities=" + entities +
        ", locations=" + locations +
        '}';
  }
}
