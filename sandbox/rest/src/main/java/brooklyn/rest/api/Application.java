package brooklyn.rest.api;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

public class Application {

  private Set<Entity> entities;

  public Application(@JsonProperty("entities") Set<Entity> entities) {
    this.entities = ImmutableSet.copyOf(entities);
  }

  public Set<Entity> getEntities() {
    return entities;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Application that = (Application) o;

    if (entities != null ? !entities.equals(that.entities) : that.entities != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = entities != null ? entities.hashCode() : 0;
    return result;
  }

  @Override
  public String toString() {
    return "Application{" +
        "entities=" + entities +
        '}';
  }
}
