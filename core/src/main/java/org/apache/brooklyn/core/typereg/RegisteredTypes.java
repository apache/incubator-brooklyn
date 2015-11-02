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

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredType.TypeImplementationPlan;
import org.apache.brooklyn.core.typereg.JavaTypePlanTransformer.JavaTypeNameImplementation;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class RegisteredTypes {

    /** @deprecated since it was introduced in 0.9.0; for backwards compatibility only, may be removed at any point */
    @Deprecated
    static final Function<CatalogItem<?,?>,RegisteredType> CI_TO_RT = new Function<CatalogItem<?,?>, RegisteredType>() {
        @Override
        public RegisteredType apply(CatalogItem<?, ?> item) {
            return of(item);
        }
    };
    
    /** @deprecated since it was introduced in 0.9.0; for backwards compatibility only, may be removed at any point */
    @Deprecated
    public static RegisteredType of(CatalogItem<?, ?> item) {
        if (item==null) return null;
        TypeImplementationPlan impl = null;
        if (item.getPlanYaml()!=null) {
            impl = new BasicTypeImplementationPlan(null, item.getPlanYaml());
        } else if (item.getJavaType()!=null) {
            impl = new JavaTypeNameImplementation(item.getJavaType());
        } else {
            throw new IllegalStateException("Unsupported catalog item "+item+" when trying to create RegisteredType");
        }
        
        BasicRegisteredType type = (BasicRegisteredType) spec(item.getSymbolicName(), item.getVersion(), item.getCatalogItemJavaType(), impl);
        type.bundles = item.getLibraries()==null ? ImmutableList.<OsgiBundleWithUrl>of() : ImmutableList.<OsgiBundleWithUrl>copyOf(item.getLibraries());
        type.displayName = item.getDisplayName();
        type.description = item.getDescription();
        type.iconUrl = item.getIconUrl();
        type.disabled = item.isDisabled();
        type.deprecated = item.isDeprecated();

        // TODO
        // probably not: javaType, specType, registeredTypeName ...
        // maybe: tags ?
        return type;
    }
    
    public static RegisteredType bean(String symbolicName, String version, Class<?> javaType, TypeImplementationPlan plan) {
        return new BasicRegisteredType(RegisteredTypeKind.BEAN, symbolicName, version, javaType, plan);
    }
    
    public static RegisteredType spec(String symbolicName, String version, Class<?> javaType, TypeImplementationPlan plan) {
        return new BasicRegisteredType(RegisteredTypeKind.SPEC, symbolicName, version, javaType, plan);
    }

    /** returns the implementation data for a spec if it is a string (e.g. plan yaml or java class name); else false */
    @Beta
    public static String getImplementationDataStringForSpec(RegisteredType item) {
        if (item==null || item.getPlan()==null) return null;
        Object data = item.getPlan().getPlanData();
        if (!(data instanceof String)) return null;
        return (String)data;
    }

}
