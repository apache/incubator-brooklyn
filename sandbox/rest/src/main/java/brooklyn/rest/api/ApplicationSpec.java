package brooklyn.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.entity.Application;
import brooklyn.location.Location;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ApplicationSpec {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String name;
    private String type;
    private Set<EntitySpec> entities;
    private Set<String> locations;
    private Map<String,String> config;

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

    public Builder type(String type) {
      this.type = type;
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

    public Builder config(Map<String,String> config) {
        this.config = config;
        return this;
    }

    public ApplicationSpec build() {
      return new ApplicationSpec(name, type, entities, locations, config);
    }
  }

  private final String name;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String type;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Set<EntitySpec> entities;
  private final Set<String> locations;
  @JsonSerialize(include=Inclusion.NON_EMPTY)
  private final Map<String, String> config;

  public ApplicationSpec(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("entities") Set<EntitySpec> entities,
      @JsonProperty("locations") Collection<String> locations,
      @JsonProperty("config") Map<String, String> config
  ) {
    this.name = checkNotNull(name, "name must be provided for an application spec");
    this.type = type;
    if (entities==null) this.entities = null;
    else this.entities = (entities.isEmpty() && type!=null) ? null : ImmutableSet.copyOf(entities);
    this.locations = ImmutableSet.copyOf(checkNotNull(locations, "locations must be provided for an application spec"));
    this.config = config == null ? Collections.<String, String>emptyMap() : ImmutableMap.<String, String>copyOf(config);
    if (this.entities!=null && this.type!=null) throw new IllegalStateException("cannot supply both type and entities for an application spec");
    if (this.entities==null && this.type==null) throw new IllegalStateException("must supply either type or entities for an application spec");
  }

  public static ApplicationSpec fromApplication(Application application) {
      Collection<String> locations = Collections2.transform(application.getLocations(), new Function<Location,String>() {
        @Override @Nullable
        public String apply(@Nullable Location input) { return input.getId(); }
      });
      // okay to have entities and config as null, as this comes from a real instance
      return new ApplicationSpec(application.getDisplayName(), application.getEntityType().getName(),
              null, locations, null);
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
}
  
  public Set<EntitySpec> getEntities() {
    return entities;
  }

  public Set<String> getLocations() {
    return locations;
  }

  public Map<String, String> getConfig() {
    return config;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ApplicationSpec that = (ApplicationSpec) o;

    if (name != null ? !name.equals(that.name) : that.name != null)
        return false;
    if (type != null ? !type.equals(that.type) : that.type != null)
        return false;
    if (entities != null ? !entities.equals(that.entities) : that.entities != null)
      return false;
    if (locations != null ? !locations.equals(that.locations) : that.locations != null)
      return false;
    if (config != null ? !config.equals(that.config) : that.config != null)
        return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (entities != null ? entities.hashCode() : 0);
    result = 31 * result + (locations != null ? locations.hashCode() : 0);
    result = 31 * result + (config != null ? config.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ApplicationSpec{" +
        "name='" + name + '\'' +
        ", type=" + type +
        ", entitySpecs=" + entities +
        ", locations=" + locations +
        ", config=" + config +
        '}';
  }

}
