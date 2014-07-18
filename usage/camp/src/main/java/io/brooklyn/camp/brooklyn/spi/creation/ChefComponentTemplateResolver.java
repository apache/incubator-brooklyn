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

import io.brooklyn.camp.spi.AbstractResource;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

public class ChefComponentTemplateResolver extends BrooklynComponentTemplateResolver {

    public ChefComponentTemplateResolver(BrooklynClassLoadingContext loader, ConfigBag attrs, AbstractResource optionalTemplate) {
        super(loader, attrs, optionalTemplate);
    }

    @Override
    protected String getBrooklynType() {
        return ChefEntity.class.getName();
    }

    // chef: items are not in catalog
    @Override
    public CatalogItem<Entity, EntitySpec<?>> getCatalogItem() {
        return null;
    }
    
    @Override
    protected <T extends Entity> void decorateSpec(EntitySpec<T> spec) {
        if (getDeclaredType().startsWith("chef:")) {
            spec.configure(ChefConfig.CHEF_COOKBOOK_PRIMARY_NAME, Strings.removeFromStart(getDeclaredType(), "chef:"));
        }
        
        super.decorateSpec(spec);
    }
    
}
