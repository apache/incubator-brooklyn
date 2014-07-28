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
package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;
import io.brooklyn.camp.spi.pdp.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.catalog.internal.CatalogItemBuilder;
import brooklyn.catalog.internal.CatalogItemDtoAbstract;
import brooklyn.catalog.internal.CatalogLibrariesDto;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.internal.SpecCreator;
import brooklyn.internal.SpecCreatorFactory;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;

public class YamlSpecCreator implements SpecCreator {
    @Override
    public boolean supports(String mime) {
        return SpecCreatorFactory.YAML_CAMP_PLAN_TYPE.equals(mime);
    }

    @Override
    public EntitySpec<?> createSpec(ManagementContext mgmt, Object obj, BrooklynClassLoadingContext loader) {
        Preconditions.checkNotNull(mgmt);
        Preconditions.checkNotNull(obj);

        if (loader == null) {
            loader = getJavaLoader(mgmt);
        }

        CampPlatform camp = getCampPlatform(mgmt).get();
        String yaml = readInput(obj);

        DeploymentPlan plan = createPlan(camp, yaml);
        return instantiateTemplate(camp, plan, loader);
    }


    @Override
    public CatalogItemDtoAbstract<?, ?> createCatalogItem(ManagementContext mgmt, Object obj) {
        Preconditions.checkNotNull(mgmt);
        Preconditions.checkNotNull(obj);

        CampPlatform camp = getCampPlatform(mgmt).get();
        String yaml = readInput(obj);

        DeploymentPlan plan = createPlan(camp, yaml);
        return getCatalogItem(mgmt, plan, yaml);
    }

    private String readInput(Object obj) {
        if (obj instanceof Reader) {
            try {
                return CharStreams.toString((Reader)obj);
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            }
        } else if (obj instanceof String) {
            return (String)obj;
        } else {
            throw new IllegalArgumentException("Unexpected object type " + obj.getClass() + " cannot be transformed to Reader");
        }
    }

    private JavaBrooklynClassLoadingContext getJavaLoader(ManagementContext mgmt) {
        return new JavaBrooklynClassLoadingContext(mgmt, Thread.currentThread().getContextClassLoader());
    }

    private DeploymentPlan createPlan(CampPlatform camp, String yaml) {
        return camp.pdp().parseDeploymentPlan(new StringReader(yaml));
    }

    private EntitySpec<?> instantiateTemplate(CampPlatform camp, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        BrooklynLoaderTracker.setLoader(loader);
        try {
            return instantiateTemplate(camp, plan);
        } finally {
            BrooklynLoaderTracker.unsetLoader(loader);
        }
    }

    
    private EntitySpec<?> instantiateTemplate(CampPlatform camp, DeploymentPlan plan) {
        AssemblyTemplate at = registerDeploymentPlan(camp, plan);

        try {
            AssemblyTemplateInstantiator instantiator = at.getInstantiator().newInstance();
            if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
                return ((AssemblyTemplateSpecInstantiator)instantiator).createSpec(at, camp);
            }
            throw new IllegalStateException("Unable to instantiate YAML; incompatible instantiator "+instantiator+" for "+at);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    private CatalogItemDtoAbstract<?, ?> getCatalogItem(ManagementContext mgmt, DeploymentPlan plan, String yaml) {
        CatalogLibrariesDto libraries = null;

        @SuppressWarnings("rawtypes")
        Maybe<Map> possibleCatalog = plan.getCustomAttribute("brooklyn.catalog", Map.class, true);
        MutableMap<String, Object> catalog = MutableMap.of();
        if (possibleCatalog.isPresent()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog2 = (Map<String, Object>) possibleCatalog.get();
            catalog.putAll(catalog2);
        }

        Maybe<Object> possibleLibraries = catalog.getMaybe("libraries");
        if (possibleLibraries.isAbsent()) possibleLibraries = catalog.getMaybe("brooklyn.libraries");
        if (possibleLibraries.isPresentAndNonNull()) {
            if (!(possibleLibraries.get() instanceof List))
                throw new IllegalArgumentException("Libraries should be a list, not "+possibleLibraries.get());
            libraries = CatalogLibrariesDto.fromList((List<?>) possibleLibraries.get());
        }

        // TODO clear up the abundance of id, name, registered type, java type
        String registeredTypeName = (String) catalog.getMaybe("id").orNull();
        if (Strings.isBlank(registeredTypeName))
            registeredTypeName = (String) catalog.getMaybe("name").orNull();
        // take name from plan if not specified in brooklyn.catalog section not supplied
        if (Strings.isBlank(registeredTypeName)) {
            registeredTypeName = plan.getName();
            if (Strings.isBlank(registeredTypeName)) {
                if (plan.getServices().size()==1) {
                    Service svc = Iterables.getOnlyElement(plan.getServices());
                    registeredTypeName = svc.getServiceType();
                }
            }
        }

        // TODO long-term:  support applications / templates, policies

        // build the catalog item from the plan (as CatalogItem<Entity> for now)
        CatalogItemBuilder builder = CatalogItemBuilder.newEntity();
        builder.registeredTypeName(registeredTypeName);
        builder.libraries(libraries);
        builder.name(plan.getName());
        builder.description(plan.getDescription());
        builder.plan(yaml);

        // and populate other fields
        Maybe<Object> name = catalog.getMaybe("name");
        if (name.isPresent()) builder.name((String)name.get());

        Maybe<Object> description = catalog.getMaybe("description");
        if (description.isPresent()) builder.description((String)description.get());

        Maybe<Object> iconUrl = catalog.getMaybe("iconUrl");
        if (iconUrl.isAbsent()) iconUrl = catalog.getMaybe("icon_url");
        if (iconUrl.isPresent()) builder.iconUrl((String)iconUrl.get());

        return builder.build();
    }

    private AssemblyTemplate registerDeploymentPlan(CampPlatform camp, DeploymentPlan plan) {
        AssemblyTemplate at;
            at = camp.pdp().registerDeploymentPlan(plan);
        return at;
    }
    
    /** Returns the CAMP platform associated with a management context, if there is one. */
    public static Maybe<CampPlatform> getCampPlatform(ManagementContext mgmt) {
        CampPlatform result = mgmt.getConfig().getConfig(BrooklynCampConstants.CAMP_PLATFORM);
        if (result!=null) return Maybe.of(result);
        return Maybe.absent("No CAMP Platform is registered with this Brooklyn management context.");
    }
}
