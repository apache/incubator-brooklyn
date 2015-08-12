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

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;

import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.Policy;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.location.Location;

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