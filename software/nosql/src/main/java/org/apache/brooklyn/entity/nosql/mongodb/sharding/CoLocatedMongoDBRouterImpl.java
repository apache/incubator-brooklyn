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
package org.apache.brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SameServerEntityImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class CoLocatedMongoDBRouterImpl extends SameServerEntityImpl implements CoLocatedMongoDBRouter {
    @Override
    public void init() {
        super.init();
        
        for (EntitySpec<?> siblingSpec : getConfig(CoLocatedMongoDBRouter.SIBLING_SPECS)) {
            addChild(siblingSpec);
        }
    }

    @Override
    protected void doStart(Collection<? extends Location> locations) {
        // TODO Changed to create the router child after init as a workaround.
        // When we use `mongo-sharded.yaml`, and we call 
        // `getConfig(CoLocatedMongoDBRouter.SHARDED_DEPLOYMENT)`,
        // the value is `$brooklyn:component("shardeddeployment")`.
        // To look up the component, it tries to do `entity().getApplication()` to
        // search the entities for one with the correct id. However if being done
        // during `init()`, then this (which is returned by `entity()`) has not had its parent
        // set, so `entity().getApplication()` returns null.
        //
        // We should move this code back to `init()` once we have a solution for that.
        // We can also remove the call to Entities.manage() once this is in init() again.
        
        MongoDBRouter router = addChild(EntitySpec.create(MongoDBRouter.class)
                .configure(MongoDBRouter.CONFIG_SERVERS,
                        DependentConfiguration.attributeWhenReady(
                                getConfig(CoLocatedMongoDBRouter.SHARDED_DEPLOYMENT), 
                                MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES)));
        Entities.manage(router);
        setAttribute(ROUTER, (MongoDBRouter) Iterables.tryFind(getChildren(), Predicates.instanceOf(MongoDBRouter.class)).get());
        addEnricher(Enrichers.builder().propagating(MongoDBRouter.PORT).from(router).build());
        
        super.doStart(locations);
        setAttribute(Startable.SERVICE_UP, true);
    }
}
