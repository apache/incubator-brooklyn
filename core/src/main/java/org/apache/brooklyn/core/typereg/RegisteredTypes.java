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

import org.apache.brooklyn.api.typereg.OsgiBundleWithUrl;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;

import com.google.common.annotations.Beta;

public class RegisteredTypes {

    /** Visitor adapter which can be used to ensure all kinds are supported */
    public static abstract class RegisteredTypeKindVisitor<T> {
        public T visit(RegisteredType type) {
            if (type==null) throw new NullPointerException("Registered type must not be null");
            if (type instanceof RegisteredSpecType) {
                visitSpec((RegisteredSpecType)type);
            }
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
        public Class<?> getJavaType() {
            return javaType;
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
        final String kind;
        final Object data;
        
        public TypeImplementation(String kind, Object data) {
            super();
            this.kind = kind;
            this.data = data;
        }

        /** details of the implementation, if known;
         * this may be null if the relevant {@link PlanToSpecTransformer} was not declared when created,
         * but in general we should look to determine the kind as early as possible and use that
         * to retrieve the appropriate such transformer.
         */
        public String getKind() {
            return kind;
        }
        
        public Object getData() {
            return data;
        }
    }
    
    public static class JavaTypeImplementation extends TypeImplementation {
        public static final String KIND = "java";
        public JavaTypeImplementation(String javaType) {
            super(KIND, javaType);
        }
        public String getJavaType() { return (String)getData(); }
    }
    
//    // TODO remove, unless we want it
//    public static class CampYamlTypeImplementation extends TypeImplementation {
//        public static final String KIND = "camp";
//        public CampYamlTypeImplementation(String javaType) {
//            super(KIND, javaType);
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
