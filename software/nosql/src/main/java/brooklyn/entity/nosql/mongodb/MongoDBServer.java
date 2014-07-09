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

import org.bson.BasicBSONObject;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="MongoDB Server",
    description="MongoDB (from \"humongous\") is a scalable, high-performance, open source NoSQL database",
    iconUrl="classpath:///mongodb-logo.png")
@ImplementedBy(MongoDBServerImpl.class)
public interface MongoDBServer extends AbstractMongoDBServer {
    
    // See http://docs.mongodb.org/ecosystem/tools/http-interfaces/#http-console
    // This is *always* 1000 more than port. We disable if it is not available.
    PortAttributeSensorAndConfigKey HTTP_PORT =
        new PortAttributeSensorAndConfigKey("mongodb.server.httpPort", "HTTP port for the server (estimated)", "28017+");

    @SetFromFlag("enableRestInterface")
    ConfigKey<Boolean> ENABLE_REST_INTERFACE = ConfigKeys.newBooleanConfigKey(
            "mongodb.config.enable_rest", "Adds --rest to server startup flags when true", Boolean.FALSE);

    AttributeSensor<String> HTTP_INTERFACE_URL = Sensors.newStringSensor(
            "mongodb.server.http_interface", "URL of the server's HTTP console");

    AttributeSensor<BasicBSONObject> STATUS_BSON = Sensors.newSensor(BasicBSONObject.class,
            "mongodb.server.status.bson", "Server status (BSON/JSON map ojbect)");
    
    AttributeSensor<Double> UPTIME_SECONDS = Sensors.newDoubleSensor(
            "mongodb.server.uptime", "Server uptime in seconds");

    AttributeSensor<Long> OPCOUNTERS_INSERTS = Sensors.newLongSensor(
            "mongodb.server.opcounters.insert", "Server inserts");

    AttributeSensor<Long> OPCOUNTERS_QUERIES = Sensors.newLongSensor(
            "mongodb.server.opcounters.query", "Server queries");

    AttributeSensor<Long> OPCOUNTERS_UPDATES = Sensors.newLongSensor(
            "mongodb.server.opcounters.update", "Server updates");

    AttributeSensor<Long> OPCOUNTERS_DELETES = Sensors.newLongSensor(
            "mongodb.server.opcounters.delete", "Server deletes");

    AttributeSensor<Long> OPCOUNTERS_GETMORE = Sensors.newLongSensor(
            "mongodb.server.opcounters.getmore", "Server getmores");

    AttributeSensor<Long> OPCOUNTERS_COMMAND = Sensors.newLongSensor(
            "mongodb.server.opcounters.command", "Server commands");

    AttributeSensor<Long> NETWORK_BYTES_IN = Sensors.newLongSensor(
            "mongodb.server.network.bytesIn", "Server incoming network traffic (in bytes)");

    AttributeSensor<Long> NETWORK_BYTES_OUT = Sensors.newLongSensor(
            "mongodb.server.network.bytesOut", "Server outgoing network traffic (in bytes)");

    AttributeSensor<Long> NETWORK_NUM_REQUESTS = Sensors.newLongSensor(
            "mongodb.server.network.numRequests", "Server network requests");

    /** A single server's replica set configuration **/
    ConfigKey<MongoDBReplicaSet> REPLICA_SET = new BasicConfigKey<MongoDBReplicaSet>(MongoDBReplicaSet.class,
            "mongodb.replicaset", "The replica set to which the server belongs. " +
            "Users should not set this directly when creating a new replica set.");

    AttributeSensor<ReplicaSetMemberStatus> REPLICA_SET_MEMBER_STATUS = Sensors.newSensor(
            ReplicaSetMemberStatus.class, "mongodb.server.replicaSet.memberStatus", "The status of this server in the replica set");

    AttributeSensor<Boolean> IS_PRIMARY_FOR_REPLICA_SET = Sensors.newBooleanSensor(
            "mongodb.server.replicaSet.isPrimary", "True if this server is the write master for the replica set");

    AttributeSensor<Boolean> IS_SECONDARY_FOR_REPLICA_SET = Sensors.newBooleanSensor(
            "mongodb.server.replicaSet.isSecondary", "True if this server is a secondary server in the replica set");

    AttributeSensor<String> REPLICA_SET_PRIMARY_ENDPOINT = Sensors.newStringSensor(
            "mongodb.server.replicaSet.primary.endpoint", "The host:port of the server which is acting as primary (master) for the replica set");

    AttributeSensor<String> MONGO_SERVER_ENDPOINT = Sensors.newStringSensor(
        "mongodb.server.endpoint", "The host:port where this server is listening");

    /**
     * @return The replica set the server belongs to, or null if the server is a standalone instance.
     */
    MongoDBReplicaSet getReplicaSet();

    /**
     * @return True if the server is a child of {@link MongoDBReplicaSet}.
     */
    boolean isReplicaSetMember();

    /**
     * Initialises a replica set at the server the method is invoked on.
     * @param replicaSetName The name for the replica set.
     * @param id The id to be given to this server in the replica set configuration.
     * @return True if initialisation is successful.
     */
    boolean initializeReplicaSet(String replicaSetName, Integer id);

    /**
     * Reconfigures the replica set that the server the method is invoked on is the primary member of
     * to include a new member.
     * <p/>
     * Note that this can cause long downtime (typically 10-20s, even up to a minute).
     *
     * @param secondary New member of the set.
     * @param id The id for the new set member. Must be unique within the set; its validity is not checked.
     * @return True if addition is successful. False if the server this is called on is not the primary
     *         member of the replica set.
     */
    boolean addMemberToReplicaSet(MongoDBServer secondary, Integer id);

    /**
     * Reconfigures the replica set that the server the method is invoked on is the primary member of
     * to remove the given server.
     * @param server The server to remove.
     * @return True if removal is successful. False if the server this is called on is not the primary
     *         member of the replica set.
     */
    boolean removeMemberFromReplicaSet(MongoDBServer server);

}
