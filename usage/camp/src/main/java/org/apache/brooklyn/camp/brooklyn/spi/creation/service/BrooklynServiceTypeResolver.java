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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver;
import org.apache.brooklyn.camp.spi.PlatformComponentTemplate;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.persist.DeserializingClassRenamesProvider;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

/**
 * This converts {@link PlatformComponentTemplate} instances whose type is prefixed {@code brooklyn:}
 * to Brooklyn {@link EntitySpec} instances.
 */
public class BrooklynServiceTypeResolver implements ServiceTypeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceTypeResolver.class);
    
    private final Map<String, String> deserializingClassRenames;

    public BrooklynServiceTypeResolver() {
        this(DeserializingClassRenamesProvider.loadDeserializingClassRenames());
    }
    
    public BrooklynServiceTypeResolver(Map<String, String> deserializingClassRenames) {
        this.deserializingClassRenames = checkNotNull(deserializingClassRenames, "deserializingClassRenames");
    }
    
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
            return getCatalogItemImpl(resolver.getManagementContext(),  type);
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

    protected CatalogItem<Entity,EntitySpec<?>> getCatalogItemImpl(ManagementContext mgmt, String brooklynType) {
        try {
            return CatalogUtils.getCatalogItemOptionalVersion(mgmt, Entity.class,  brooklynType);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            Optional<String> alternativeType = DeserializingClassRenamesProvider.tryFindMappedName(deserializingClassRenames, brooklynType);
            if (alternativeType.isPresent()) {
                LOG.debug("Transforming entity "+brooklynType+" to "+alternativeType.get());
                try {
                    return CatalogUtils.getCatalogItemOptionalVersion(mgmt, Entity.class,  alternativeType.get());
                } catch (Exception e2) {
                    LOG.debug("Problem getting catalog for transformed type "+alternativeType.get()+"; throwing of untransformed type "+brooklynType, e);
                }
            }
            throw Exceptions.propagate(e);
        }
    }
}
