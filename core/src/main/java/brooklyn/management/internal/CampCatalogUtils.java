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
package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.basic.BrooklynObjectInternal.ConfigurationSupportInternal;
import brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogItem.CatalogItemType;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.catalog.internal.CatalogItemDo;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;
import brooklyn.util.yaml.Yamls;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;

public class CampCatalogUtils {
    public static <T, SpecT> SpecT createSpec(ManagementContext mgmt, CatalogItemDo<T, SpecT> itemDo) {
        // preferred way is to parse the yaml, to resolve references late;
        // the parsing on load is to populate some fields, but it is optional.
        // TODO messy for location and policy that we need brooklyn.{locations,policies} root of the yaml, but it works;
        // see related comment when the yaml is set, in addAbstractCatalogItems
        // (not sure if anywhere else relies on that syntax; if not, it should be easy to fix!)
        DeploymentPlan plan = makePlanFromYaml(mgmt, itemDo.getPlanYaml());
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, itemDo);
        SpecT spec;
        switch (itemDo.getCatalogItemType()) {
            case TEMPLATE:
            case ENTITY:
                spec = createEntitySpec(itemDo.getSymbolicName(), plan, loader);
                break;
            case POLICY:
                spec = createPolicySpec(itemDo.getSymbolicName(), plan, loader);
                break;
            case LOCATION:
                spec = createLocationSpec(plan, loader);
                break;
            default: throw new RuntimeException("Only entity, policy & location catalog items are supported. Unsupported catalog item type " + itemDo.getCatalogItemType());
        }
        ((AbstractBrooklynObjectSpec<?, ?>)spec).catalogItemId(itemDo.getId());
        
        if (Strings.isBlank( ((AbstractBrooklynObjectSpec<?, ?>)spec).getDisplayName() ))
            ((AbstractBrooklynObjectSpec<?, ?>)spec).displayName(itemDo.getDisplayName());
        
        return spec;
    }

    public static <T, SpecT> SpecT createSpec(String optionalId, CatalogItemType ciType, String yaml, BrooklynClassLoadingContext loader) {
        DeploymentPlan plan = makePlanFromYaml(loader.getManagementContext(), yaml);
        Preconditions.checkNotNull(ciType, "catalog item type for "+plan); 
        switch (ciType) {
        case TEMPLATE:
        case ENTITY: 
            return createEntitySpec(optionalId, plan, loader);
        case LOCATION: return createLocationSpec(plan, loader);
        case POLICY: return createPolicySpec(optionalId, plan, loader);
        }
        throw new IllegalStateException("Unknown CI Type "+ciType+" for "+plan);
    }
    
    @SuppressWarnings("unchecked")
    private static <T, SpecT> SpecT createEntitySpec(String symbolicName, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(loader.getManagementContext()).get();

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
                return (SpecT) ((AssemblyTemplateSpecInstantiator)instantiator).createNestedSpec(at, camp, loader, 
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

    private static <T, SpecT> SpecT createPolicySpec(String symbolicName, DeploymentPlan plan, BrooklynClassLoadingContext loader) {
        return createPolicySpec(plan, loader, getInitialEncounteredSymbol(symbolicName));
    }

    private static <T, SpecT> SpecT createPolicySpec(DeploymentPlan plan, BrooklynClassLoadingContext loader, Set<String> encounteredCatalogTypes) {
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
    private static <T, SpecT> SpecT createPolicySpec(BrooklynClassLoadingContext loader, Object policy, Set<String> encounteredCatalogTypes) {
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
        return (SpecT) spec;
    }
    
    private static <T, SpecT> SpecT createLocationSpec(DeploymentPlan plan, BrooklynClassLoadingContext loader) {
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
    private static <T, SpecT> SpecT createLocationSpec(BrooklynClassLoadingContext loader, Object location) {
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
            return (SpecT) spec;
        } else {
            Maybe<Location> loc = loader.getManagementContext().getLocationRegistry().resolve(type, false, brooklynConfig);
            if (loc.isPresent()) {
                // TODO extensions?
                Map<String, Object> locConfig = ((ConfigurationSupportInternal)loc.get().config()).getBag().getAllConfig();
                Class<? extends Location> locType = loc.get().getClass();
                Set<Object> locTags = loc.get().tags().getTags();
                String locDisplayName = loc.get().getDisplayName();
                return (SpecT) LocationSpec.create(locType)
                        .configure(locConfig)
                        .displayName(locDisplayName)
                        .tags(locTags);
            } else {
                throw new IllegalStateException("No class or resolver found for location type "+type);
            }
        }
    }

    private static DeploymentPlan makePlanFromYaml(ManagementContext mgmt, String yaml) {
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();
        return camp.pdp().parseDeploymentPlan(Streams.newReaderWithContents(yaml));
    }

}
