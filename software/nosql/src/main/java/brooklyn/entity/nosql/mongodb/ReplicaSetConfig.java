package brooklyn.entity.nosql.mongodb;

import com.mongodb.ServerAddress;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
     * Auto-incrememnts the replica set's version number.
     *
     * @see MongoClientSupport#getReplicaSetConfig()
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
     * Adds a new member to the replica set config using {@link MongoDBServer#HOSTNAME} and {@link MongoDBServer#PORT}
     * for hostname and port. Doesn't attempt to check that the id is free.
     */
    public ReplicaSetConfig member(MongoDBServer server, Integer id) {
        return member(server.getAttribute(MongoDBServer.HOSTNAME), server.getAttribute(MongoDBServer.PORT), id);
    }

    /**
     * Adds a new member to the replica set config using {@link com.mongodb.ServerAddress#getHost()} and
     * {@link com.mongodb.ServerAddress#getPort()} for hostname and port. Doesn't attempt to check that
     * the id is free.
     */
    public ReplicaSetConfig member(ServerAddress address, Integer id) {
        return member(address.getHost(), address.getPort(), id);
    }

    /**
     * Adds a new member to the replica set config with the given hostname, port and id. Doesn't attempt to check
     * that the id is free.
     */
    public ReplicaSetConfig member(String hostname, Integer port, Integer id) {
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
        BasicBSONObject config = new BasicBSONObject();
        config.put("_id", name);
        config.put("version", version);
        config.put("members", members);
        return config;
    }
}
