/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest.transform;

import static com.google.common.collect.Iterables.transform;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.EntitySummary;

/**
 * @author Adam Lowe
 */
public class EntityTransformer {

    public static final Function<? super Entity, EntitySummary> FROM_ENTITY = new Function<Entity, EntitySummary>() {
        @Override
        public EntitySummary apply(Entity entity) {
            return EntityTransformer.entitySummary(entity);
        }
    };

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
                .put("locations", URI.create(entityUri + "/locations"))
                .put("tags", URI.create(entityUri + "/tags"))
                .put("catalog", URI.create("/v1/catalog/entities/" + type))
                .put("expunge", URI.create(entityUri + "/expunge")
            );
        if (entity.getIconUrl()!=null)
            lb.put("iconUrl", URI.create(entityUri + "/icon"));

        return new EntitySummary(entity.getId(), entity.getDisplayName(), type, lb.build());
    }

    public static List<EntitySummary> entitySummaries(Iterable<? extends Entity> entities) {
        return Lists.newArrayList(transform(
            entities,
            new Function<Entity, EntitySummary>() {
                @Override
                public EntitySummary apply(Entity entity) {
                    return EntityTransformer.entitySummary(entity);
                }
            }));
    }

    protected static EntityConfigSummary entityConfigSummary(ConfigKey<?> config, String label, Double priority, Map<String, URI> links) {
        Map<String, URI> mapOfLinks =  links==null ? null : ImmutableMap.copyOf(links);
      return new EntityConfigSummary(config, label, priority, mapOfLinks);
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

    public static String applicationUri(Application entity) {
        return "/v1/applications/" + entity.getApplicationId();
    }
    
    public static String entityUri(Entity entity) {
        return applicationUri(entity.getApplication()) + "/entities/" + entity.getId();
    }
    
    protected static EntityConfigSummary entityConfigSummary(ConfigKey<?> config, Field configKeyField) {
        CatalogConfig catalogConfig = configKeyField!=null ? configKeyField.getAnnotation(CatalogConfig.class) : null;
        String label = catalogConfig==null ? null : catalogConfig.label();
        Double priority = catalogConfig==null ? null : catalogConfig.priority();
        return entityConfigSummary(config, label, priority, null);
    }

}
