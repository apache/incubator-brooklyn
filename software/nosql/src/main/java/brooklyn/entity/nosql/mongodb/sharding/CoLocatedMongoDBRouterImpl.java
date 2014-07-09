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
package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.basic.SameServerEntityImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;

public class CoLocatedMongoDBRouterImpl extends SameServerEntityImpl implements CoLocatedMongoDBRouter {
    @Override
    public void init() {
        super.init();
        
        for (EntitySpec<?> siblingSpec : getConfig(CoLocatedMongoDBRouter.SIBLING_SPECS)) {
            addChild(siblingSpec);
        }
        setAttribute(ROUTER, addChild(EntitySpec.create(MongoDBRouter.class)
                .configure(MongoDBRouter.CONFIG_SERVERS,
                        DependentConfiguration.attributeWhenReady(getConfig(CoLocatedMongoDBRouter.SHARDED_DEPLOYMENT), MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES))));
        addEnricher(Enrichers.builder().propagating(MongoDBRouter.PORT).from(getAttribute(ROUTER)).build());
    }
    
    @Override
    protected void doStart(Collection<? extends Location> locations) {
        super.start(locations);
        setAttribute(Startable.SERVICE_UP, true);
    }
}
