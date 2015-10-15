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
package org.apache.brooklyn.core.resolve;

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal.ConfigurationSupportInternal;
import org.apache.brooklyn.util.guava.Maybe;

public class ResolveUtils {

    @SuppressWarnings("unchecked")
    public static PolicySpec<? extends Policy> resolveSpec(
            String versionedId,
            BrooklynClassLoadingContext loader,
            Set<String> encounteredCatalogTypes) {
        PolicySpec<? extends Policy> spec;
        CatalogItem<?, ?> policyItem = CatalogUtils.getCatalogItemOptionalVersion(loader.getManagementContext(), versionedId);
        if (policyItem != null && !encounteredCatalogTypes.contains(policyItem.getSymbolicName())) {
            if (policyItem.getCatalogItemType() != CatalogItemType.POLICY) {
                throw new IllegalStateException("Non-policy catalog item in policy context: " + policyItem);
            }
            @SuppressWarnings("rawtypes")
            CatalogItem rawItem = policyItem;
            spec = (PolicySpec<? extends Policy>) EntityManagementUtils.createCatalogSpec(loader.getManagementContext(), rawItem, encounteredCatalogTypes);
        } else {
            spec = PolicySpec.create(loader.loadClass(versionedId, Policy.class));
        }
        return spec;
    }

    public static LocationSpec<?> resolveLocationSpec(
            String type,
            Map<String, Object> brooklynConfig,
            BrooklynClassLoadingContext loader) {
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

}
