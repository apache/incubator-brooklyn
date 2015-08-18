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

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.entity.proxying.ImplementedBy;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.util.flags.SetFromFlag;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SameServerEntity;
import brooklyn.event.basic.Sensors;

import com.google.common.reflect.TypeToken;

@ImplementedBy(CoLocatedMongoDBRouterImpl.class)
public interface CoLocatedMongoDBRouter extends SameServerEntity {
    @SuppressWarnings("serial")
    @SetFromFlag("siblingSpecs")
    ConfigKey<Iterable<EntitySpec<?>>> SIBLING_SPECS = ConfigKeys.newConfigKey(new TypeToken<Iterable<EntitySpec<?>>>(){}, 
            "mongodb.colocatedrouter.sibling.specs", "Collection of (configured) specs for entities to be co-located with the router");
    
    @SetFromFlag("shardedDeployment")
    ConfigKey<MongoDBShardedDeployment> SHARDED_DEPLOYMENT = ConfigKeys.newConfigKey(MongoDBShardedDeployment.class, 
            "mongodb.colocatedrouter.shardeddeployment", "Sharded deployment to which the router should report");

    /** Deprecated since 0.7.0 use {@link #PROPAGATING_SENSORS} instead. */
    @Deprecated
    @SuppressWarnings("serial")
    @SetFromFlag("propogatingSensors")
    ConfigKey<List<Map<String, ?>>> PROPOGATING_SENSORS = ConfigKeys.newConfigKey(new TypeToken<List<Map<String, ?>>>(){}, 
            "mongodb.colocatedrouter.propogating.sensors", "List of sensors to be propogated from child members");

    @SetFromFlag("propagatingSensors")
    ConfigKey<List<Map<String, ?>>> PROPAGATING_SENSORS = ConfigKeys.newConfigKey(new TypeToken<List<Map<String, ?>>>(){},
            "mongodb.colocatedrouter.propagating.sensors", "List of sensors to be propogated from child members");

    public static AttributeSensor<MongoDBRouter> ROUTER = Sensors.newSensor(MongoDBRouter.class,
            "mongodb.colocatedrouter.router", "Router");
}
