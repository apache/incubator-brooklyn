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

import static com.google.common.base.Preconditions.checkNotNull;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampConstants;
import io.brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;
import io.brooklyn.camp.spi.pdp.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.catalog.CatalogItem.CatalogBundle;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.catalog.internal.CatalogItemBuilder;
import brooklyn.catalog.internal.CatalogItemDtoAbstract;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Application;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.internal.EntityManagementUtils;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.plan.PlanToSpecCreator;
import brooklyn.plan.SpecCreatorFactory;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;
import brooklyn.util.yaml.Yamls;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;

public class CampPlanToSpecCreator implements PlanToSpecCreator {
    private static final Logger log = LoggerFactory.getLogger(CampPlanToSpecCreator.class);

    private static final String POLICIES_KEY = "brooklyn.policies";

    @Override
    public String getName() {
        return SpecCreatorFactory.YAML_CAMP_PLAN_TYPE;
    }


    @Override
    public boolean accepts(String mime) {
        return SpecCreatorFactory.YAML_CAMP_PLAN_TYPE.equals(mime);
    }

    @Override
    public AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, Reader yaml, BrooklynClassLoadingContext loader) {
        Preconditions.checkNotNull(mgmt, "mgmt");
        Preconditions.checkNotNull(mgmt, "yaml");
        Preconditions.checkNotNull(mgmt, "loader");

        CampPlatform camp = getCampPlatform(mgmt);
        DeploymentPlan plan = parseDeploymentPlan(camp, yaml);
        return createSpec(mgmt, plan, loader);
    }

    @Override
    public CatalogItemDtoAbstract<?, ?> createCatalogItem(ManagementContext mgmt, Reader input) {
        String yaml = readStream(input);
        DeploymentPlan plan = parseDeploymentPlan(getCampPlatform(mgmt), new StringReader(yaml));

        @SuppressWarnings("rawtypes")
        Maybe<Map> possibleCatalog = plan.getCustomAttribute("brooklyn.catalog", Map.class, true);
        MutableMap<String, Object> catalog = MutableMap.of();
        if (possibleCatalog.isPresent()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> catalog2 = (Map<String, Object>) possibleCatalog.get();
            catalog.putAll(catalog2);
        }

        Collection<CatalogBundle> libraries = Collections.emptyList();
        Maybe<Object> possibleLibraries = catalog.getMaybe("libraries");
        if (possibleLibraries.isAbsent()) possibleLibraries = catalog.getMaybe("brooklyn.libraries");
        if (possibleLibraries.isPresentAndNonNull()) {
            if (!(possibleLibraries.get() instanceof Collection))
                throw new IllegalArgumentException("Libraries should be a list, not "+possibleLibraries.get());
            libraries = CatalogItemDtoAbstract.parseLibraries((Collection<?>) possibleLibraries.get());
        }

        final String id = (String) catalog.getMaybe("id").orNull();
        final String version = Strings.toString(catalog.getMaybe("version").orNull());
        final String symbolicName = (String) catalog.getMaybe("symbolicName").orNull();
        final String name = (String) catalog.getMaybe("name").orNull();
        final String displayName = (String) catalog.getMaybe("displayName").orNull();
        final String description = (String) catalog.getMaybe("description").orNull();
        final String iconUrl = (String) catalog.getMaybe("iconUrl").orNull();
        final String iconUrlUnderscore = (String) catalog.getMaybe("icon_url").orNull();

        if ((Strings.isNonBlank(id) || Strings.isNonBlank(symbolicName)) && 
                Strings.isNonBlank(displayName) &&
                Strings.isNonBlank(name) && !name.equals(displayName)) {
            log.warn("Name property will be ignored due to the existence of displayName and at least one of id, symbolicName");
        }

        final String catalogSymbolicName;
        if (Strings.isNonBlank(symbolicName)) {
            catalogSymbolicName = symbolicName;
        } else if (Strings.isNonBlank(id)) {
            if (Strings.isNonBlank(id) && CatalogUtils.looksLikeVersionedId(id)) {
                catalogSymbolicName = CatalogUtils.getIdFromVersionedId(id);
            } else {
                catalogSymbolicName = id;
            }
        } else if (Strings.isNonBlank(name)) {
            catalogSymbolicName = name;
        } else if (Strings.isNonBlank(plan.getName())) {
            catalogSymbolicName = plan.getName();
        } else if (plan.getServices().size()==1) {
            Service svc = Iterables.getOnlyElement(plan.getServices());
            if (Strings.isBlank(svc.getServiceType())) {
                throw new IllegalStateException("CAMP service type not expected to be missing for " + svc);
            }
            catalogSymbolicName = svc.getServiceType();
        } else {
            log.error("Can't infer catalog item symbolicName from the following plan:\n" + yaml);
            throw new IllegalStateException("Can't infer catalog item symbolicName from catalog item description");
        }

        final String catalogVersion;
        if (CatalogUtils.looksLikeVersionedId(id)) {
            catalogVersion = CatalogUtils.getVersionFromVersionedId(id);
            if (version != null  && !catalogVersion.equals(version)) {
                throw new IllegalArgumentException("Discrepency between version set in id " + catalogVersion + " and version property " + version);
            }
        } else if (Strings.isNonBlank(version)) {
            catalogVersion = version;
        } else {
            log.warn("No version specified for catalog item " + catalogSymbolicName + ". Using default value.");
            catalogVersion = null;
        }

        final String catalogDisplayName;
        if (Strings.isNonBlank(displayName)) {
            catalogDisplayName = displayName;
        } else if (Strings.isNonBlank(name)) {
            catalogDisplayName = name;
        } else if (Strings.isNonBlank(plan.getName())) {
            catalogDisplayName = plan.getName();
        } else {
            catalogDisplayName = null;
        }

        final String catalogDescription;
        if (Strings.isNonBlank(description)) {
            catalogDescription = description;
        } else if (Strings.isNonBlank(plan.getDescription())) {
            catalogDescription = plan.getDescription();
        } else {
            catalogDescription = null;
        }

        final String catalogIconUrl;
        if (Strings.isNonBlank(iconUrl)) {
            catalogIconUrl = iconUrl;
        } else if (Strings.isNonBlank(iconUrlUnderscore)) {
            catalogIconUrl = iconUrlUnderscore;
        } else {
            catalogIconUrl = null;
        }

        CatalogUtils.installLibraries(mgmt, libraries);

        String versionedId = CatalogUtils.getVersionedId(catalogSymbolicName, catalogVersion);
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, versionedId, libraries);
        AbstractBrooklynObjectSpec<?, ?> spec = createSpec(mgmt, plan, loader);

        CatalogItemDtoAbstract<?, ?> dto = createItemBuilder(spec, catalogSymbolicName, catalogVersion)
            .libraries(libraries)
            .displayName(catalogDisplayName)
            .description(catalogDescription)
            .iconUrl(catalogIconUrl)
            .plan(yaml)
            .build();

        dto.setManagementContext((ManagementContextInternal) mgmt);
        return dto;
    }

    private String readStream(Reader yaml) {
        try {
            return CharStreams.toString(yaml);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            Streams.closeQuietly(yaml);
        }
    }

    private static DeploymentPlan parseDeploymentPlan(CampPlatform camp, Reader yaml) {
        return camp.pdp().parseDeploymentPlan(yaml);
    }
    
    private static AssemblyTemplateInstantiator newInstantiator(AssemblyTemplate at) {
        try {
            return at.getInstantiator().newInstance();
        } catch (InstantiationException e) {
            throw Exceptions.propagate(e);
        } catch (IllegalAccessException e) {
            throw Exceptions.propagate(e);
        }
    }

    private static AssemblyTemplate registerDeploymentPlan(CampPlatform camp, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        BrooklynClassLoadingContext prev = BrooklynLoaderTracker.setLoader(loader);
        try {
            return camp.pdp().registerDeploymentPlan(plan);
        } finally {
            BrooklynLoaderTracker.unsetLoader(loader);
            if (prev != null) {
                BrooklynLoaderTracker.setLoader(prev);
            }
        }
    }

    private static CatalogItemBuilder<?> createItemBuilder(AbstractBrooklynObjectSpec<?, ?> spec, String itemId, String version) {
        if (spec instanceof EntitySpec) {
            if (EntityManagementUtils.isApplicationSpec((EntitySpec<?>)spec)) {
                return CatalogItemBuilder.newTemplate(itemId, version);
            } else {
                return CatalogItemBuilder.newEntity(itemId, version);
            }
        } else if (spec instanceof PolicySpec) {
            return CatalogItemBuilder.newPolicy(itemId, version);
        } else {
            throw new IllegalStateException("Unknown spec type " + spec.getClass().getName() + " (" + spec + ")");
        }
    }

    private static boolean isPolicyPlan(DeploymentPlan plan) {
        return plan.getCustomAttributes().containsKey(POLICIES_KEY);
    }

    private static AbstractBrooklynObjectSpec<?,?> createSpec(ManagementContext mgmt, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        if (isPolicyPlan(plan)) {
            return createPolicySpec(plan, loader);
        } else {
            return createEntitySpec(getCampPlatform(mgmt), plan, loader);
        }
    }

    private static EntitySpec<? extends Application> createEntitySpec(CampPlatform camp, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        AssemblyTemplate at = registerDeploymentPlan(camp, plan, loader);
        AssemblyTemplateInstantiator instantiator = newInstantiator(at);
        if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
            @SuppressWarnings("unchecked")
            EntitySpec<? extends Application> spec = (EntitySpec<? extends Application>) ((AssemblyTemplateSpecInstantiator) instantiator).createSpec(at, camp, loader, true);
            return spec;
        } else {
            throw new IllegalStateException("Unable to instantiate YAML; incompatible instantiator " + instantiator + " for " + at);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, SpecT> SpecT createPolicySpec(DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        //Would ideally re-use io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver.PolicySpecResolver
        //but it is CAMP specific and there is no easy way to get hold of it.
        Object policies = checkNotNull(plan.getCustomAttributes().get(POLICIES_KEY), "policy config");
        if (!(policies instanceof Iterable<?>)) {
            throw new IllegalStateException("The value of " + POLICIES_KEY + " must be an Iterable.");
        }

        Object policy = Iterables.getOnlyElement((Iterable<?>)policies);

        Map<String, Object> policyConfig;
        if (policy instanceof String) {
            policyConfig = ImmutableMap.<String, Object>of("type", policy);
        } else if (policy instanceof Map) {
            policyConfig = (Map<String, Object>) policy;
        } else {
            throw new IllegalStateException("Policy exepcted to be string or map. Unsupported object type " + policy.getClass().getName() + " (" + policy.toString() + ")");
        }

        String policyType = (String) checkNotNull(Yamls.getMultinameAttribute(policyConfig, "policy_type", "policyType", "type"), "policy type");
        Map<String, Object> brooklynConfig = (Map<String, Object>) policyConfig.get("brooklyn.config");
        PolicySpec<? extends Policy> spec = PolicySpec.create(loader.loadClass(policyType, Policy.class));
        if (brooklynConfig != null) {
            spec.configure(brooklynConfig);
        }
        return (SpecT) spec;
    }

    /**
     * @return the CAMP platform associated with a management context, if there is one.
     */
    public static CampPlatform getCampPlatform(ManagementContext mgmt) {
        CampPlatform result = mgmt.getConfig().getConfig(BrooklynCampConstants.CAMP_PLATFORM);
        if (result!=null) {
            return result;
        } else {
            throw new IllegalStateException("No CAMP Platform is registered with this Brooklyn management context.");
        }
    }

}
