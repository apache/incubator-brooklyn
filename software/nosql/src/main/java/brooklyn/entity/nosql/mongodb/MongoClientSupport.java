package brooklyn.entity.nosql.mongodb;

import com.google.common.base.Optional;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * Manages connections to standalone MongoDB servers.
 *
 * @see <a href="http://docs.mongodb.org/manual/reference/command/">MongoDB database command documentation</a>
 */
public class MongoClientSupport implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(MongoClientSupport.class);

    private final MongoClient client;

    // Set client to automatically reconnect to servers.
    private static final MongoClientOptions connectionOptions = MongoClientOptions.builder()
            .autoConnectRetry(true)
            .socketKeepAlive(true)
            .build();

    private static final BasicBSONObject EMPTY_RESPONSE = new BasicBSONObject();

    public MongoClientSupport(ServerAddress standlone) {
        // We could also use a MongoClient to access an entire replica set. See MongoClient(List<ServerAddress>).
        client = new MongoClient(standlone, connectionOptions);
    }

    /**
     * Creates a {@link MongoClientSupport} instance in standalone mode.
     * Returns {@link com.google.common.base.Optional#absent} if the server's host and port are unknown.
     */
    public static MongoClientSupport forServer(MongoDbServer standlone) throws UnknownHostException {
        String hostname = standlone.getAttribute(MongoDbServer.HOSTNAME);
        Integer port = standlone.getAttribute(MongoDbServer.PORT);
        ServerAddress address = new ServerAddress(hostname, port);
        return new MongoClientSupport(address);
    }

    private Optional<CommandResult> runDBCommand(String database, String command) {
        return runDBCommand(database, new BasicDBObject(command, Boolean.TRUE));
    }

    private Optional<CommandResult> runDBCommand(String database, DBObject command) {
        DB db = client.getDB(database);
        CommandResult status;
        try {
            status = db.command(command);
        } catch (MongoException e) {
            LOG.warn("Command {} on {} failed: {}",
                    new Object[]{command, client.getServerAddressList().get(0), e});
            return Optional.absent();
        }
        if (!status.ok()) {
            LOG.debug("Unexpected result of {} on {}: {}",
                    new Object[]{command, client.getServerAddressList().get(0), status.getErrorMessage()});
            return Optional.absent();
        }
        return Optional.of(status);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    public BasicBSONObject getServerStatus() {
        Optional<CommandResult> result = runDBCommand("admin", "serverStatus");
        if (result.isPresent() && result.get().ok()) {
            return result.get();
        } else {
            return EMPTY_RESPONSE;
        }
    }

    public boolean initializeReplicaSet(String replicaSetName, Integer id) {
        ServerAddress primary = client.getServerAddressList().get(0);
        BasicBSONObject config = ReplicaSetConfig.builder(replicaSetName)
                .member(primary, id)
                .build();

        BasicDBObject dbObject = new BasicDBObject("replSetInitiate", config);
        LOG.debug("Initiating replica set with: " + dbObject);

        Optional<CommandResult> result = runDBCommand("admin", dbObject);
        if (result.isPresent() && result.get().ok() && LOG.isDebugEnabled()) {
            LOG.debug("Completed intiating MongoDB replica set {} on entity {}", replicaSetName, this);
        }
        return result.isPresent() && result.get().ok();
    }

    /**
     * Java equivalent of calling rs.conf() in the console.
     */
    public BSONObject getReplicaSetConfig() {
        try {
            return client.getDB("local").getCollection("system.replset").findOne();
        } catch (MongoException e) {
            LOG.warn("Failed to get replica set config on {}: {}", client, e);
            return null;
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
    public boolean addMemberToReplicaSet(MongoDbServer secondary, Integer id) {
        // We need to:
        // - get the existing configuration
        // - update its version
        // - add the new member to its list of members
        // - run replSetReconfig with the new configuration.
        BSONObject existingConfig = getReplicaSetConfig();
        if (existingConfig == null) {
            LOG.warn("Couldn't load existing config for replica set.");
            return false;
        }

        BasicBSONObject newConfig = ReplicaSetConfig.fromExistingConfig(existingConfig)
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
    public boolean removeMemberFromReplicaSet(MongoDbServer server) {
        BSONObject existingConfig = getReplicaSetConfig();
        if (existingConfig == null) {
            LOG.warn("Couldn't load config for replica set.");
            return false;
        }
        BasicBSONObject newConfig = ReplicaSetConfig.fromExistingConfig(existingConfig)
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

}
