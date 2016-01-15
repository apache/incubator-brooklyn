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
package org.apache.brooklyn.entity.nosql.mongodb;

import java.net.UnknownHostException;
import java.util.concurrent.Callable;

import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;

/**
 * Manages connections to standalone MongoDB servers.
 *
 * @see <a href="http://docs.mongodb.org/manual/reference/command/">MongoDB database command documentation</a>
 */
public class MongoDBClientSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBClientSupport.class);
    private ServerAddress address;

    private boolean usesAuthentication;
    private String username;
    private String password;
    private String authenticationDatabase;

    private MongoClient client() {
        return baseClient(connectionOptions);
    }

    private MongoClient fastClient() {
        MongoClientOptions fastConnectionOptions = MongoClientOptions.builder()
                .connectTimeout(1000 * 10)
                .maxWaitTime(1000 * 10)
                .serverSelectionTimeout(1000 * 10)
                .build();

        return baseClient(fastConnectionOptions);
    }

    private MongoClient baseClient(MongoClientOptions connectionOptions) {
        if (usesAuthentication) {
            MongoCredential credential = MongoCredential.createMongoCRCredential(username, authenticationDatabase, password.toCharArray());
            return new MongoClient(address, ImmutableList.of(credential), connectionOptions);
        } else {
            return new MongoClient(address, connectionOptions);
        }
    }

    // Set client to automatically reconnect to servers.
    private static final MongoClientOptions connectionOptions = MongoClientOptions.builder()
            .socketKeepAlive(true)
            .build();

    private static final BasicBSONObject EMPTY_RESPONSE = new BasicBSONObject();

    public MongoDBClientSupport(ServerAddress standalone) {
        address = standalone;
        usesAuthentication = false;
    }

    public MongoDBClientSupport(ServerAddress standalone, String username, String password, String authenticationDatabase) {
        // We could also use a MongoClient to access an entire replica set. See MongoClient(List<ServerAddress>).
        address = standalone;
        this.usesAuthentication = true;
        this.username = username;
        this.password = password;
        this.authenticationDatabase = authenticationDatabase;
    }

    /**
     * Creates a {@link MongoDBClientSupport} instance in standalone mode.
     */
    public static MongoDBClientSupport forServer(AbstractMongoDBServer standalone) throws UnknownHostException {
        HostAndPort hostAndPort = BrooklynAccessUtils.getBrooklynAccessibleAddress(standalone, standalone.getAttribute(MongoDBServer.PORT));
        ServerAddress address = new ServerAddress(hostAndPort.getHostText(), hostAndPort.getPort());
        if (MongoDBAuthenticationUtils.usesAuthentication(standalone)) {
            return new MongoDBClientSupport(address, standalone.sensors().get(MongoDBAuthenticationMixins.ROOT_USERNAME),
                    standalone.sensors().get(MongoDBAuthenticationMixins.ROOT_PASSWORD), standalone.sensors().get(MongoDBAuthenticationMixins.AUTHENTICATION_DATABASE));
        } else {
            return new MongoDBClientSupport(address);
        }
    }

    private ServerAddress getServerAddress() {
        MongoClient client = client();
        try {
            return client.getServerAddressList().get(0);
        } finally {
            client.close();
        }
    }

    private HostAndPort getServerHostAndPort() {
        ServerAddress address = getServerAddress();
        return HostAndPort.fromParts(address.getHost(), address.getPort());
    }

    public Optional<CommandResult> runDBCommand(String database, String command) {
        return runDBCommand(database, new BasicDBObject(command, Boolean.TRUE));
    }

    private Optional<CommandResult> runDBCommand(String database, final DBObject command) {
        MongoClient client = client();
        try {
            final DB db = client.getDB(database);
            final CommandResult[] status = new CommandResult[1];

            // The mongoDB client can occasionally fail to connect. Try up to 5 times to run the command
            boolean commandResult = Repeater.create().backoff(Duration.ONE_SECOND, 1.5, null).limitIterationsTo(5)
                    .until(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            try {
                                status[0] = db.command(command);
                                return true;
                            } catch (Exception e) {
                                LOG.warn("Command " + command + " on " + address.getHost() + " failed", e);
                                return false;
                            }
                        }
            }).run();

            if (!commandResult) {
                return Optional.absent();
            }

            if (!status[0].ok()) {
                LOG.debug("Unexpected result of {} on {}: {}",
                        new Object[] { command, getServerAddress(), status[0].getErrorMessage() });
            }
            return Optional.of(status[0]);
        } finally {
            client.close();
        }
    }
    
    public long getShardCount() {
        MongoClient client = client();
        try {
            return client.getDB("config").getCollection("shards").getCount();
        } finally {
            client.close();
        }
    }

    public BasicBSONObject getServerStatus() {
        Optional<CommandResult> result = runDBCommand("admin", "serverStatus");
        if (result.isPresent() && result.get().ok()) {
            return result.get();
        } else {
            return EMPTY_RESPONSE;
        }
    }
    
    public boolean ping() {
        MongoClient client = fastClient();
        DBObject command = new BasicDBObject("ping", "1");
        final DB db = client.getDB("admin");

        try {
            CommandResult status = db.command(command);
            return status.ok();
        } catch (MongoException e) {
            LOG.warn("Pinging server {} failed with {}", address.getHost(), e);
        } finally {
            client.close();
        }
        return false;
    }

    public boolean initializeReplicaSet(String replicaSetName, Integer id) {
        HostAndPort primary = getServerHostAndPort();
        BasicBSONObject config = ReplicaSetConfig.builder(replicaSetName)
                .member(primary, id)
                .build();

        BasicDBObject dbObject = new BasicDBObject("replSetInitiate", config);
        LOG.debug("Initiating replica set with: " + dbObject);

        Optional<CommandResult> result = runDBCommand("admin", dbObject);
        if (result.isPresent() && result.get().ok() && LOG.isDebugEnabled()) {
            LOG.debug("Completed initiating MongoDB replica set {} on entity {}", replicaSetName, this);
        }
        return result.isPresent() && result.get().ok();
    }

    /**
     * Java equivalent of calling rs.conf() in the console.
     */
    private BSONObject getReplicaSetConfig() {
        MongoClient client = client();
        try {
            return client.getDB("local").getCollection("system.replset").findOne();
        } catch (MongoException e) {
            LOG.error("Failed to get replica set config on "+client, e);
            return null;
        } finally {
            client.close();
        }
    }

    /**
     * Runs <code>replSetGetStatus</code> on the admin database.
     *
     * @return The result of <code>replSetGetStatus</code>, or
     *         an empty {@link BasicBSONObject} if the command threw an exception (e.g. if
     *         the connection was reset) or if the resultant {@link CommandResult#ok} was false.
     *
     * @see <a href="http://docs.mongodb.org/manual/reference/replica-status/">Replica set status reference</a>
     * @see <a href="http://docs.mongodb.org/manual/reference/command/replSetGetStatus/">replSetGetStatus documentation</a>
     */
    public BasicBSONObject getReplicaSetStatus() {
        Optional<CommandResult> result = runDBCommand("admin", "replSetGetStatus");
        if (result.isPresent() && result.get().ok()) {
            return result.get();
        } else {
            return EMPTY_RESPONSE;
        }
    }

    /**
     * Reconfigures the replica set that this client is the primary member of to include a new member.
     * <p/>
     * Note that this can cause long downtime (typically 10-20s, even up to a minute).
     *
     * @param secondary New member of the set.
     * @param id The id for the new set member. Must be unique within the set.
     * @return True if successful
     */
    public boolean addMemberToReplicaSet(MongoDBServer secondary, Integer id) {
        // We need to:
        // - get the existing configuration
        // - update its version
        // - add the new member to its list of members
        // - run replSetReconfig with the new configuration.
        BSONObject existingConfig = getReplicaSetConfig();
        if (existingConfig == null) {
            LOG.warn("Couldn't load existing config for replica set from {}. Server {} not added.",
                    getServerAddress(), secondary);
            return false;
        }

        BasicBSONObject newConfig = ReplicaSetConfig.fromExistingConfig(existingConfig)
                .primary(getServerHostAndPort())
                .member(secondary, id)
                .build();
        return reconfigureReplicaSet(newConfig);
    }

    /**
     * Reconfigures the replica set that this client is the primary member of to
     * remove the given server.
     * @param server The server to remove
     * @return True if successful
     */
    public boolean removeMemberFromReplicaSet(MongoDBServer server) {
        BSONObject existingConfig = getReplicaSetConfig();
        if (existingConfig == null) {
            LOG.warn("Couldn't load existing config for replica set from {}. Server {} not removed.",
                    getServerAddress(), server);
            return false;
        }
        BasicBSONObject newConfig = ReplicaSetConfig.fromExistingConfig(existingConfig)
                .primary(getServerHostAndPort())
                .remove(server)
                .build();
        return reconfigureReplicaSet(newConfig);
    }

    /**
     * Runs replSetReconfig with the given BasicBSONObject. Returns true if the result's
     * status is ok.
     */
    private boolean reconfigureReplicaSet(BasicBSONObject newConfig) {
        BasicDBObject command = new BasicDBObject("replSetReconfig", newConfig);
        LOG.debug("Reconfiguring replica set to: " + command);
        Optional<CommandResult> result = runDBCommand("admin", command);
        return result.isPresent() && result.get().ok();
    }

    public boolean addShardToRouter(String hostAndPort) {
        LOG.debug("Adding shard " + hostAndPort);
        BasicDBObject command = new BasicDBObject("addShard", hostAndPort);
        Optional<CommandResult> result = runDBCommand("admin", command);
        return result.isPresent() && result.get().ok();
    }

}
