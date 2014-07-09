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

import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.ResolvableLink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;

public abstract class AbstractTemplateBrooklynLookup<T extends AbstractResource>  extends AbstractBrooklynResourceLookup<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractTemplateBrooklynLookup.class);
    
    public AbstractTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public T get(String id) {
        CatalogItem<?,?> item = bmc.getCatalog().getCatalogItem(id);
        if (item==null) {
            log.warn("Could not find item '"+id+"' in Brooklyn catalog; returning null");
            return null;
        }
        return adapt(item);
    }

    public abstract T adapt(CatalogItem<?,?> item);

    protected ResolvableLink<T> newLink(CatalogItem<? extends Entity,EntitySpec<?>> li) {
        return newLink(li.getId(), li.getName());
    }

}
