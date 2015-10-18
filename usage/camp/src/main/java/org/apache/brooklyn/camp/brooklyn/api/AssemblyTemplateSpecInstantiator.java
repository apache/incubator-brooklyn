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
package org.apache.brooklyn.camp.brooklyn.api;

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;

public interface AssemblyTemplateSpecInstantiator extends AssemblyTemplateInstantiator {

    /**
     * Gets the single item returned by {@link #createServiceSpecs}
     * and wraps it in an Application if needed, applying top-level
     * attributes and locations to the root entity.
     */
    EntitySpec<? extends Application> createApplicationSpec(AssemblyTemplate template, CampPlatform platform, BrooklynClassLoadingContext loader);
    /** Returns specs for each item in the services list */
    List<EntitySpec<?>> createServiceSpecs(AssemblyTemplate template, CampPlatform platform, BrooklynClassLoadingContext itemLoader, Set<String> encounteredCatalogTypes);

}
