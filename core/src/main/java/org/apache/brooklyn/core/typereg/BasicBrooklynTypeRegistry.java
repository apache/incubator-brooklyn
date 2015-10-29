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
package org.apache.brooklyn.core.typereg;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.typereg.RegisteredTypes.JavaTypeImplementation;
import org.apache.brooklyn.core.typereg.RegisteredTypes.RegisteredSpecType;
import org.apache.brooklyn.core.typereg.RegisteredTypes.TypeImplementation;
import org.apache.brooklyn.util.collections.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class BasicBrooklynTypeRegistry implements BrooklynTypeRegistry {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BasicBrooklynTypeRegistry.class);
    
    private ManagementContext mgmt;

    public BasicBrooklynTypeRegistry(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    private static final Function<CatalogItem<?,?>,RegisteredType> CI_TO_RT = new Function<CatalogItem<?,?>, RegisteredType>() {
        @Override
        public RegisteredType apply(CatalogItem<?, ?> item) {
            if (item==null) return null;
            TypeImplementation impl = null;
            if (item.getPlanYaml()!=null) {
                impl = new TypeImplementation(null, item.getPlanYaml());
            }
            if (item.getJavaType()!=null) {
                impl = new JavaTypeImplementation(item.getJavaType());
            }
            if (impl!=null) {
                RegisteredSpecType type = new RegisteredSpecType(item.getSymbolicName(), item.getVersion(),
                    item.getCatalogItemJavaType(), impl);
                type.bundles = MutableList.<OsgiBundleWithUrl>copyOf(item.getLibraries());
                type.displayName = item.getDisplayName();
                type.description = item.getDescription();
                type.iconUrl = item.getIconUrl();
                
                // TODO
                // disabled, deprecated
                // javaType, specType, registeredTypeName ...
                // tags ?
                return type;
            }
            throw new IllegalStateException("Unsupported catalog item "+item+" when trying to create RegisteredType");
        }
    };
    
    public Iterable<RegisteredType> getAll() {
        return getAll(Predicates.alwaysTrue());
    }
    
    @Override
    public Iterable<RegisteredType> getAll(Predicate<? super RegisteredType> filter) {
        return Iterables.filter(Iterables.transform(mgmt.getCatalog().getCatalogItems(), CI_TO_RT), filter);
    }

    @Override
    public RegisteredType get(String symbolicNameWithOptionalVersion, RegisteredTypeKind kind, Class<?> parentClass) {
        if (CatalogUtils.looksLikeVersionedId(symbolicNameWithOptionalVersion)) {
            String id = CatalogUtils.getSymbolicNameFromVersionedId(symbolicNameWithOptionalVersion);
            String version = CatalogUtils.getVersionFromVersionedId(symbolicNameWithOptionalVersion);
            return get(id, version, kind, parentClass);
        } else {
            return get(symbolicNameWithOptionalVersion, BrooklynCatalog.DEFAULT_VERSION, kind, parentClass);
        }
    }

    @Override
    public RegisteredType get(String symbolicName, String version, RegisteredTypeKind kind, Class<?> parentClass) {
        return CI_TO_RT.apply( mgmt.getCatalog().getCatalogItem(symbolicName, version) );
    }

    @Override
    public RegisteredType get(String symbolicName, String version) {
        return get(symbolicName, version, null, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T extends AbstractBrooklynObjectSpec> T createSpec(RegisteredType type, Class<T> specKind) {
        if (!(type instanceof RegisteredSpecType)) { 
            throw new IllegalStateException("Cannot create spec from type "+type);
        }
        
        CatalogItem item = mgmt.getCatalog().getCatalogItem(type.getSymbolicName(), type.getVersion());
        return (T) mgmt.getCatalog().createSpec(item);
    }

}
