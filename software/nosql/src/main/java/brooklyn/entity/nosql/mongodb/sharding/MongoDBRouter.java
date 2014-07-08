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

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.time.Duration;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MongoDBRouterImpl.class)
public interface MongoDBRouter extends AbstractMongoDBServer {

    @SuppressWarnings("serial")
    ConfigKey<Iterable<String>> CONFIG_SERVERS = ConfigKeys.newConfigKey(
            new TypeToken<Iterable<String>>(){}, "mongodb.router.config.servers", "List of host names and ports of the config servers");
    
    AttributeSensor<Integer> SHARD_COUNT = Sensors.newIntegerSensor("mongodb.router.config.shard.count", "Number of shards that have been added");
    
    AttributeSensor<Boolean> RUNNING = Sensors.newBooleanSensor("mongodb.router.running", "Indicates that the router is running, "
            + "and can be used to add shards, but is not necessarity available for CRUD operations (e.g. if no shards have been added)");

    /**
     * @throws IllegalStateException if times out.
     */
    public void waitForServiceUp(Duration duration);
}
