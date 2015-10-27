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
package org.apache.brooklyn.core.mgmt.rebind;

import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindExceptionHandler;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoPersister.LookupContext;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynObjectType;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.Feed;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;

/** Looks in {@link RebindContext} <i>and</i> {@link ManagementContext} to find entities, locations, etc. */
public class RebindContextLookupContext implements LookupContext {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RebindContextLookupContext.class);
    
    @Nullable
    protected final ManagementContext managementContext;
    
    protected final RebindContextImpl rebindContext;
    protected final RebindExceptionHandler exceptionHandler;
    
    public RebindContextLookupContext(ManagementContext managementContext, RebindContextImpl rebindContext, RebindExceptionHandler exceptionHandler) {
        this.managementContext = managementContext;
        this.rebindContext = rebindContext;
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override public ManagementContext lookupManagementContext() {
        return managementContext;
    }
    
    @Override public Entity lookupEntity(String id) {
        Entity result = rebindContext.getEntity(id);
        if (result == null) {
            result = managementContext.lookup(id, Entity.class);
        }
        if (result == null) {
            result = exceptionHandler.onDanglingEntityRef(id);
        }
        return result;
    }
    
    @Override public Location lookupLocation(String id) {
        Location result = rebindContext.getLocation(id);
        if (result == null) {
            result = managementContext.lookup(id, Location.class);
        }
        if (result == null) {
            result = exceptionHandler.onDanglingLocationRef(id);
        }
        return result;
    }
    
    @Override public Policy lookupPolicy(String id) {
        Policy result = rebindContext.getPolicy(id);
        if (result == null) {
            result = managementContext.lookup(id, Policy.class);
        }
        if (result == null) {
            result = exceptionHandler.onDanglingPolicyRef(id);
        }
        return result;
    }
    
    @Override public Enricher lookupEnricher(String id) {
        Enricher result = rebindContext.getEnricher(id);
        if (result == null) {
            result = managementContext.lookup(id, Enricher.class);
        }
        if (result == null) {
            result = exceptionHandler.onDanglingEnricherRef(id);
        }
        return result;
    }

    @Override public Feed lookupFeed(String id) {
        Feed result = rebindContext.getFeed(id);
        if (result == null) {
            result = managementContext.lookup(id, Feed.class);
        }
        if (result == null) {
            result = exceptionHandler.onDanglingFeedRef(id);
        }
        return result;
    }

    @Override
    public CatalogItem<?, ?> lookupCatalogItem(String id) {
        CatalogItem<?, ?> result = rebindContext.getCatalogItem(id);
        if (result == null) {
            result = CatalogUtils.getCatalogItemOptionalVersion(managementContext, id);
        }
        if (result == null) {
            result = exceptionHandler.onDanglingCatalogItemRef(id);
        }
        return result;
    }
    
    @Override
    public BrooklynObject lookup(BrooklynObjectType type, String id) {
        if (type==null) {
            BrooklynObject result = peek(null, id);
            if (result==null) {
                exceptionHandler.onDanglingUntypedItemRef(id);
            }
            type = BrooklynObjectType.of(result);
        }
        
        switch (type) {
        case CATALOG_ITEM: return lookupCatalogItem(id);
        case ENRICHER: return lookupEnricher(id);
        case ENTITY: return lookupEntity(id);
        case FEED: return lookupFeed(id);
        case LOCATION: return lookupLocation(id);
        case POLICY: return lookupPolicy(id);
        case UNKNOWN: return null;
        }
        throw new IllegalStateException("Unexpected type "+type+" / id "+id);
    }
    
    @Override
    public BrooklynObject peek(BrooklynObjectType type, String id) {
        if (type==null) {
            for (BrooklynObjectType typeX: BrooklynObjectType.values()) {
                BrooklynObject result = peek(typeX, id);
                if (result!=null) return result;
            }
            return null;
        }
        
        switch (type) {
        case CATALOG_ITEM: return rebindContext.getCatalogItem(id);
        case ENRICHER: return rebindContext.getEnricher(id);
        case ENTITY: return rebindContext.getEntity(id);
        case FEED: return rebindContext.getFeed(id);
        case LOCATION: return rebindContext.getLocation(id);
        case POLICY: return rebindContext.getPolicy(id);
        case UNKNOWN: return null;
        }
        throw new IllegalStateException("Unexpected type "+type+" / id "+id);
    }


}