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
package org.apache.brooklyn.rest.resources;

import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.rest.api.EntityConfigApi;
import org.apache.brooklyn.rest.domain.EntityConfigSummary;
import org.apache.brooklyn.rest.filter.HaHotStateRequired;
import org.apache.brooklyn.rest.transform.EntityTransformer;
import org.apache.brooklyn.rest.util.WebResourceUtils;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@HaHotStateRequired
public class EntityConfigResource extends AbstractBrooklynRestResource implements EntityConfigApi {

    private static final Logger LOG = LoggerFactory.getLogger(EntityConfigResource.class);

    @Override
    public List<EntityConfigSummary> list(final String application, final String entityToken) {
        final Entity entity = brooklyn().getEntity(application, entityToken);
        // TODO merge with keys which have values
        return Lists.newArrayList(transform(
                entity.getEntityType().getConfigKeys(),
                new Function<ConfigKey<?>, EntityConfigSummary>() {
                    @Override
                    public EntityConfigSummary apply(ConfigKey<?> config) {
                        return EntityTransformer.entityConfigSummary(entity, config);
                    }
                }));
    }

    // TODO support parameters  ?show=value,summary&name=xxx &format={string,json,xml}
    // (and in sensors class)
    @Override
    public Map<String, Object> batchConfigRead(String application, String entityToken, Boolean raw) {
        // TODO: add test
        Entity entity = brooklyn().getEntity(application, entityToken);
        // wrap in a task for better runtime view
        return Entities.submit(entity, Tasks.<Map<String,Object>>builder().displayName("REST API batch config read").body(new BatchConfigRead(this, entity, raw)).build()).getUnchecked();
    }
    
    private static class BatchConfigRead implements Callable<Map<String,Object>> {
        private EntityConfigResource resource;
        private Entity entity;
        private Boolean raw;

        public BatchConfigRead(EntityConfigResource resource, Entity entity, Boolean raw) {
            this.resource = resource;
            this.entity = entity;
            this.raw = raw;
        }

        @Override
        public Map<String, Object> call() throws Exception {
            Map<ConfigKey<?>, ?> source = ((EntityInternal) entity).config().getBag().getAllConfigAsConfigKeyMap();
            Map<String, Object> result = Maps.newLinkedHashMap();
            for (Map.Entry<ConfigKey<?>, ?> ek : source.entrySet()) {
                Object value = ek.getValue();
                result.put(ek.getKey().getName(), 
                    resource.resolving(value).preferJson(true).asJerseyOutermostReturnValue(false).raw(raw).context(entity).timeout(Duration.ZERO).renderAs(ek.getKey()).resolve());
            }
            return result;
        }
    }

    @Override
    public Object get(String application, String entityToken, String configKeyName, Boolean raw) {
        return get(true, application, entityToken, configKeyName, raw);
    }

    @Override
    public String getPlain(String application, String entityToken, String configKeyName, Boolean raw) {
        return Strings.toString(get(false, application, entityToken, configKeyName, raw));
    }

    public Object get(boolean preferJson, String application, String entityToken, String configKeyName, Boolean raw) {
        Entity entity = brooklyn().getEntity(application, entityToken);
        ConfigKey<?> ck = findConfig(entity, configKeyName);
        Object value = ((EntityInternal)entity).config().getRaw(ck).orNull();
        return resolving(value).preferJson(preferJson).asJerseyOutermostReturnValue(true).raw(raw).context(entity).timeout(ValueResolver.PRETTY_QUICK_WAIT).renderAs(ck).resolve();
    }

    private ConfigKey<?> findConfig(Entity entity, String configKeyName) {
        ConfigKey<?> ck = entity.getEntityType().getConfigKey(configKeyName);
        if (ck == null)
            ck = new BasicConfigKey<Object>(Object.class, configKeyName);
        return ck;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void setFromMap(String application, String entityToken, Boolean recurse, Map newValues) {
        final Entity entity = brooklyn().getEntity(application, entityToken);
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.MODIFY_ENTITY, entity)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to modify entity '%s'",
                    Entitlements.getEntitlementContext().user(), entity);
        }

        if (LOG.isDebugEnabled())
            LOG.debug("REST user " + Entitlements.getEntitlementContext() + " setting configs " + newValues);
        for (Object entry : newValues.entrySet()) {
            String configName = Strings.toString(((Map.Entry) entry).getKey());
            Object newValue = ((Map.Entry) entry).getValue();

            ConfigKey ck = findConfig(entity, configName);
            ((EntityInternal) entity).config().set(ck, TypeCoercions.coerce(newValue, ck.getTypeToken()));
            if (Boolean.TRUE.equals(recurse)) {
                for (Entity e2 : Entities.descendants(entity, Predicates.alwaysTrue(), false)) {
                    ((EntityInternal) e2).config().set(ck, newValue);
                }
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void set(String application, String entityToken, String configName, Boolean recurse, Object newValue) {
        final Entity entity = brooklyn().getEntity(application, entityToken);
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.MODIFY_ENTITY, entity)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to modify entity '%s'",
                    Entitlements.getEntitlementContext().user(), entity);
        }

        ConfigKey ck = findConfig(entity, configName);
        LOG.debug("REST setting config " + configName + " on " + entity + " to " + newValue);
        ((EntityInternal) entity).config().set(ck, TypeCoercions.coerce(newValue, ck.getTypeToken()));
        if (Boolean.TRUE.equals(recurse)) {
            for (Entity e2 : Entities.descendants(entity, Predicates.alwaysTrue(), false)) {
                ((EntityInternal) e2).config().set(ck, newValue);
            }
        }
    }
}
