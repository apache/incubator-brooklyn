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
package org.apache.brooklyn.rest.transform;

import static com.google.common.collect.Iterables.transform;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.rest.domain.EntityConfigSummary;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.util.collections.MutableMap;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import javax.ws.rs.core.UriBuilder;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.rest.api.ApplicationApi;
import org.apache.brooklyn.rest.api.CatalogApi;
import org.apache.brooklyn.rest.api.EntityApi;
import org.apache.brooklyn.rest.api.EntityConfigApi;
import static org.apache.brooklyn.rest.util.WebResourceUtils.serviceUriBuilder;

/**
 * @author Adam Lowe
 */
public class EntityTransformer {

    public static final Function<? super Entity, EntitySummary> fromEntity(final UriBuilder ub) {
        return new Function<Entity, EntitySummary>() {
            @Override
            public EntitySummary apply(Entity entity) {
                return EntityTransformer.entitySummary(entity, ub);
            }
        };
    };

    public static EntitySummary entitySummary(Entity entity, UriBuilder ub) {
        URI applicationUri = serviceUriBuilder(ub, ApplicationApi.class, "get").build(entity.getApplicationId());
        URI entityUri = serviceUriBuilder(ub, EntityApi.class, "get").build(entity.getApplicationId(), entity.getId());
        ImmutableMap.Builder<String, URI> lb = ImmutableMap.<String, URI>builder()
                .put("self", entityUri);
        if (entity.getParent()!=null) {
            URI parentUri = serviceUriBuilder(ub, EntityApi.class, "get").build(entity.getApplicationId(), entity.getParent().getId());
            lb.put("parent", parentUri);
        }

//        UriBuilder urib = serviceUriBuilder(ub, EntityApi.class, "getChildren").build(entity.getApplicationId(), entity.getId());
        // TODO: change all these as well :S
        lb.put("application", applicationUri)
                .put("children", URI.create(entityUri + "/children"))
                .put("config", URI.create(entityUri + "/config"))
                .put("sensors", URI.create(entityUri + "/sensors"))
                .put("effectors", URI.create(entityUri + "/effectors"))
                .put("policies", URI.create(entityUri + "/policies"))
                .put("activities", URI.create(entityUri + "/activities"))
                .put("locations", URI.create(entityUri + "/locations"))
                .put("tags", URI.create(entityUri + "/tags"))
                .put("expunge", URI.create(entityUri + "/expunge"))
                .put("rename", URI.create(entityUri + "/name"))
                .put("spec", URI.create(entityUri + "/spec"))
            ;

        if (entity.getIconUrl()!=null)
            lb.put("iconUrl", URI.create(entityUri + "/icon"));

        if (entity.getCatalogItemId() != null) {
            String versionedId = entity.getCatalogItemId();
            URI catalogUri;
            if (CatalogUtils.looksLikeVersionedId(versionedId)) {
                String symbolicName = CatalogUtils.getSymbolicNameFromVersionedId(versionedId);
                String version = CatalogUtils.getVersionFromVersionedId(versionedId);
                catalogUri = serviceUriBuilder(ub, CatalogApi.class, "getEntity").build(symbolicName, version);
            } else {
                catalogUri = serviceUriBuilder(ub, CatalogApi.class, "getEntity_0_7_0").build(versionedId);
            }
            lb.put("catalog", catalogUri);
        }

        String type = entity.getEntityType().getName();
        return new EntitySummary(entity.getId(), entity.getDisplayName(), type, entity.getCatalogItemId(), lb.build());
    }

    public static List<EntitySummary> entitySummaries(Iterable<? extends Entity> entities, final UriBuilder ub) {
        return Lists.newArrayList(transform(
            entities,
            new Function<Entity, EntitySummary>() {
                @Override
                public EntitySummary apply(Entity entity) {
                    return EntityTransformer.entitySummary(entity, ub);
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
    public static EntityConfigSummary entityConfigSummary(Entity entity, ConfigKey<?> config, UriBuilder ub) {
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

        URI applicationUri = serviceUriBuilder(ub, ApplicationApi.class, "get").build(entity.getApplicationId());
        URI entityUri = serviceUriBuilder(ub, EntityApi.class, "get").build(entity.getApplicationId(), entity.getId());
        URI selfUri = serviceUriBuilder(ub, EntityConfigApi.class, "get").build(entity.getApplicationId(), entity.getId(), config.getName());
        
        MutableMap.Builder<String, URI> lb = MutableMap.<String, URI>builder()
            .put("self", selfUri)
            .put("application", applicationUri)
            .put("entity", entityUri)
            .put("action:json", selfUri);

        Iterable<RendererHints.NamedAction> hints = Iterables.filter(RendererHints.getHintsFor(config), RendererHints.NamedAction.class);
        for (RendererHints.NamedAction na : hints) {
            SensorTransformer.addNamedAction(lb, na, entity.getConfig(config), config, entity);
        }
    
        return entityConfigSummary(config, label, priority, lb.build());
    }

    public static URI applicationUri(Application entity, UriBuilder ub) {
        return serviceUriBuilder(ub, ApplicationApi.class, "get").build(entity.getApplicationId());
    }
    
    public static URI entityUri(Entity entity, UriBuilder ub) {
        return serviceUriBuilder(ub, EntityApi.class, "get").build(entity.getApplicationId(), entity.getId());
    }
    
    public static EntityConfigSummary entityConfigSummary(ConfigKey<?> config, Field configKeyField) {
        CatalogConfig catalogConfig = configKeyField!=null ? configKeyField.getAnnotation(CatalogConfig.class) : null;
        String label = catalogConfig==null ? null : catalogConfig.label();
        Double priority = catalogConfig==null ? null : catalogConfig.priority();
        return entityConfigSummary(config, label, priority, null);
    }

    public static EntityConfigSummary entityConfigSummary(SpecParameter<?> input) {
        Double priority = input.isPinned() ? Double.valueOf(1d) : null;
        return entityConfigSummary(input.getConfigKey(), input.getLabel(), priority, null);
    }

}
