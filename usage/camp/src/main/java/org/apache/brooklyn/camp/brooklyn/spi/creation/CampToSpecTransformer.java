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
package org.apache.brooklyn.camp.brooklyn.spi.creation;

import java.io.StringReader;
import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.core.plan.PlanNotRecognizedException;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CampToSpecTransformer implements PlanToSpecTransformer {

    public static final String YAML_CAMP_PLAN_TYPE = "org.apache.brooklyn.camp/yaml";

    private static final Logger log = LoggerFactory.getLogger(CampToSpecTransformer.class);
    
    private ManagementContext mgmt;

    @Override
    public String getShortDescription() {
        return "Brooklyn OASIS CAMP interpreter";
    }

    @Override
    public boolean accepts(String mime) {
        return YAML_CAMP_PLAN_TYPE.equals(mime);
    }

    @Override
    public EntitySpec<? extends Application> createApplicationSpec(String plan) {
        try {
            CampPlatform camp = CampCatalogUtils.getCampPlatform(mgmt);
            BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
            AssemblyTemplate at = CampUtils.registerDeploymentPlan(plan, loader, camp);
            AssemblyTemplateInstantiator instantiator = CampUtils.getInstantiator(at);
            if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
                return ((AssemblyTemplateSpecInstantiator) instantiator).createApplicationSpec(at, camp, loader);
            } else {
                // The unknown instantiator can create the app (Assembly), but not a spec.
                // Currently, all brooklyn plans should produce the above.
                if (at.getPlatformComponentTemplates()==null || at.getPlatformComponentTemplates().isEmpty()) {
                    if (at.getCustomAttributes().containsKey("brooklyn.catalog"))
                        throw new IllegalArgumentException("Unrecognized application blueprint format: expected an application, not a brooklyn.catalog");
                    throw new PlanNotRecognizedException("Unrecognized application blueprint format: no services defined");
                }
                // map this (expected) error to a nicer message
                throw new PlanNotRecognizedException("Unrecognized application blueprint format");
            }
        } catch (Exception e) {
            // TODO how do we figure out that the plan is not supported vs. invalid to wrap in a PlanNotRecognizedException?
            if (log.isDebugEnabled())
                log.debug("Failed to create entity from CAMP spec:\n" + plan, e);
            throw Exceptions.propagate(e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item, Set<String> encounteredTypes) {
        // Ignore old-style java type catalog items
        if (item.getPlanYaml() == null) {
            throw new PlanNotRecognizedException("Old style catalog item " + item + " not supported.");
        }
        if (encounteredTypes.contains(item.getSymbolicName())) {
            throw new IllegalStateException("Already encountered types " + encounteredTypes + " must not contain catalog item being resolver " + item.getSymbolicName());
        }

        // Not really clear what should happen to the top-level attributes, ignored until a good use case appears.
        return (SpecT) CampCatalogUtils.createSpec(mgmt, (CatalogItem)item, encounteredTypes);
    }

    @Override
    public void injectManagementContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

}
