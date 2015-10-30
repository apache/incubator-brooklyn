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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampConstants;
import org.apache.brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import org.apache.brooklyn.camp.spi.pdp.DeploymentPlan;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.resolve.ResolveUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.yaml.Yamls;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

//TODO-type-registry
public class CampUtils {

    public static List<EntitySpec<?>> createServiceSpecs(String plan, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        CampPlatform camp = getCampPlatform(loader.getManagementContext());

        AssemblyTemplate at = registerDeploymentPlan(plan, loader, camp);
        AssemblyTemplateInstantiator instantiator = getInstantiator(at);
        if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
            return ((AssemblyTemplateSpecInstantiator)instantiator).createServiceSpecs(at, camp, loader, encounteredTypes);
        } else {
            throw new IllegalStateException("Unable to instantiate YAML; incompatible instantiator "+instantiator+" for "+at);
        }
    }

    public static AssemblyTemplateInstantiator getInstantiator(AssemblyTemplate at) {
        try {
            return at.getInstantiator().newInstance();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public static AssemblyTemplate registerDeploymentPlan(String plan, BrooklynClassLoadingContext loader, CampPlatform camp) {
        BrooklynLoaderTracker.setLoader(loader);
        try {
            return camp.pdp().registerDeploymentPlan(new StringReader(plan));
        } finally {
            BrooklynLoaderTracker.unsetLoader(loader);
        }
    }

    public static PolicySpec<?> createPolicySpec(String yamlPlan, BrooklynClassLoadingContext loader, Set<String> encounteredCatalogTypes) {
        DeploymentPlan plan = makePlanFromYaml(loader.getManagementContext(), yamlPlan);

        //Would ideally re-use the PolicySpecResolver
        //but it is CAMP specific and there is no easy way to get hold of it.
        Object policies = checkNotNull(plan.getCustomAttributes().get(BasicBrooklynCatalog.POLICIES_KEY), "policy config");
        if (!(policies instanceof Iterable<?>)) {
            throw new IllegalStateException("The value of " + BasicBrooklynCatalog.POLICIES_KEY + " must be an Iterable.");
        }

        Object policy = Iterables.getOnlyElement((Iterable<?>)policies);

        return createPolicySpec(loader, policy, encounteredCatalogTypes);
    }

    @SuppressWarnings("unchecked")
    public static PolicySpec<?> createPolicySpec(BrooklynClassLoadingContext loader, Object policy, Set<String> encounteredCatalogTypes) {
        Map<String, Object> itemMap;
        if (policy instanceof String) {
            itemMap = ImmutableMap.<String, Object>of("type", policy);
        } else if (policy instanceof Map) {
            itemMap = (Map<String, Object>) policy;
        } else {
            throw new IllegalStateException("Policy expected to be string or map. Unsupported object type " + policy.getClass().getName() + " (" + policy.toString() + ")");
        }

        String versionedId = (String) checkNotNull(Yamls.getMultinameAttribute(itemMap, "policy_type", "policyType", "type"), "policy type");
        PolicySpec<? extends Policy> spec = ResolveUtils.resolveSpec(versionedId, loader, encounteredCatalogTypes);
        Map<String, Object> brooklynConfig = (Map<String, Object>) itemMap.get("brooklyn.config");
        if (brooklynConfig != null) {
            spec.configure(brooklynConfig);
        }
        return spec;
    }

    public static LocationSpec<?> createLocationSpec(String yamlPlan, BrooklynClassLoadingContext loader, Set<String> encounteredTypes) {
        DeploymentPlan plan = makePlanFromYaml(loader.getManagementContext(), yamlPlan);
        Object locations = checkNotNull(plan.getCustomAttributes().get(BasicBrooklynCatalog.LOCATIONS_KEY), "location config");
        if (!(locations instanceof Iterable<?>)) {
            throw new IllegalStateException("The value of " + BasicBrooklynCatalog.LOCATIONS_KEY + " must be an Iterable.");
        }

        Object location = Iterables.getOnlyElement((Iterable<?>)locations);

        return createLocationSpec(loader, location); 
    }

    @SuppressWarnings("unchecked")
    private static LocationSpec<?> createLocationSpec(BrooklynClassLoadingContext loader, Object location) {
        Map<String, Object> itemMap;
        if (location instanceof String) {
            itemMap = ImmutableMap.<String, Object>of("type", location);
        } else if (location instanceof Map) {
            itemMap = (Map<String, Object>) location;
        } else {
            throw new IllegalStateException("Location expected to be string or map. Unsupported object type " + location.getClass().getName() + " (" + location.toString() + ")");
        }

        String type = (String) checkNotNull(Yamls.getMultinameAttribute(itemMap, "location_type", "locationType", "type"), "location type");
        Map<String, Object> brooklynConfig = (Map<String, Object>) itemMap.get("brooklyn.config");
        return ResolveUtils.resolveLocationSpec(type, brooklynConfig, loader);
    }

    public static DeploymentPlan makePlanFromYaml(ManagementContext mgmt, String yaml) {
        CampPlatform camp = getCampPlatform(mgmt);
        return camp.pdp().parseDeploymentPlan(Streams.newReaderWithContents(yaml));
    }

    public static CampPlatform getCampPlatform(ManagementContext mgmt) {
        CampPlatform result = mgmt.getConfig().getConfig(BrooklynCampConstants.CAMP_PLATFORM);
        if (result!=null) {
            return result;
        } else {
            throw new IllegalStateException("No CAMP Platform is registered with this Brooklyn management context.");
        }
    }

}
