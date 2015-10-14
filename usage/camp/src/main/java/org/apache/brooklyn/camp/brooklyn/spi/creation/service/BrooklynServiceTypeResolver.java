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

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.persist.DeserializingClassRenamesProvider;
import org.apache.brooklyn.core.resolve.AbstractServiceSpecResolver;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This converts {@link PlatformComponentTemplate} instances whose type is prefixed {@code brooklyn:}
 * to Brooklyn {@link EntitySpec} instances.
 * 
 * @deprecated since 0.9.0, use {@link AbstractServiceSpecResolver} instead
 */
@Deprecated
public class BrooklynServiceTypeResolver implements ServiceTypeResolver {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ServiceTypeResolver.class);
    
    public BrooklynServiceTypeResolver() {
    }
    
    @Override
    public String getTypePrefix() { return DEFAULT_TYPE_PREFIX; }

    @Override
    public String getBrooklynType(String serviceType) {
        return Strings.removeFromStart(serviceType, getTypePrefix() + ":").trim();
    }

    @Nullable
    @Override
    public CatalogItem<Entity,EntitySpec<?>> getCatalogItem(BrooklynComponentTemplateResolver resolver, String serviceType) {
        String type = getBrooklynType(serviceType);
        if (type != null) {
            return getCatalogItemImpl(resolver.getManagementContext(),  type);
        } else {
            return null;
        }
    }

    @Override
    public <T extends Entity> void decorateSpec(BrooklynComponentTemplateResolver resolver, EntitySpec<T> spec) {
    }

    protected CatalogItem<Entity,EntitySpec<?>> getCatalogItemImpl(ManagementContext mgmt, String brooklynType) {
        brooklynType = DeserializingClassRenamesProvider.findMappedName(brooklynType);
        return CatalogUtils.getCatalogItemOptionalVersion(mgmt, Entity.class,  brooklynType);
    }
}
