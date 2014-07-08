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
package io.brooklyn.camp.brooklyn.spi.lookup;

import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import java.util.ArrayList;
import java.util.List;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;

public class PlatformComponentTemplateBrooklynLookup extends AbstractTemplateBrooklynLookup<PlatformComponentTemplate> {

    public PlatformComponentTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public PlatformComponentTemplate adapt(CatalogItem<?,?> item) {
        return PlatformComponentTemplate.builder().
                name(item.getName()).
                id(item.getId()).
                description(item.getDescription()).
                created(root.getCreated()).
                build();
    }

    @Override
    public List<ResolvableLink<PlatformComponentTemplate>> links() {
        Iterable<CatalogItem<Entity,EntitySpec<?>>> l = bmc.getCatalog().getCatalogItems(CatalogPredicates.IS_ENTITY);
        List<ResolvableLink<PlatformComponentTemplate>> result = new ArrayList<ResolvableLink<PlatformComponentTemplate>>();
        for (CatalogItem<Entity,EntitySpec<?>> li: l)
            result.add(newLink(li));
        return result;
    }

}
