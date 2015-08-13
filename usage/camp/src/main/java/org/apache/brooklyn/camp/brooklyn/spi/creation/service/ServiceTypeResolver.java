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

import java.util.ServiceLoader;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynComponentTemplateResolver;
import org.apache.brooklyn.catalog.CatalogItem;

/**
 * Resolves and decorates {@link EntitySpec entity specifications} based on the {@code serviceType} in a template.
 * <p>
 * The {@link #getTypePrefix()} method returns a string that should match the beginning of the
 * service type. The resolver implementation will use the rest of the service type information
 * to create and decorate an approprate {@link EntitySpec entity}.
 * <p>
 * The resolvers are loaded using the {@link ServiceLoader} mechanism, allowing external libraries
 * to add extra service type implementations that will be picked up at runtime.
 *
 * @see BrooklynServiceTypeResolver
 * @see ChefServiceTypeResolver
 */
public interface ServiceTypeResolver {

    String DEFAULT_TYPE_PREFIX = "brooklyn";

    /**
     * The service type prefix the resolver is responsible for.
     */
    String getTypePrefix();

    /**
     * The name of the Java type that Brooklyn will instantiate to create the
     * service. This can be generated from parts of the service type information
     * or may be a fixed value.
     */
    String getBrooklynType(String serviceType);

    /**
     * Returns the {@link CatalogItem} if there is one for the given type.
     * <p>
     * If no type, callers should fall back to default classloading.
     */
    CatalogItem<Entity, EntitySpec<?>> getCatalogItem(BrooklynComponentTemplateResolver resolver, String serviceType);

    /**
     * Takes the provided {@link EntitySpec} and decorates it appropriately for the service type.
     * <p>
     * This includes setting configuration and adding policies, enrichers and initializers.
     *
     * @see BrooklynServiceTypeResolver#decorateSpec(BrooklynComponentTemplateResolver, EntitySpec)
     */
    <T extends Entity> void decorateSpec(BrooklynComponentTemplateResolver resolver, EntitySpec<T> spec);

}
