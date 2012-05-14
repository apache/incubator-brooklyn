package brooklyn.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

public class ApplicationSpec {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String name;
    private Set<EntitySpec> entities;
    private Set<String> locations;

    public Builder from(ApplicationSpec spec) {
      this.name = spec.name;
      this.entities = spec.entities;
      this.locations = spec.locations;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder entities(Set<EntitySpec> entities) {
      this.entities = entities;
      return this;
    }

    public Builder locations(Set<String> locations) {
      this.locations = locations;
      return this;
    }

    public ApplicationSpec build() {
      return new ApplicationSpec(name, entities, locations);
    }
  }

  private final String name;
  private final Set<EntitySpec> entities;
  private final Set<String> locations;

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
