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
import java.util.concurrent.ConcurrentHashMap;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.yaml.Yamls;

/**
 * Resolves previously registered specs by id.
 * First create a spec and register it, keeping the returned ID:
 * <pre> {@code
 * String specId = TestToSpecTransformer.registerSpec(EntitySpec.create(BasicEntity.class));
 * }</pre>
 *
 * Then build a plan to be resolved such as:
 * <pre> {@code
 *  brooklyn.catalog:
 *    id: test.inputs
 *    version: 0.0.1
 *    item: <specId>
 * } </pre>
 */
public class TestToSpecTransformer implements PlanToSpecTransformer {
    private static final Map<String, AbstractBrooklynObjectSpec<?, ?>> REGISTERED_SPECS = new ConcurrentHashMap<>();
    public static String registerSpec(AbstractBrooklynObjectSpec<?, ?> spec) {
        String id = Identifiers.makeRandomId(10);
        REGISTERED_SPECS.put(id, spec);
        return id;
    }

    @Override
    public void setManagementContext(ManagementContext managementContext) {
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
        return (EntitySpec<? extends Application>) getSpec(plan);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item, Set<String> encounteredTypes) 
            throws PlanNotRecognizedException {
        return (SpecT) getSpecFromPlan(item.getPlanYaml());
    }

    private AbstractBrooklynObjectSpec<?,?> getSpecFromPlan(String plan) {
        if (plan != null) {
            Object planRaw = Yamls.parseAll(plan).iterator().next();
            if (planRaw instanceof String) {
                return getSpec((String)planRaw);
            } else if (planRaw instanceof Map) {
                // The catalog parser assumes it's dealing with CAMP specs so will helpfully
                // prepend "type: " if it's an inline item.
                return getSpec((String)((Map<?, ?>)planRaw).get("type"));
            } else {
                throw notRecognized();
            }
        } else {
            throw notRecognized();
        }
    }

    private AbstractBrooklynObjectSpec<?,?> getSpec(String plan) {
        if (plan == null) {
            throw notRecognized();
        }
        AbstractBrooklynObjectSpec<?, ?> spec = REGISTERED_SPECS.get(plan);
        if (spec != null) {
            return spec;
        } else {
            throw notRecognized();
        }
    }

    private PlanNotRecognizedException notRecognized() {
        return new PlanNotRecognizedException("Not recognized as registered spec");
    }

}
