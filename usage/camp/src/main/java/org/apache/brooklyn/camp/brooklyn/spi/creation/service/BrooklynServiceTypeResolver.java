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
package org.apache.brooklyn.camp.brooklyn.spi.creation.service;

import io.brooklyn.camp.spi.PlatformComponentTemplate;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver;
import org.apache.brooklyn.catalog.CatalogItem;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.util.text.Strings;

/**
 * This converts {@link PlatformComponentTemplate} instances whose type is prefixed {@code brooklyn:}
 * to Brooklyn {@link EntitySpec} instances.
 */
public class BrooklynServiceTypeResolver implements ServiceTypeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceTypeResolver.class);

    @Override
    public String getTypePrefix() { return DEFAULT_TYPE_PREFIX; }

    @Override
    public String getBrooklynType(String serviceType) {
        String type = Strings.removeFromStart(serviceType, getTypePrefix() + ":").trim();
        if (type == null) return null;
        return type;
    }

    @Nullable
    @Override
    public CatalogItem<Entity,EntitySpec<?>> getCatalogItem(BrooklynComponentTemplateResolver resolver, String serviceType) {
        String type = getBrooklynType(serviceType);
        if (type != null) {
            return CatalogUtils.getCatalogItemOptionalVersion(resolver.getManagementContext(), Entity.class,  type);
        } else {
            return null;
        }
    }

    @Override
    public <T extends Entity> void decorateSpec(BrooklynComponentTemplateResolver resolver, EntitySpec<T> spec) {
        new BrooklynEntityDecorationResolver.PolicySpecResolver(resolver.getYamlLoader()).decorate(spec, resolver.getAttrs());
        new BrooklynEntityDecorationResolver.EnricherSpecResolver(resolver.getYamlLoader()).decorate(spec, resolver.getAttrs());
        new BrooklynEntityDecorationResolver.InitializerResolver(resolver.getYamlLoader()).decorate(spec, resolver.getAttrs());
    }

}
