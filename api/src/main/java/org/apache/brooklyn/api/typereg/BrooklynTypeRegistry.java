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
package org.apache.brooklyn.api.typereg;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;

import com.google.common.base.Predicate;


public interface BrooklynTypeRegistry {

    public enum RegisteredTypeKind {
        /** a registered type which will create an {@link AbstractBrooklynObjectSpec} (e.g. {@link EntitySpec}) 
         * for the type registered (e.g. the {@link Entity} instance) */
        SPEC,
        // TODO
//        BEAN 
        
        // NB: additional kinds should have the Visitor in RegisteredTypes updated
    }
    
    Iterable<RegisteredType> getAll();
    Iterable<RegisteredType> getAll(Predicate<? super RegisteredType> alwaysTrue);
    
    RegisteredType get(String symbolicNameWithOptionalVersion, @Nullable RegisteredTypeKind kind, @Nullable Class<?> requiredSupertype);
    RegisteredType get(String symbolicName, String version, @Nullable RegisteredTypeKind kind, @Nullable Class<?> requiredSupertype);
    RegisteredType get(String symbolicName, String version);

    @SuppressWarnings("rawtypes")
    <T extends AbstractBrooklynObjectSpec> T createSpec(RegisteredType type, @Nullable Class<T> specKind);
    
    // TODO when we support beans
//    <T> T createBean(RegisteredType type, Class<T> superType);
    
}
