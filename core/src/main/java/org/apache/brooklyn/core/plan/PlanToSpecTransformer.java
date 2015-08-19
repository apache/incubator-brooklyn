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

/** Pluggable {@link ServiceLoader} interface for defferent plan-interpreters.
 * Implementations should take a plan and return an {@link EntitySpec}.
 */
@Beta
public interface PlanToSpecTransformer extends ManagementContextInjectable {
    
    /** Human-readable name for this transformer */
    String getName();
    
    /** whether this accepts the given MIME format */
    boolean accepts(String mime);
    
    /** creates an {@link EntitySpec} given a plan, according to the transformation rules this understands */
    <T extends Application> EntitySpec<T> createApplicationSpec(String plan);
    
    /** creates an object spec given a catalog item, according to the transformation rules this understands */
    <T,SpecT extends AbstractBrooklynObjectSpec<T, SpecT>> AbstractBrooklynObjectSpec<T, SpecT> createCatalogSpec(CatalogItem<T, SpecT> item);
    
}
