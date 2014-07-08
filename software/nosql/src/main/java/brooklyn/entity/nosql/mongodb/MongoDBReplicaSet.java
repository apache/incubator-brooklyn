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
package brooklyn.entity.nosql.mongodb;

import java.util.Collection;
import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

/**
 * A replica set of {@link MongoDBServer}s, based on {@link DynamicCluster} which can be resized by a policy
 * if required.
 *
 * <p/><b>Note</b>
 * An issue with <code>mongod</code> on Mac OS X can cause unpredictable failure of servers at start-up.
 * See <a href="https://groups.google.com/forum/#!topic/mongodb-user/QRQYdIXOR2U">this mailing list post</a>
 * for more information.
 *
 * <p/>This replica set implementation has been tested on OS X 10.6 and Ubuntu 12.04.
 *
 * @see <a href="http://docs.mongodb.org/manual/replication/">http://docs.mongodb.org/manual/replication/</a>
 */
@ImplementedBy(MongoDBReplicaSetImpl.class)
public interface MongoDBReplicaSet extends DynamicCluster {

    @SetFromFlag("replicaSetName")
    ConfigKey<String> REPLICA_SET_NAME = ConfigKeys.newStringConfigKey(
            "mongodb.replicaSet.name", "Name of the MongoDB replica set", "BrooklynCluster");

    AttributeSensor<MongoDBServer> PRIMARY_ENTITY = Sensors.newSensor(
            MongoDBServer.class, "mongodb.replicaSet.primary.entity", "The entity acting as primary");

    @SuppressWarnings("serial")
    AttributeSensor<List<String>> REPLICA_SET_ENDPOINTS = Sensors.newSensor(new TypeToken<List<String>>() {}, 
        "mongodb.replicaSet.endpoints", "Endpoints active for this replica set");
    
    /**
     * The name of the replica set.
     */
    String getName();

    /**
     * @return The primary MongoDB server in the replica set.
     */
    MongoDBServer getPrimary();

    /**
     * @return The secondary servers in the replica set.
     */
    Collection<MongoDBServer> getSecondaries();

    /**
     * @return All servers in the replica set.
     */
    Collection<MongoDBServer> getReplicas();

}
