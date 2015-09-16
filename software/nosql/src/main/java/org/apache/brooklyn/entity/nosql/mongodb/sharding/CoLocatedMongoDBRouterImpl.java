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

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBAuthenticationMixins;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBAuthenticationUtils;
import org.apache.brooklyn.entity.software.base.SameServerEntityImpl;

public class CoLocatedMongoDBRouterImpl extends SameServerEntityImpl implements CoLocatedMongoDBRouter, MongoDBAuthenticationMixins {

    @Override
    public void init() {
        super.init();
        MongoDBRouter router = addChild(EntitySpec.create(MongoDBRouter.class)
                .configure(MongoDBRouter.CONFIG_SERVERS,
                        DependentConfiguration.attributeWhenReady(
                                getConfig(CoLocatedMongoDBRouter.SHARDED_DEPLOYMENT),
                                MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES)));

        for (EntitySpec<?> siblingSpec : getConfig(CoLocatedMongoDBRouter.SIBLING_SPECS)) {
            addChild(siblingSpec);
        }
        sensors().set(ROUTER, router);
    }

    @Override
    protected void doStart(Collection<? extends Location> locations) {
        MongoDBRouter router = sensors().get(ROUTER);

        // Do not attempt to read the configuration until after the router has been added to the cluster
        // as it is at this point that the authentication configuration is set
        Task<?> clusterTask = DependentConfiguration.attributeWhenReady(router, AbstractGroup.FIRST);
        Entities.submit(this, clusterTask);
        clusterTask.getUnchecked();

        MongoDBAuthenticationUtils.setAuthenticationConfig(router, this);
        router.sensors().set(MongoDBAuthenticationMixins.ROOT_PASSWORD, router.config().get(MongoDBAuthenticationMixins.ROOT_PASSWORD));
        router.sensors().set(MongoDBAuthenticationMixins.ROOT_USERNAME, router.config().get(MongoDBAuthenticationMixins.ROOT_USERNAME));
        router.sensors().set(MongoDBAuthenticationMixins.AUTHENTICATION_DATABASE, router.config().get(MongoDBAuthenticationMixins.AUTHENTICATION_DATABASE));
        addEnricher(Enrichers.builder().propagating(MongoDBRouter.PORT).from(router).build());
        
        super.doStart(locations);
        sensors().set(Startable.SERVICE_UP, true);
    }

}
