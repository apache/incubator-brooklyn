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
package org.apache.brooklyn.api.objs;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.Feed;

import com.google.common.annotations.Beta;
import com.google.common.base.CaseFormat;

@Beta
public enum BrooklynObjectType {
    // these are correctly type-checked by i can't tell how to get java not to warn!
    @SuppressWarnings("unchecked") ENTITY(Entity.class, EntitySpec.class, "entities"),
    @SuppressWarnings("unchecked") LOCATION(Location.class, LocationSpec.class, "locations"),
    @SuppressWarnings("unchecked") POLICY(Policy.class, PolicySpec.class, "policies"),
    @SuppressWarnings("unchecked") ENRICHER(Enricher.class, EnricherSpec.class, "enrichers"),
    FEED(Feed.class, null, "feeds"),
    CATALOG_ITEM(CatalogItem.class, null, "catalog"),
    UNKNOWN(null, null, "unknown");
    
    private final Class<? extends BrooklynObject> interfaceType;
    private final Class<? extends AbstractBrooklynObjectSpec<?,?>> specType;
    private final String subPathName;
    
    <T extends BrooklynObject,K extends AbstractBrooklynObjectSpec<T,K>> BrooklynObjectType(Class<T> interfaceType, Class<K> specType, String subPathName) {
        this.interfaceType = interfaceType;
        this.specType = specType;
        this.subPathName = subPathName;
    }
    public String toCamelCase() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    public String getSubPathName() {
        return subPathName;
    }
    
    public Class<? extends BrooklynObject> getInterfaceType() {
        return interfaceType;
    }
    
    public Class<? extends AbstractBrooklynObjectSpec<?, ?>> getSpecType() {
        return specType;
    }
    
    public static BrooklynObjectType of(BrooklynObject instance) {
        for (BrooklynObjectType t: values()) {
            if (t.getInterfaceType()!=null && t.getInterfaceType().isInstance(instance))
                return t;
        }
        return UNKNOWN;
    }
}