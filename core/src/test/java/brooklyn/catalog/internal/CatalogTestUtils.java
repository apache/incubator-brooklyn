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
package brooklyn.catalog.internal;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;

import brooklyn.entity.proxying.EntitySpec;

import com.google.common.annotations.Beta;

public class CatalogTestUtils {

    /** creates entity spec with the java type of a catalog item;
     * would be nice to have this in {@link CatalogUtils},
     * but the logic for parsing the yaml is buried in camp code,
     * so it's a little bit hard to make this a first class method.
     * <p>
     * (this impl ignores many things, including config and location.)
     */
    @Beta
    public static EntitySpec<?> createEssentialEntitySpec(ManagementContext mgmt, CatalogItem<?, ?> catalogItem) {
        BrooklynClassLoadingContext loader = CatalogUtils.newClassLoadingContext(mgmt, catalogItem);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        EntitySpec<?> spec = EntitySpec.create( (Class)loader.loadClass(catalogItem.getJavaType()) );
        spec.catalogItemId(catalogItem.getId());
        return spec;
    }

}
