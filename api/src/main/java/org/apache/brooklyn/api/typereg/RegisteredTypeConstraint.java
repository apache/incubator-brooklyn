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

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;

public interface RegisteredTypeConstraint {
    
    /** The kind required, if specified. */
    @Nullable public RegisteredTypeKind getKind();
    
    /** A java super-type or interface that should be filtered for; 
     * for specs, this refers to the target type, not the spec 
     * (eg {@link Entity} not {@link EntitySpec}). 
     * If nothing is specified, this returns {@link Object}'s class. */
    @Nonnull public Class<?> getJavaSuperType();
    
    /** encountered types, so that during resolution, 
     * if we have already attempted to resolve a given type,
     * the instantiator can avoid recursive cycles */
    @Nonnull public Set<String> getEncounteredTypes();
}
