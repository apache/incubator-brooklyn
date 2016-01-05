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
package org.apache.brooklyn.camp.brooklyn.spi.lookup;

import java.util.ArrayList;
import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.PlatformRootSummary;
import org.apache.brooklyn.camp.spi.collection.ResolvableLink;
import org.apache.brooklyn.core.catalog.CatalogPredicates;

public class AssemblyTemplateBrooklynLookup extends AbstractTemplateBrooklynLookup<AssemblyTemplate> {

    public AssemblyTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public AssemblyTemplate adapt(RegisteredType item) {
        return AssemblyTemplate.builder().
                name(item.getDisplayName()).
                id(item.getId()).
                description(item.getDescription()).
                created(root.getCreated()).
                instantiator(BrooklynAssemblyTemplateInstantiator.class).
                build();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    // why can I not pass an EntitySpec<? extends Application> to    newLink(EntitySpec<?> spec)  ?
    // feels to me (alexheneveld) that `? extends Application` should be both covariant and contravariant to `?` ..
    // but it's not, so we introduce this conversion method
    protected ResolvableLink<AssemblyTemplate> newApplicationLink(CatalogItem<? extends Entity, EntitySpec<? extends Application>> li) {
        return super.newLink((CatalogItem)li);
    }
    
    @Override
    public List<ResolvableLink<AssemblyTemplate>> links() {
        Iterable<CatalogItem<Application,EntitySpec<? extends Application>>> l = bmc.getCatalog().getCatalogItems(CatalogPredicates.IS_TEMPLATE);
        List<ResolvableLink<AssemblyTemplate>> result = new ArrayList<ResolvableLink<AssemblyTemplate>>();
        for (CatalogItem<Application,EntitySpec<? extends Application>> li: l)
            result.add(newApplicationLink(li));
        return result;
    }
    
}
