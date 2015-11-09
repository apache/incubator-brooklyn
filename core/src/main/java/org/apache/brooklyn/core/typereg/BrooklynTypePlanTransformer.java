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

import java.util.List;
import java.util.ServiceLoader;

import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;

/**
 * Interface for use by schemes which with to be able to transform plans.
 * <p>
 * To add a new plan transformation scheme, simply create an implementation and declare it
 * as a java service (cf {@link ServiceLoader}).
 * <p>
 * Implementations may wish to extend {@link AbstractTypePlanTransformer} which simplifies the process.
 */
public interface BrooklynTypePlanTransformer extends ManagementContextInjectable {

    /** @return a code to identify type implementations created specifying the use of this plan transformer. */
    String getFormatCode();
    /** @return a display name for this transformer. */
    String getFormatName();
    /** @return a description for this transformer */
    String getFormatDescription();

    /** @return how appropriate is this transformer for the {@link RegisteredType#getPlan()} of the type;
     * 0 (or less) if not, 1 for absolutely, and in some autodetect cases a value between 0 and 1 indicate a ranking.
     * <p>
     * The framework guarantees arguments are nonnull, and that the {@link RegisteredType#getPlan()} is also not-null.
     * However the format in that plan may be null. */
    double scoreForType(RegisteredType type, RegisteredTypeLoadingContext context);
    /** Creates a new instance of the indicated type, or throws if not supported;
     * this method is used by the {@link BrooklynTypeRegistry} when it creates instances,
     * so implementations must respect the {@link RegisteredTypeKind} semantics and the {@link RegisteredTypeLoadingContext}
     * if they return an instance.
     * <p>
     * The framework guarantees this will only be invoked when {@link #scoreForType(RegisteredType, RegisteredTypeLoadingContext)} 
     * has returned a positive value.
     * <p>
     * Implementations should either return null or throw {@link UnsupportedTypePlanException} 
     * if the {@link RegisteredType#getPlan()} is not supported. */
    Object create(RegisteredType type, RegisteredTypeLoadingContext context);
    
    double scoreForTypeDefinition(String formatCode, Object catalogData);
    List<RegisteredType> createFromTypeDefinition(String formatCode, Object catalogData);

}
