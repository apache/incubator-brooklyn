package brooklyn.rest.transform;

import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.util.JsonUtils;
import com.google.common.collect.ImmutableMap;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: aplowe
 * Date: 05/07/2013
 * Time: 17:02
 * To change this template use File | Settings | File Templates.
 */
public class EntityTransformer {

    public static EntitySummary entitySummary(Entity entity) {
        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();
        ImmutableMap.Builder<String, URI> lb = ImmutableMap.<String, URI>builder()
                .put("self", URI.create(entityUri));
        if (entity.getParent()!=null)
            lb.put("parent", URI.create(applicationUri+"/entities/"+entity.getParent().getId()));
        String type = entity.getEntityType().getName();
        lb.put("application", URI.create(applicationUri))
                .put("children", URI.create(entityUri + "/entities"))
                .put("config", URI.create(entityUri + "/config"))
                .put("sensors", URI.create(entityUri + "/sensors"))
                .put("effectors", URI.create(entityUri + "/effectors"))
                .put("policies", URI.create(entityUri + "/policies"))
                .put("activities", URI.create(entityUri + "/activities"))
                .put("catalog", URI.create("/v1/catalog/entities/" + type));

        return new EntitySummary(entity.getId(), entity.getDisplayName(), type, lb.build());
    }


    protected static EntityConfigSummary entityConfigSummary(ConfigKey<?> config, String label, Double priority, Map<String, URI> links) {
        Map<String, URI> mapOfLinks =  links==null ? null : ImmutableMap.copyOf(links);
        return new EntityConfigSummary(config.getName(), config.getTypeName(),
                config.getDescription(),
                JsonUtils.toJsonable(config.getDefaultValue()), config.isReconfigurable(), label, priority, mapOfLinks);
    }
    /** generates a representation for a given config key, 
     * with label inferred from annoation in the entity class,
     * and links pointing to the entity and the applicaiton */
    public static EntityConfigSummary entityConfigSummary(EntityLocal entity, ConfigKey<?> config) {
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
        Double priority = null;

        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();
        Map<String,URI> links = ImmutableMap.<String, URI>builder()
                .put("self", URI.create(entityUri + "/config/" + config.getName()))
                .put("application", URI.create(applicationUri))
                .put("entity", URI.create(entityUri))
                .build();
        return entityConfigSummary(config, label, priority, links);
    }

    protected static EntityConfigSummary entityConfigSummary(ConfigKey<?> config, Field configKeyField) {
        CatalogConfig catalogConfig = configKeyField!=null ? configKeyField.getAnnotation(CatalogConfig.class) : null;
        String label = catalogConfig==null ? null : catalogConfig.label();
        Double priority = catalogConfig==null ? null : catalogConfig.priority();
        return entityConfigSummary(config, label, priority, null);
    }

}
