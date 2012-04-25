package brooklyn.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

public class ApplicationSpec {

  private String name;
  private Set<EntitySpec> entities;
  private Set<String> locations;

  // TODO add policies

  public ApplicationSpec(
      @JsonProperty("name") String name,
      @JsonProperty("entities") Set<EntitySpec> entities,
      @JsonProperty("locations") Set<String> locations
  ) {
    this.name = checkNotNull(name, "name");
    this.entities = ImmutableSet.copyOf(checkNotNull(entities));
    this.locations = ImmutableSet.copyOf(checkNotNull(locations));
  }

  public String getName() {
    return name;
  }

  public Set<EntitySpec> getEntities() {
    return entities;
  }

  public Set<String> getLocations() {
    return locations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ApplicationSpec that = (ApplicationSpec) o;

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
    return "ApplicationSpec{" +
        "name='" + name + '\'' +
        ", entitySpecs=" + entities +
        ", locations=" + locations +
        '}';
  }
}
