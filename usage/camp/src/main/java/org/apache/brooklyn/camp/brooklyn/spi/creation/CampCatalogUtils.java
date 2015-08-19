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

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
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
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal.ConfigurationSupportInternal;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class CampCatalogUtils {

    public static AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, CatalogItem<?, ?> item) {
        // preferred way is to parse the yaml, to resolve references late;
        // the parsing on load is to populate some fields, but it is optional.
        // TODO messy for location and policy that we need brooklyn.{locations,policies} root of the yaml, but it works;
        // see related comment when the yaml is set, in addAbstractCatalogItems
        // (not sure if anywhere else relies on that syntax; if not, it should be easy to fix!)
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, item);
        DeploymentPlan plan = makePlanFromYaml(loader.getManagementContext(), item.getPlanYaml());
        Preconditions.checkNotNull(item.getCatalogItemType(), "catalog item type for "+plan);
        AbstractBrooklynObjectSpec<?, ?> spec;
        switch (item.getCatalogItemType()) {
            case TEMPLATE:
            case ENTITY:
                spec = createEntitySpec(item.getSymbolicName(), plan, loader);
                break;
            case LOCATION: 
                spec = createLocationSpec(plan, loader);
                break;
            case POLICY: 
                spec = createPolicySpec(item.getSymbolicName(), plan, loader);
                break;
            default:
                throw new IllegalStateException("Unknown CI Type "+item.getCatalogItemType()+" for "+plan);
        }

        ((AbstractBrooklynObjectSpec<?, ?>)spec).catalogItemId(item.getId());

        if (Strings.isBlank( ((AbstractBrooklynObjectSpec<?, ?>)spec).getDisplayName() ))
            ((AbstractBrooklynObjectSpec<?, ?>)spec).displayName(item.getDisplayName());

        return spec;
    }

    private static EntitySpec<?> createEntitySpec(String symbolicName, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        CampPlatform camp = getCampPlatform(loader.getManagementContext());

        // TODO should not register new AT each time we instantiate from the same plan; use some kind of cache
        AssemblyTemplate at;
        BrooklynLoaderTracker.setLoader(loader);
        try {
            at = camp.pdp().registerDeploymentPlan(plan);
        } finally {
            BrooklynLoaderTracker.unsetLoader(loader);
        }

        try {
            AssemblyTemplateInstantiator instantiator = at.getInstantiator().newInstance();
            if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
                return ((AssemblyTemplateSpecInstantiator)instantiator).createNestedSpec(at, camp, loader, 
                    getInitialEncounteredSymbol(symbolicName));
            }
            throw new IllegalStateException("Unable to instantiate YAML; incompatible instantiator "+instantiator+" for "+at);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    private static MutableSet<String> getInitialEncounteredSymbol(String symbolicName) {
        return symbolicName==null ? MutableSet.<String>of() : MutableSet.of(symbolicName);
    }

    private static PolicySpec<?> createPolicySpec(String symbolicName, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        return createPolicySpec(plan, loader, getInitialEncounteredSymbol(symbolicName));
    }

    private static PolicySpec<?> createPolicySpec(DeploymentPlan plan, BrooklynClassLoadingContext loader, Set<String> encounteredCatalogTypes) {
        //Would ideally re-use io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityDecorationResolver.PolicySpecResolver
        //but it is CAMP specific and there is no easy way to get hold of it.
        Object policies = checkNotNull(plan.getCustomAttributes().get(BasicBrooklynCatalog.POLICIES_KEY), "policy config");
        if (!(policies instanceof Iterable<?>)) {
            throw new IllegalStateException("The value of " + BasicBrooklynCatalog.POLICIES_KEY + " must be an Iterable.");
        }

        Object policy = Iterables.getOnlyElement((Iterable<?>)policies);

        return createPolicySpec(loader, policy, encounteredCatalogTypes);
    }

    @SuppressWarnings("unchecked")
    private static PolicySpec<?> createPolicySpec(BrooklynClassLoadingContext loader, Object policy, Set<String> encounteredCatalogTypes) {
        Map<String, Object> itemMap;
        if (policy instanceof String) {
            itemMap = ImmutableMap.<String, Object>of("type", policy);
        } else if (policy instanceof Map) {
            itemMap = (Map<String, Object>) policy;
        } else {
            throw new IllegalStateException("Policy expected to be string or map. Unsupported object type " + policy.getClass().getName() + " (" + policy.toString() + ")");
        }

        String versionedId = (String) checkNotNull(Yamls.getMultinameAttribute(itemMap, "policy_type", "policyType", "type"), "policy type");
        PolicySpec<? extends Policy> spec;
        CatalogItem<?, ?> policyItem = CatalogUtils.getCatalogItemOptionalVersion(loader.getManagementContext(), versionedId);
        if (policyItem != null && !encounteredCatalogTypes.contains(policyItem.getSymbolicName())) {
            if (policyItem.getCatalogItemType() != CatalogItemType.POLICY) {
                throw new IllegalStateException("Non-policy catalog item in policy context: " + policyItem);
            }
            //TODO re-use createSpec
            BrooklynClassLoadingContext itemLoader = CatalogUtils.newClassLoadingContext(loader.getManagementContext(), policyItem);
            if (policyItem.getPlanYaml() != null) {
                DeploymentPlan plan = makePlanFromYaml(loader.getManagementContext(), policyItem.getPlanYaml());
                encounteredCatalogTypes.add(policyItem.getSymbolicName());
                return createPolicySpec(plan, itemLoader, encounteredCatalogTypes);
            } else if (policyItem.getJavaType() != null) {
                spec = PolicySpec.create((Class<Policy>)itemLoader.loadClass(policyItem.getJavaType()));
            } else {
                throw new IllegalStateException("Invalid policy item - neither yaml nor javaType: " + policyItem);
            }
        } else {
            spec = PolicySpec.create(loader.loadClass(versionedId, Policy.class));
        }
        Map<String, Object> brooklynConfig = (Map<String, Object>) itemMap.get("brooklyn.config");
        if (brooklynConfig != null) {
            spec.configure(brooklynConfig);
        }
        return spec;
    }

    private static LocationSpec<?> createLocationSpec(DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        // See #createPolicySpec; this impl is modeled on that.
        // spec.catalogItemId is set by caller
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
        Maybe<Class<? extends Location>> javaClass = loader.tryLoadClass(type, Location.class);
        if (javaClass.isPresent()) {
            LocationSpec<?> spec = LocationSpec.create(javaClass.get());
            if (brooklynConfig != null) {
                spec.configure(brooklynConfig);
            }
            return spec;
        } else {
            Maybe<Location> loc = loader.getManagementContext().getLocationRegistry().resolve(type, false, brooklynConfig);
            if (loc.isPresent()) {
                // TODO extensions?
                Map<String, Object> locConfig = ((ConfigurationSupportInternal)loc.get().config()).getBag().getAllConfig();
                Class<? extends Location> locType = loc.get().getClass();
                Set<Object> locTags = loc.get().tags().getTags();
                String locDisplayName = loc.get().getDisplayName();
                return LocationSpec.create(locType)
                        .configure(locConfig)
                        .displayName(locDisplayName)
                        .tags(locTags);
            } else {
                throw new IllegalStateException("No class or resolver found for location type "+type);
            }
        }
    }

    private static DeploymentPlan makePlanFromYaml(ManagementContext mgmt, String yaml) {
        CampPlatform camp = getCampPlatform(mgmt);
        return camp.pdp().parseDeploymentPlan(Streams.newReaderWithContents(yaml));
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
