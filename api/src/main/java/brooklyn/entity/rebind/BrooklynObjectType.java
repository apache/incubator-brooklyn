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
package brooklyn.entity.rebind;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.location.Location;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

import com.google.common.annotations.Beta;
import com.google.common.base.CaseFormat;

@Beta
public enum BrooklynObjectType {
    ENTITY(Entity.class, "entities"),
    LOCATION(Location.class, "locations"),
    POLICY(Policy.class, "policies"),
    ENRICHER(Enricher.class, "enrichers"),
    FEED(Feed.class, "feeds"),
    CATALOG_ITEM(CatalogItem.class, "catalog"),
    UNKNOWN(null, "unknown");
    
    private Class<? extends BrooklynObject> interfaceType;
    private final String subPathName;
    
    BrooklynObjectType(Class<? extends BrooklynObject> interfaceType, String subPathName) {
        this.interfaceType = interfaceType;
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
    
    public static BrooklynObjectType of(BrooklynObject instance) {
        for (BrooklynObjectType t: values()) {
            if (t.getInterfaceType()!=null && t.getInterfaceType().isInstance(instance))
                return t;
        }
        return UNKNOWN;
    }
}