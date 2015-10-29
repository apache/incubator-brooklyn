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

import java.util.Set;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class CampCatalogUtils {

    public static AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, CatalogItem<?, ?> item, Set<String> parentEncounteredTypes) {
        // preferred way is to parse the yaml, to resolve references late;
        // the parsing on load is to populate some fields, but it is optional.
        // TODO messy for location and policy that we need brooklyn.{locations,policies} root of the yaml, but it works;
        // see related comment when the yaml is set, in addAbstractCatalogItems
        // (not sure if anywhere else relies on that syntax; if not, it should be easy to fix!)
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, item);
        Preconditions.checkNotNull(item.getCatalogItemType(), "catalog item type for "+item.getPlanYaml());

        Set<String> encounteredTypes;
        // symbolicName could be null if coming from the catalog parser where it tries to load before knowing the id
        if (item.getSymbolicName() != null) {
            encounteredTypes = ImmutableSet.<String>builder()
                    .addAll(parentEncounteredTypes)
                    .add(item.getSymbolicName())
                    .build();
        } else {
            encounteredTypes = parentEncounteredTypes;
        }

        AbstractBrooklynObjectSpec<?, ?> spec;
        switch (item.getCatalogItemType()) {
            case TEMPLATE:
            case ENTITY:
                spec = CampUtils.createRootServiceSpec(item.getPlanYaml(), loader, encounteredTypes);
                break;
            case LOCATION: 
                spec = CampUtils.createLocationSpec(item.getPlanYaml(), loader, encounteredTypes);
                break;
            case POLICY: 
                spec = CampUtils.createPolicySpec(item.getPlanYaml(), loader, encounteredTypes);
                break;
            default:
                throw new IllegalStateException("Unknown CI Type "+item.getCatalogItemType()+" for "+item.getPlanYaml());
        }

        ((AbstractBrooklynObjectSpec<?, ?>)spec).catalogItemId(item.getId());

        if (Strings.isBlank( ((AbstractBrooklynObjectSpec<?, ?>)spec).getDisplayName() ))
            ((AbstractBrooklynObjectSpec<?, ?>)spec).displayName(item.getDisplayName());

        return spec;
    }

    public static CampPlatform getCampPlatform(ManagementContext mgmt) {
        return CampUtils.getCampPlatform(mgmt);
    }

}
