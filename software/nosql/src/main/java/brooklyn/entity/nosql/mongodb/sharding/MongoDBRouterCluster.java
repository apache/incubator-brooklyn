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

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

@ImplementedBy(MongoDBRouterClusterImpl.class)
public interface MongoDBRouterCluster extends DynamicCluster {

    AttributeSensor<MongoDBRouter> ANY_ROUTER = Sensors.newSensor(MongoDBRouter.class, "mongodb.routercluster.any", 
            "When set, can be used to access one of the routers in the cluster (usually the first). This will only be set once "
            + "at least one shard has been added, and the router is available for CRUD operations");
    
    AttributeSensor<MongoDBRouter> ANY_RUNNING_ROUTER = Sensors.newSensor(MongoDBRouter.class, "mongodb.routercluster.any.running", 
            "When set, can be used to access one of the running routers in the cluster (usually the first). This should only be used " 
            + "to add shards as it does not guarantee that the router is available for CRUD operations");

    /**
     * @return One of the routers in the cluster if available, null otherwise
     */
    MongoDBRouter getAnyRouter();
    
    /**
     * @return One of the running routers in the cluster. This should only be used to add shards as it does not guarantee that 
     * the router is available for CRUD operations
     */
    MongoDBRouter getAnyRunningRouter();
    
    /**
     * @return All routers in the cluster
     */
    Collection<MongoDBRouter> getRouters();
}
