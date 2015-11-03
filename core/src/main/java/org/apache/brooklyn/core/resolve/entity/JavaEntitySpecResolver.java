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
package org.apache.brooklyn.core.resolve.entity;

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.persist.DeserializingClassRenamesProvider;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.javalang.Reflections;

public class JavaEntitySpecResolver extends AbstractEntitySpecResolver{
    private static final String RESOLVER_NAME = "java";

    public JavaEntitySpecResolver() {
        super(RESOLVER_NAME);
    }

    @Override
    protected String getLocalType(String type) {
        type = super.getLocalType(type);
        type = DeserializingClassRenamesProvider.findMappedName(type);
        return type;
    }
    
    @Override
    protected boolean canResolve(String type, BrooklynClassLoadingContext loader) {
        String localType = getLocalType(type);
        Maybe<?> javaType = tryLoadJavaType(localType, loader);
        return javaType.isPresent();
    }

    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        String localType = getLocalType(type);
        try {
            return resolveInternal(localType, loader);
        } catch (Exception e) {
            boolean firstOccurrence = encounteredTypes.add(localType);
            boolean recursiveButTryJava = !firstOccurrence;
            if (recursiveButTryJava) {
                throw new IllegalStateException("Recursive reference to " + localType + " (and cannot be resolved as a Java type)", e);
            } else {
                throw e;
            }
        }
    }

    private EntitySpec<?> resolveInternal(String localType, BrooklynClassLoadingContext loader) {
        Maybe<Class<? extends Entity>> javaTypeMaybe = tryLoadJavaType(localType, loader);
        if (javaTypeMaybe.isAbsent())
            throw new IllegalStateException("Could not find "+localType, ((Maybe.Absent<?>)javaTypeMaybe).getException());
        Class<? extends Entity> javaType = javaTypeMaybe.get();

        EntitySpec<? extends Entity> spec;
        if (javaType.isInterface()) {
            spec = EntitySpec.create(javaType);
        } else {
            // If this is a concrete class, particularly for an Application class, we want the proxy
            // to expose all interfaces it implements.
            Class<? extends Entity> interfaceclazz = (Application.class.isAssignableFrom(javaType)) ? Application.class : Entity.class;
            List<Class<?>> additionalInterfaceClazzes = Reflections.getAllInterfaces(javaType);
            @SuppressWarnings({ "rawtypes", "unchecked" })
            EntitySpec<?> rawSpec = EntitySpec.create(interfaceclazz)
                .impl((Class) javaType)
                .additionalInterfaces(additionalInterfaceClazzes);
            spec = rawSpec;
        }
        spec.catalogItemId(CatalogUtils.getCatalogItemIdFromLoader(loader));

        return spec;
    }

    private Maybe<Class<? extends Entity>> tryLoadJavaType(String localType, BrooklynClassLoadingContext loader) {
        return loader.tryLoadClass(localType, Entity.class);
    }

}
