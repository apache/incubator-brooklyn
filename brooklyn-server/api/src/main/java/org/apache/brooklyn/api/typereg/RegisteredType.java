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

import java.util.Collection;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.Identifiable;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;

import com.google.common.annotations.Beta;

public interface RegisteredType extends Identifiable {
    
    @Override String getId();
    
    RegisteredTypeKind getKind();
    
    String getSymbolicName();
    String getVersion();

    Collection<OsgiBundleWithUrl> getLibraries();

    String getDisplayName();
    String getDescription();
    String getIconUrl();

    /** @return all declared supertypes or super-interfaces of this registered type,
     * consisting of a collection of {@link Class} or {@link RegisteredType}
     * <p>
     * This should normally include at least one {@link Class} object:
     * For beans, this should include the java type that the {@link BrooklynTypeRegistry} will create. 
     * For specs, this should refer to the {@link BrooklynObject} type that the created spec will point at 
     * (e.g. the concrete {@link Entity}, not the {@link EntitySpec}).
     * <p>
     * This may not necessarily return the most specific java class or classes;
     * such as if the concrete type is private and callers should know only about a particular public interface,
     * or if precise type details are unavailable and all that is known at creation is some higher level interface/supertype
     * (e.g. this may return {@link Entity} even though the spec points at a specific subclass,
     * for instance because the YAML has not yet been parsed or OSGi bundles downloaded).
     * <p>
     * This may include other registered types such as marker interfaces.
     */
    @Beta
    Set<Object> getSuperTypes();

    /**
     * @return True if the item has been deprecated (i.e. its use is discouraged)
     */
    boolean isDeprecated();
    
    /**
     * @return True if the item has been disabled (i.e. its use is forbidden, except for pre-existing apps)
     */
    boolean isDisabled();

    /** Alias words defined for this type */
    Set<String> getAliases();

    /** Tags attached to this item */
    Set<Object> getTags();
    
    /** @return implementation details, so that the framework can find a suitable {@link BrooklynTypePlanTransformer} 
     * which can then use this object to instantiate this type */
    TypeImplementationPlan getPlan();
    
    public interface TypeImplementationPlan {
        /** hint which {@link BrooklynTypePlanTransformer} instance(s) can be used, if known;
         * this may be null if the relevant transformer was not declared when created,
         * but in general we should look to determine the kind as early as possible 
         * and use that to retrieve the appropriate such transformer */
        String getPlanFormat();
        /** data for the implementation; may be more specific */
        Object getPlanData();
    }

}
