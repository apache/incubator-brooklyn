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
import org.apache.brooklyn.core.typereg.RegisteredTypes;

@Deprecated /** @deprecated since 0.9.0 use RegisteredType and CampResolver */
public class CampCatalogUtils {

    public static AbstractBrooklynObjectSpec<?, ?> createSpec(ManagementContext mgmt, CatalogItem<?, ?> item, Set<String> parentEncounteredTypes) {
        return CampResolver.createSpecFromFull(mgmt, RegisteredTypes.of(item), item.getCatalogItemJavaType(), parentEncounteredTypes, null);
    }
    
    public static CampPlatform getCampPlatform(ManagementContext mgmt) {
        return CampInternalUtils.getCampPlatform(mgmt);
    }

}
