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

import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.util.javalang.JavaClassNames;

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
        TypeImplementation impl = null;
        if (item.getPlanYaml()!=null) {
            impl = new TypeImplementation(null, item.getPlanYaml());
        } else if (item.getJavaType()!=null) {
            impl = new JavaTypeImplementation(item.getJavaType());
        } else {
            throw new IllegalStateException("Unsupported catalog item "+item+" when trying to create RegisteredType");
        }
        
        RegisteredSpecType type = new RegisteredSpecType(item.getSymbolicName(), item.getVersion(),
            item.getCatalogItemJavaType(), impl);
        type.bundles = item.getLibraries()==null ? ImmutableList.<OsgiBundleWithUrl>of() : ImmutableList.<OsgiBundleWithUrl>copyOf(item.getLibraries());
        type.displayName = item.getDisplayName();
        type.description = item.getDescription();
        type.iconUrl = item.getIconUrl();
        type.disabled = item.isDisabled();
        type.deprecated = item.isDeprecated();

        // TODO
        // javaType, specType, registeredTypeName ...
        // tags ?
        return type;
    }

    /** Visitor adapter which can be used to ensure all kinds are supported */
    public static abstract class RegisteredTypeKindVisitor<T> {
        public T visit(RegisteredType type) {
            if (type==null) throw new NullPointerException("Registered type must not be null");
            if (type instanceof RegisteredSpecType) {
                return visitSpec((RegisteredSpecType)type);
            }
            // others go here
            throw new IllegalStateException("Unexpected registered type: "+type.getClass());
        }

        protected abstract T visitSpec(RegisteredSpecType type);
        
        // TODO beans, others
    }
    
    public static RegisteredTypeKind getKindOf(RegisteredType type) {
        return new RegisteredTypeKindVisitor<RegisteredTypeKind>() {
            @Override protected RegisteredTypeKind visitSpec(RegisteredSpecType type) { return RegisteredTypeKind.SPEC; }
        }.visit(type);
    }
    
    public abstract static class AbstractRegisteredType implements RegisteredType {

        final String symbolicName;
        final String version;
        
        List<OsgiBundleWithUrl> bundles;
        String displayName;
        String description;
        String iconUrl;
        boolean deprecated;
        boolean disabled;

        // TODO ensure this is re-populated on rebind
        transient Class<?> javaType;
        
        public AbstractRegisteredType(String symbolicName, String version, Class<?> javaType) {
            this.symbolicName = symbolicName;
            this.version = version;
            this.javaType = javaType;
        }

        @Override
        public String getId() {
            return symbolicName + (version!=null ? ":"+version : "");
        }

        @Override
        public String getSymbolicName() {
            return symbolicName;
        }

        @Override
        public String getVersion() {
            return version;
        }
        
        @Override
        public Collection<OsgiBundleWithUrl> getLibraries() {
            return bundles;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getIconUrl() {
            return iconUrl;
        }
        
        @Override
        public boolean isDisabled() {
            return disabled;
        }
        
        @Override
        public boolean isDeprecated() {
            return deprecated;
        }
        
        @Override
        public Class<?> getJavaType() {
            return javaType;
        }
        
        @Override
        public String toString() {
            return JavaClassNames.simpleClassName(this)+"["+getId()+
                (isDisabled() ? ";DISABLED" : "")+
                (isDeprecated() ? ";deprecated" : "")+
                "]";
        }
    }

    // TODO
//    public static class RegisteredBeanType extends AbstractRegisteredType {
//        
//    }
    
    public static class RegisteredSpecType extends AbstractRegisteredType {

        private TypeImplementation impl;
        
        public RegisteredSpecType(String symbolicName, String version, Class<?> javaType, TypeImplementation impl) {
            super(symbolicName, version, javaType);
            this.impl = impl;
        }

        public TypeImplementation getImplementation() {
            return impl;
        }
    }

    public static class TypeImplementation {
        final String format;
        final Object data;
        
        public TypeImplementation(String kind, Object data) {
            super();
            this.format = kind;
            this.data = data;
        }

        /** details of the implementation, if known;
         * this may be null if the relevant {@link PlanToSpecTransformer} was not declared when created,
         * but in general we should look to determine the kind as early as possible and use that
         * to retrieve the appropriate such transformer.
         */
        public String getFormat() {
            return format;
        }
        
        public Object getData() {
            return data;
        }
    }
    
    public static class JavaTypeImplementation extends TypeImplementation {
        public static final String FORMAT = "java";
        public JavaTypeImplementation(String javaType) {
            super(FORMAT, javaType);
        }
        public String getJavaType() { return (String)getData(); }
    }
    
//    // TODO remove, unless we want it
//    public static class CampYamlTypeImplementation extends TypeImplementation {
//        public static final String FORMAT = "camp";
//        public CampYamlTypeImplementation(String javaType) {
//            super(FORMAT, javaType);
//        }
//        public String getCampYaml() { return (String)getData(); }
//    }

    /** returns the implementation data for a spec if it is a string (e.g. plan yaml or java class name); else false */
    @Beta
    public static String getImplementationDataStringForSpec(RegisteredType item) {
        if (!(item instanceof RegisteredSpecType)) return null;
        Object data = ((RegisteredSpecType)item).getImplementation().getData();
        if (data instanceof String) return (String) data;
        return null;
    }
    
}
