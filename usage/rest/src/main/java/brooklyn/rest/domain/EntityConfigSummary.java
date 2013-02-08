package brooklyn.rest.domain;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.catalog.CatalogConfig;
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
      @JsonProperty("label") String label,
      @JsonProperty("links") Map<String, URI> links
  ) {
    super(name, type, description, defaultValue, reconfigurable, label);
    this.links = links!=null ? ImmutableMap.copyOf(links) : null;
  }

  protected EntityConfigSummary(ConfigKey<?> config, String label, Map<String, URI> links) {
      super(config.getName(), config.getTypeName(), 
          config.getDescription(), config.getDefaultValue(), config.isReconfigurable(), label);
      this.links = links==null ? null : ImmutableMap.copyOf(links);
  }
  
  /** generates a representation for a given config key, 
   * with label inferred from annoation in the entity class,
   * and links pointing to the entity and the applicaiton */
  public static EntityConfigSummary fromEntity(EntityLocal entity, ConfigKey<?> config) {
      /*
       * following code nearly there to get the @CatalogConfig annotation
       * in the class and use that to populate a label
       */
      
//    EntityDynamicType typeMap = 
//            ((AbstractEntity)entity).getMutableEntityType();
//      // above line works if we can cast; line below won't work, but there should some way
//      // to get back the handle to the spec from an entity local, which then *would* work
//            EntityTypes.getDefinedEntityType(entity.getClass());
      
//    String label = typeMap.getConfigKeyField(config.getName());
    String label = null;
    
    String applicationUri = "/v1/applications/" + entity.getApplicationId();
    String entityUri = applicationUri + "/entities/" + entity.getId();
    Map<String,URI> links = ImmutableMap.<String, URI>builder()
        .put("self", URI.create(entityUri + "/config/" + config.getName()))
        .put("application", URI.create(applicationUri))
        .put("entity", URI.create(entityUri))
        .build();
    
    return new EntityConfigSummary(config, label, links);
  }

  /** generates a representation for a given config key, with no label or links */
  public static EntityConfigSummary forCatalog(ConfigKey<?> config) {
      return new EntityConfigSummary(config, null, null);
  }
  
  /** generates a representation for a given config key, with no links, but label from this field */
  public static EntityConfigSummary forCatalog(ConfigKey<?> config, Field configKeyField) {
      CatalogConfig catalogConfig = configKeyField!=null ? configKeyField.getAnnotation(CatalogConfig.class) : null;
      String label = catalogConfig==null ? null : catalogConfig.label();
      return new EntityConfigSummary(config, label, null);
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
