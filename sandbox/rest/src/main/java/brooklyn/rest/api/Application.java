package brooklyn.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

public class Application {

  private String name;
  private Set<Entity> entities;
  private Set<String> locationRefs;

  public Application(
      @JsonProperty("name") String name,
      @JsonProperty("entities") Set<Entity> entities,
      @JsonProperty("locationRefs") Set<String> locationRefs
  ) {
    this.name = checkNotNull(name, "name");
    this.entities = ImmutableSet.copyOf(entities);
    this.locationRefs = ImmutableSet.copyOf(locationRefs);
  }

  public String getName() {
    return name;
  }

  public Set<Entity> getEntities() {
    return entities;
  }

  public Set<String> getLocationRefs() {
    return locationRefs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Application that = (Application) o;

    if (entities != null ? !entities.equals(that.entities) : that.entities != null)
      return false;
    if (locationRefs != null ? !locationRefs.equals(that.locationRefs) : that.locationRefs != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (entities != null ? entities.hashCode() : 0);
    result = 31 * result + (locationRefs != null ? locationRefs.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Application{" +
        "name='" + name + '\'' +
        ", entities=" + entities +
        ", locationRefs=" + locationRefs +
        '}';
  }
}
