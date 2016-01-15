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

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.spi.AbstractResource;
import org.apache.brooklyn.camp.spi.PlatformRootSummary;
import org.apache.brooklyn.camp.spi.collection.ResolvableLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTemplateBrooklynLookup<T extends AbstractResource>  extends AbstractBrooklynResourceLookup<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractTemplateBrooklynLookup.class);
    
    public AbstractTemplateBrooklynLookup(PlatformRootSummary root, ManagementContext bmc) {
        super(root, bmc);
    }

    @Override
    public T get(String id) {
        RegisteredType item = bmc.getTypeRegistry().get(id);
        if (item==null) {
            log.warn("Could not find item '"+id+"' in Brooklyn catalog; returning null");
            return null;
        }
        return adapt(item);
    }

    public abstract T adapt(RegisteredType item);

    protected ResolvableLink<T> newLink(CatalogItem<? extends Entity,EntitySpec<?>> li) {
        return newLink(li.getId(), li.getDisplayName());
    }

}
