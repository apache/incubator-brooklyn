package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.EntityLocal;

import com.google.common.collect.ImmutableMap;

public class EntityConfigSummary extends ConfigSummary {

  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Map<String, URI> links;

  public EntityConfigSummary(
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("description") String description,
      @JsonProperty("defaultValue") Object defaultValue,
      @JsonProperty("reconfigurable") boolean reconfigurable,
      @JsonProperty("links") Map<String, URI> links
  ) {
    super(name, type, description, defaultValue, reconfigurable, links);
    this.links = links!=null ? ImmutableMap.copyOf(links) : null;
  }

  public EntityConfigSummary(ApplicationSummary application, EntityLocal entity, ConfigKey<?> config) {
      this(entity, config);
      if (!entity.getApplicationId().equals(application.getInstance().getId()))
          throw new IllegalStateException("Application "+application+" does not match app "+entity.getApplication()+" of "+entity);
  }
  
  public EntityConfigSummary(EntityLocal entity, ConfigKey<?> config) {
    super(config);
    
    String applicationUri = "/v1/applications/" + entity.getApplicationId();
    String entityUri = applicationUri + "/entities/" + entity.getId();

    this.links = ImmutableMap.<String, URI>builder()
        .put("self", URI.create(entityUri + "/config/" + config.getName()))
        .put("application", URI.create(applicationUri))
        .put("entity", URI.create(entityUri))
        .build();
  }
  
  public static EntityConfigSummary forCatalog(ConfigKey<?> config) {
      return new EntityConfigSummary(config.getName(), config.getTypeName(), 
              config.getDescription(), config.getDefaultValue(), config.isReconfigurable(), null);
  }

  @Override
  public Map<String, URI> getLinks() {
    return links;
  }

  @Override
  public String toString() {
    return "EntityConfigSummary{" +
        "name='" + getName() + '\'' +
        ", type='" + getType() + '\'' +
        '}';
  }
}
