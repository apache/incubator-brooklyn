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
import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;


public interface BrooklynTypeRegistry {

    public enum RegisteredTypeKind {
        /** a registered type which will create an {@link AbstractBrooklynObjectSpec} (e.g. {@link EntitySpec}) 
         * for the type registered (e.g. the {@link Entity} instance) */
        SPEC,
        /** a registered type which will create the java type described */
        BEAN 
        // note: additional kinds should have the visitor in core/RegisteredTypeKindVisitor updated
        // to flush out all places which want to implement support for all kinds 
    }
    
    Iterable<RegisteredType> getAll();
    Iterable<RegisteredType> getAll(Predicate<? super RegisteredType> filter);

    /** @return The item matching the given given 
     * {@link RegisteredType#getSymbolicName() symbolicName} 
     * and optionally {@link RegisteredType#getVersion()},
     * taking the best version if the version is null or a default marker,
     * returning null if no matches are found. */
    RegisteredType get(String symbolicName, String version);
    /** as {@link #get(String, String)} but the given string here 
     * is allowed to match any of:
     * <li>the given string as an ID including version (<code>"name:version"</code>) 
     * <li>the symbolic name unversioned, or
     * <li>an alias */
    RegisteredType get(String symbolicNameWithOptionalVersion);

    /** as {@link #get(String)} but further filtering for the additional context */
    public RegisteredType get(String symbolicNameOrAliasWithOptionalVersion, RegisteredTypeLoadingContext context);
    /** returns a wrapper of the result of {@link #get(String, RegisteredTypeLoadingContext)} 
     * including a detailed message if absent */
    public Maybe<RegisteredType> getMaybe(String symbolicNameOrAliasWithOptionalVersion, RegisteredTypeLoadingContext context);

    // NB the seemingly more correct generics <T,SpecT extends AbstractBrooklynObjectSpec<T,SpecT>> 
    // cause compile errors, not in Eclipse, but in maven (?) 
    // TODO do these belong here, or in a separate master TypePlanTransformer ?  see also BrooklynTypePlanTransformer
    @Beta
    <SpecT extends AbstractBrooklynObjectSpec<?,?>> SpecT createSpec(RegisteredType type, @Nullable RegisteredTypeLoadingContext optionalContext, @Nullable Class<SpecT> optionalSpecSuperType);
    @Beta
    <SpecT extends AbstractBrooklynObjectSpec<?,?>> SpecT createSpecFromPlan(@Nullable String planFormat, Object planData, @Nullable RegisteredTypeLoadingContext optionalContext, @Nullable Class<SpecT> optionalSpecSuperType);
    @Beta
    <T> T createBean(RegisteredType type, @Nullable RegisteredTypeLoadingContext optionalContext, @Nullable Class<T> optionalResultSuperType);
    @Beta
    <T> T createBeanFromPlan(String planFormat, Object planData, @Nullable RegisteredTypeLoadingContext optionalConstraint, @Nullable Class<T> optionalBeanSuperType);
    
}
