package brooklyn.rest.api;

import org.codehaus.jackson.annotate.JsonProperty;

public class Entity {

  private final String className;
  private final String description;

  public Entity(@JsonProperty("className") String className, @JsonProperty("description") String description) {
    this.className = className;
    this.description = description;
  }

  public String getClassName() {
    return className;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Entity entity = (Entity) o;

    if (className != null ? !className.equals(entity.className) : entity.className != null)
      return false;
    if (description != null ? !description.equals(entity.description) : entity.description != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = className != null ? className.hashCode() : 0;
    result = 31 * result + (description != null ? description.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Entity{" +
        "className='" + className + '\'' +
        ", description='" + description + '\'' +
        '}';
  }
}
