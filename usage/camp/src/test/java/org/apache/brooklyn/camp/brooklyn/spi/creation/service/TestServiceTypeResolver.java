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

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.util.text.Strings;

@SuppressWarnings("deprecation")
public class TestServiceTypeResolver implements ServiceTypeResolver {

    private static final String PREFIX = "test-resolver";

    @Override
    public String getTypePrefix() {
        return PREFIX;
    }

    @Override
    public String getBrooklynType(String serviceType) {
        return Strings.removeFromStart(serviceType, PREFIX + ":");
    }

    @SuppressWarnings("unchecked")
    @Override
    public CatalogItem<Entity, EntitySpec<?>> getCatalogItem(BrooklynComponentTemplateResolver resolver, String serviceType) {
        return (CatalogItem<Entity, EntitySpec<?>>) CatalogUtils.getCatalogItemOptionalVersion(resolver.getManagementContext(), getBrooklynType(serviceType));
    }

    @Override
    public <T extends Entity> void decorateSpec(BrooklynComponentTemplateResolver resolver, EntitySpec<T> spec) {
        spec.configure("resolver", TestServiceTypeResolver.class);
    }

}
