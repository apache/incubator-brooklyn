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
package org.apache.brooklyn.core.plan;

import java.util.ServiceLoader;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;

import com.google.common.annotations.Beta;

/** Pluggable {@link ServiceLoader} interface for different plan-interpreters,
 * that is, different ways of taking an application plan and returning an {@link EntitySpec},
 * and a {@link CatalogItem} and returning an {@link AbstractBrooklynObjectSpec}.
 */
@Beta
public interface PlanToSpecTransformer extends ManagementContextInjectable {
    
    /** A short, human-readable name for this transformer */
    String getShortDescription();
    
    /** whether this accepts the given plan type */
    // TODO determine semantics of plan type; for now, we try all using PlanToSpecFactory methods,
    // that's okay when there's just a very few, but we'll want something better if that grows
    @Beta
    boolean accepts(String planType);
    
    /** creates an {@link EntitySpec} given a complete plan textual description for a top-level application, 
     * according to the transformation rules this understands.
     * <p>
     * should throw {@link PlanNotRecognizedException} if not supported. */
    EntitySpec<? extends Application> createApplicationSpec(String plan) throws PlanNotRecognizedException;
    
    /** creates an object spec given a catalog item.
     * <p>
     * the catalog item might be known by type, or its source plan fragment text might be inspected and transformed.
     * implementations will typically look at the {@link CatalogItem#getCatalogItemType()} first.
     * <p>
     * should throw {@link PlanNotRecognizedException} if this transformer does not know what to do with the plan. */
    <T,SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item) throws PlanNotRecognizedException;
    
}
