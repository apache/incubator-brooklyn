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
package org.apache.brooklyn.core.resolve;

import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.persist.DeserializingClassRenamesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

public class CatalogServiceSpecResolver extends AbstractServiceSpecResolver {
    private static final Logger log = LoggerFactory.getLogger(CatalogServiceSpecResolver.class);

    private static final String RESOLVER_NAME = "catalog";

    public CatalogServiceSpecResolver() {
        super(RESOLVER_NAME);
    }

    @Override
    protected boolean canResolve(String type, BrooklynClassLoadingContext loader) {
        String localType = getLocalType(type);
        CatalogItem<Entity, EntitySpec<?>> item = getCatalogItem(mgmt, localType);
        if (item != null) {
            try {
                //Keeps behaviour of previous functionality, but probably should throw instead when using disabled items.
                checkUsable(item);
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public EntitySpec<?> resolve(String type, BrooklynClassLoadingContext loader, Set<String> parentEncounteredTypes) {
        String localType = getLocalType(type);
        CatalogItem<Entity, EntitySpec<?>> item = getCatalogItem(mgmt, localType);

        if (item == null) return null;
        checkUsable(item);

        //Take the symbolicName part of the catalog item only for recursion detection to prevent
        //cross referencing of different versions. Not interested in non-catalog item types.
        //Prevent catalog items self-referencing even if explicitly different version.
        boolean nonRecursiveCall = !parentEncounteredTypes.contains(item.getSymbolicName());
        if (nonRecursiveCall) {
            // Make a copy of the encountered types, so that we add the item being resolved for
            // dependency items only. Siblings must not see we are resolving this item.
            Set<String> encounteredTypes = ImmutableSet.<String>builder()
                    .addAll(parentEncounteredTypes)
                    .add(item.getSymbolicName())
                    .build();

            // CatalogItem generics are just getting in the way, better get rid of them, we
            // are casting anyway.
            @SuppressWarnings({ "rawtypes" })
            CatalogItem rawItem = item;
            @SuppressWarnings({ "rawtypes", "unchecked" })
            AbstractBrooklynObjectSpec rawSpec = EntityManagementUtils.createCatalogSpec(mgmt, rawItem, encounteredTypes);
            return (EntitySpec<?>) rawSpec;
        } else {
            return null;
        }
    }

    private void checkUsable(CatalogItem<Entity, EntitySpec<?>> item) {
        if (item.isDisabled()) {
            throw new IllegalStateException("Illegal use of disabled catalog item "+item.getSymbolicName()+":"+item.getVersion());
        } else if (item.isDeprecated()) {
            log.warn("Use of deprecated catalog item "+item.getSymbolicName()+":"+item.getVersion());
        }
    }

    protected CatalogItem<Entity,EntitySpec<?>> getCatalogItem(ManagementContext mgmt, String brooklynType) {
        brooklynType = DeserializingClassRenamesProvider.findMappedName(brooklynType);
        return CatalogUtils.getCatalogItemOptionalVersion(mgmt, Entity.class,  brooklynType);
    }

}
