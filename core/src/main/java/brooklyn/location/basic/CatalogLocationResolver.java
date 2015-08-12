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
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;

/**
 * Given a location spec in the form {@code brooklyn.catalog:<symbolicName>:<version>}, 
 * looks up the catalog to get its definition and creates such a location.
 */
public class CatalogLocationResolver implements LocationResolver {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(CatalogLocationResolver.class);

    public static final String NAME = "brooklyn.catalog";

    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        String id = spec.substring(NAME.length()+1);
        CatalogItem<?, ?> item = CatalogUtils.getCatalogItemOptionalVersion(managementContext, id);
        LocationSpec<?> origLocSpec = managementContext.getCatalog().createSpec((CatalogItem<Location, LocationSpec<?>>)item);
        LocationSpec<?> locSpec = LocationSpec.create(origLocSpec)
                .configure(locationFlags);
        return managementContext.getLocationManager().createLocation(locSpec);
    }

    @Override
    public String getPrefix() {
        return NAME;
    }
    
    /**
     * accepts anything that looks like it will be a YAML catalog item (e.g. starting "brooklyn.locations")
     */
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        if (BasicLocationRegistry.isResolverPrefixForSpec(this, spec, false)) return true;
        if (registry.getDefinedLocationByName(spec)!=null) return true;
        return false;
    }

}
