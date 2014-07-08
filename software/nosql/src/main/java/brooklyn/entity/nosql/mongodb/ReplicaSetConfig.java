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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;

import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;

/**
 * Simplifies the creation of configuration objects for Mongo DB replica sets.
 * <p/>
 * A configuration object is structured like this:
 * <pre>
 * {
 *	"_id" : "replica-set-name",
 * 	"version" : 3,
 *	"members" : [
 *		{ "_id" : 0, "host" : "Sams.local:27017" },
 *		{ "_id" : 1, "host" : "Sams.local:27018" },
 *		{ "_id" : 2, "host" : "Sams.local:27019" }
 *	]
 * }
 * </pre>
 * To add or remove servers to a replica set you must redefine this configuration
 * (run <code>replSetReconfig</code> on the primary) with the new <code>members</code>
 * list and the <code>version</code> updated.
 */
public class ReplicaSetConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ReplicaSetConfig.class);
    static final int MAXIMUM_REPLICA_SET_SIZE = 12;
    static final int MAXIMUM_VOTING_MEMBERS = 7;

    private Optional<HostAndPort> primary = Optional.absent();

    private String name;
    private Integer version;
    BasicBSONList members;

    public ReplicaSetConfig(String name) {
        this(name, new BasicBSONList());
    }

    public ReplicaSetConfig(String name, BasicBSONList existingMembers) {
        this.name = name;
        this.members = existingMembers;
        this.version = 1;
    }

    /**
     * Creates a configuration with the given name.
     */
    public static ReplicaSetConfig builder(String name) {
        return new ReplicaSetConfig(name);
    }

    /**
     * Creates a configuration from an existing configuration.
     * <p/>
     * Automatically increments the replica set's version number.
     */
    public static ReplicaSetConfig fromExistingConfig(BSONObject config) {
        checkNotNull(config);
        checkArgument(config.containsField("_id"), "_id missing from replica set config");
        checkArgument(config.containsField("version"), "version missing from replica set config");
        checkArgument(config.containsField("members"), "members missing from replica set config");

        String name = (String) config.get("_id");
        Integer version = (Integer) config.get("version");
        BasicBSONList members = (BasicBSONList) config.get("members");

        return new ReplicaSetConfig(name, members).version(++version);
    }

    /**
     * Sets the version of the configuration. The version number must increase as the replica set changes.
     */
    public ReplicaSetConfig version(Integer version) {
        this.version = version;
        return this;
    }

    /**
     * Notes the primary member of the replica. Primary members will always be voting members.
     */
    public ReplicaSetConfig primary(HostAndPort primary) {
        this.primary = Optional.of(primary);
        return this;
    }

    /**
     * Adds a new member to the replica set config using {@link MongoDBServer#HOSTNAME} and {@link MongoDBServer#PORT}
     * for hostname and port. Doesn't attempt to check that the id is free.
     */
    public ReplicaSetConfig member(MongoDBServer server, Integer id) {
        return member(server.getAttribute(MongoDBServer.HOSTNAME), server.getAttribute(MongoDBServer.PORT), id);
    }

    /**
     * Adds a new member to the replica set config using the given {@link HostAndPort} for hostname and port.
     * Doesn't attempt to check that the id is free.
     */
    public ReplicaSetConfig member(HostAndPort address, Integer id) {
        return member(address.getHostText(), address.getPort(), id);
    }

    /**
     * Adds a new member to the replica set config with the given hostname, port and id. Doesn't attempt to check
     * that the id is free.
     */
    public ReplicaSetConfig member(String hostname, Integer port, Integer id) {
        if (members.size() == MAXIMUM_REPLICA_SET_SIZE) {
            throw new IllegalStateException(String.format(
                    "Replica set {} exceeds maximum size of {} with addition of member at {}:{}",
                    new Object[]{name, MAXIMUM_REPLICA_SET_SIZE, hostname, port}));
        }
        BasicBSONObject member = new BasicBSONObject();
        member.put("_id", id);
        member.put("host", String.format("%s:%s", hostname, port));
        members.add(member);
        return this;
    }

    /** Removes the first entity using {@link MongoDBServer#HOSTNAME} and {@link MongoDBServer#PORT}. */
    public ReplicaSetConfig remove(MongoDBServer server) {
        return remove(server.getAttribute(MongoDBServer.HOSTNAME), server.getAttribute(MongoDBServer.PORT));
    }

    /** Removes the first entity with host and port matching the given address. */
    public ReplicaSetConfig remove(HostAndPort address) {
        return remove(address.getHostText(), address.getPort());
    }

    /**
     * Removes the first entity with the given hostname and port from the list of members
     */
    public ReplicaSetConfig remove(String hostname, Integer port) {
        String host = String.format("%s:%s", hostname, port);
        Iterator<Object> it = this.members.iterator();
        while (it.hasNext()) {
            Object next = it.next();
            if (next instanceof BasicBSONObject) {
                BasicBSONObject basicBSONObject = (BasicBSONObject) next;
                if (host.equals(basicBSONObject.getString("host"))) {
                    it.remove();
                    break;
                }
            }
        }
        return this;
    }

    /**
     * @return A {@link BasicBSONObject} representing the configuration that is suitable for a MongoDB server.
     */
    public BasicBSONObject build() {
        setVotingMembers();
        BasicBSONObject config = new BasicBSONObject();
        config.put("_id", name);
        config.put("version", version);
        config.put("members", members);
        return config;
    }

    /**
     * Selects 1, 3, 5 or 7 members to have a vote. The primary member (as set by
     * {@link #primary(com.google.common.net.HostAndPort)}) is guaranteed a vote if
     * it is in {@link #members}.
     * <p/>
     *
     * Reconfiguring a server to be voters when they previously did not have votes generally triggers
     * a primary election. This confuses the MongoDB Java driver, which logs an error like:
     * <pre>
     * WARN  emptying DBPortPool to sams.home/192.168.1.64:27019 b/c of error
     * java.io.EOFException: null
     *	at org.bson.io.Bits.readFully(Bits.java:48) ~[mongo-java-driver-2.11.3.jar:na]
     * WARN  Command { "replSetReconfig" : ... } on sams.home/192.168.1.64:27019 failed
     * com.mongodb.MongoException$Network: Read operation to server sams.home/192.168.1.64:27019 failed on database admin
     *	at com.mongodb.DBTCPConnector.innerCall(DBTCPConnector.java:253) ~[mongo-java-driver-2.11.3.jar:na]
     * Caused by: java.io.EOFException: null
     *	at org.bson.io.Bits.readFully(Bits.java:48) ~[mongo-java-driver-2.11.3.jar:na]
     * </pre>
     *
     * The MongoDB documentation on <a href=http://docs.mongodb.org/manual/tutorial/configure-a-non-voting-replica-set-member/">
     * non-voting members</a> says:
     * <blockquote>
     *     Initializes a new replica set configuration. Disconnects the shell briefly and forces a
     *     reconnection as the replica set renegotiates which member will be primary. As a result,
     *     the shell will display an error even if this command succeeds.
     * </blockquote>
     *
     * So the problem is more that the MongoDB Java driver does not understand why the server
     * may have disconnected and is to eager to report a problem.
     */
    private void setVotingMembers() {
        if (LOG.isDebugEnabled())
            LOG.debug("Setting voting and non-voting members of replica set: {}", name);
        boolean seenPrimary = false;
        String expectedPrimary = primary.isPresent()
                ? primary.get().getHostText() + ":" + primary.get().getPort()
                : "";

        // Ensure an odd number of voters
        int setSize = this.members.size();
        int nonPrimaryVotingMembers = Math.min(setSize % 2 == 0 ? setSize - 1 : setSize, MAXIMUM_VOTING_MEMBERS);
        if (primary.isPresent()) {
            if (LOG.isTraceEnabled())
                LOG.trace("Reserving vote for primary: " + expectedPrimary);
            nonPrimaryVotingMembers -= 1;
        }

        for (Object member : this.members) {
            if (member instanceof BasicBSONObject) {
                BasicBSONObject bsonObject = BasicBSONObject.class.cast(member);
                String host = bsonObject.getString("host");

                // is this member noted as the primary?
                if (this.primary.isPresent() && expectedPrimary.equals(host)) {
                    bsonObject.put("votes", 1);
                    seenPrimary = true;
                    if (LOG.isDebugEnabled())
                        LOG.debug("Voting member (primary) of set {}: {}", name, host);
                } else if (nonPrimaryVotingMembers-- > 0) {
                    bsonObject.put("votes", 1);
                    if (LOG.isDebugEnabled())
                        LOG.debug("Voting member of set {}: {}", name, host);
                } else {
                    bsonObject.put("votes", 0);
                    if (LOG.isDebugEnabled())
                        LOG.debug("Non-voting member of set {}: {}", name, host);
                }
            } else {
                LOG.error("Unexpected entry in replica set members list: " + member);
            }
        }

        if (primary.isPresent() && !seenPrimary) {
            LOG.warn("Cannot give replica set primary a vote in reconfigured set: " +
                    "primary was indicated as {} but no member with that host and port was seen in the set. " +
                    "The replica set now has an even number of voters.",
                    this.primary);
        }
    }

}
