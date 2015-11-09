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
package org.apache.brooklyn.core.catalog.internal;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.core.typereg.JavaClassNameTypePlanTransformer;
import org.apache.brooklyn.core.typereg.RegisteredTypeLoadingContexts;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.yaml.Yamls;

import com.google.common.collect.Iterables;

/**
 * For use in conjunction with {@link StaticTypePlanTransformer};
 * this will lookup an item by ID or in a map "type: id".
 * <p>
 * Should be removed when catalog is able to function using new-style lookups.
 */
public class TestToSpecTransformer implements PlanToSpecTransformer {

    private ManagementContext mgmt;

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        mgmt = managementContext;
    }

    @Override
    public String getShortDescription() {
        return "test";
    }

    @Override
    public boolean accepts(String planType) {
        return "test".equals(planType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public EntitySpec<? extends Application> createApplicationSpec(String plan) throws PlanNotRecognizedException {
        return (EntitySpec<? extends Application>) getSpec(plan, null, MutableSet.<String>of());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item, Set<String> encounteredTypes) 
            throws PlanNotRecognizedException {
        return (SpecT) getSpecFromPlan(item.getPlanYaml(), item, encounteredTypes);
    }

    @SuppressWarnings("unchecked")
    private AbstractBrooklynObjectSpec<?,?> getSpecFromPlan(String plan, CatalogItem<?,?> item, Set<String> encounteredTypes) {
        if (plan != null) {
            Object planRaw = Yamls.parseAll(plan).iterator().next();
            if (planRaw instanceof String) {
                return getSpec((String)planRaw, item, encounteredTypes);
            } else if (planRaw instanceof Map) {
                // The catalog parser assumes it's dealing with CAMP specs so will helpfully
                // prepend "type: " if it's an inline item.
                Map<?, ?> planMap = (Map<?, ?>)planRaw;
                if (planMap.size()==1 && planMap.containsKey("services")) {
                    planMap = Iterables.getOnlyElement( (Iterable<Map<?,?>>)(planMap.get("services")) );
                }
                if (planMap.size()==1 && planMap.containsKey("type"))
                    return getSpec((String)(planMap.get("type")), item, encounteredTypes);
            }
        }
        throw notRecognized("unknown format "+plan);
    }

    private AbstractBrooklynObjectSpec<?,?> getSpec(String typeName, CatalogItem<?,?> item, Set<String> encounteredTypes) {
        if (typeName == null) {
            throw notRecognized("null type "+typeName);
        }

        RegisteredType type = mgmt.getTypeRegistry().get(typeName);
        if (type==null) {
            AbstractBrooklynObjectSpec<?,?> spec = StaticTypePlanTransformer.get(typeName);
            if (spec!=null) return spec;
            
            throw notRecognized("no type "+typeName);
        }
        
        return mgmt.getTypeRegistry().createSpecFromPlan(
            JavaClassNameTypePlanTransformer.FORMAT,
            typeName, RegisteredTypeLoadingContexts.loader(CatalogUtils.newClassLoadingContext(mgmt, item)), null);
    }

    private PlanNotRecognizedException notRecognized(String message) {
        return new PlanNotRecognizedException("Not recognized as registered spec: "+message);
    }

}
