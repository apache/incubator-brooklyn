package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class MongoDbServerImpl extends SoftwareProcessImpl implements MongoDbServer {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbServerImpl.class);

    private FunctionFeed serviceStats;

    public MongoDbServerImpl() {
    }

    public MongoDbServerImpl(Map flags){
        this(flags, null);
    }

    public MongoDbServerImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Integer getServerPort() {
        return getAttribute(MongoDbServer.PORT);
    }

    @Override
    public Class getDriverInterface() {
        return MongoDbDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        connectServiceUpIsRunning();

        serviceStats = FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Object, BasicBSONObject>(STATUS)
                        .period(5, TimeUnit.SECONDS)
                        .callable(new ServerStatsCallable(this))
                        .onError(Functions.<BasicBSONObject>constant(null)))
                .build();

        // Take interesting details from STATUS.
        subscribe(this, STATUS, new SensorEventListener<BasicBSONObject>() {
                @Override public void onEvent(SensorEvent<BasicBSONObject> event) {
                    BasicBSONObject map = event.getValue();
                    if (map != null && !map.isEmpty()) {
                        setAttribute(UPTIME_SECONDS, map.getDouble("uptime", 0));

                        // Seems you can't call get("opcounters.insert")
                        BasicBSONObject opcounters = (BasicBSONObject) map.get("opcounters");
                        setAttribute(OPCOUNTERS_INSERTS, opcounters.getLong("insert", 0));
                        setAttribute(OPCOUNTERS_QUERIES, opcounters.getLong("query", 0));
                        setAttribute(OPCOUNTERS_UPDATES, opcounters.getLong("update", 0));
                        setAttribute(OPCOUNTERS_DELETES, opcounters.getLong("delete", 0));
                        setAttribute(OPCOUNTERS_GETMORE, opcounters.getLong("getmore", 0));
                        setAttribute(OPCOUNTERS_COMMAND, opcounters.getLong("command", 0));

                        // Network stats
                        BasicBSONObject network = (BasicBSONObject) map.get("network");
                        setAttribute(NETWORK_BYTES_IN, network.getLong("bytesIn", 0));
                        setAttribute(NETWORK_BYTES_OUT, network.getLong("bytesOut", 0));
                        setAttribute(NETWORK_NUM_REQUESTS, network.getLong("numRequests", 0));

                        // Replica set stats
                        BasicBSONObject repl = (BasicBSONObject) map.get("repl");
                        if (repl == null) {
                            setAttribute(REPLICA_SET_ENABLED, false);
                            setAttribute(REPLICA_SET_NAME, null);
                            setAttribute(REPLICA_SET_MASTER, false);
                            setAttribute(REPLICA_SET_SECONDARY, false);
                            setAttribute(REPLICA_SET_HOSTS, null);
                            setAttribute(REPLICA_SET_NUMBER_OF_HOSTS, 0);
                        } else {
                            setAttribute(REPLICA_SET_ENABLED, true);
                            setAttribute(REPLICA_SET_NAME, repl.getString("setName"));
                            setAttribute(REPLICA_SET_MASTER, repl.getBoolean("ismaster"));
                            setAttribute(REPLICA_SET_SECONDARY, repl.getBoolean("secondary"));
                            Object hosts = repl.get("hosts");
                            hosts = (hosts != null) ? hosts : Collections.EMPTY_SET;
                            ImmutableList<String> hostsList = FluentIterable.from((BasicBSONList) hosts)
                                    .transform(Functions.toStringFunction())
                                    .toImmutableList();
                            setAttribute(REPLICA_SET_HOSTS, Joiner.on(",").join(hostsList));
                            setAttribute(REPLICA_SET_NUMBER_OF_HOSTS, hostsList.size());
                            setAttribute(REPLICA_SET_PRIMARY, repl.getString("primary"));
                            setAttribute(REPLICA_SET_ME, repl.getString("me"));
                        }
                    }
                }
        });
    }

    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();
        if (serviceStats != null) serviceStats.stop();
    }

    @Override
    public void initializeReplicaSet() {
        String replicaSetName = getConfig(MongoDbServer.REPLICA_SET_NAME);

        BasicBSONObject myHost = new BasicBSONObject();
        myHost.put("_id", 0);
        myHost.put("host", String.format("%s:%s",
                getAttribute(SoftwareProcess.HOSTNAME),
                getAttribute(MongoDbServer.PORT)));

        BasicBSONList members = new BasicBSONList();
        members.add(myHost);

        BasicBSONObject config = new BasicBSONObject();
        config.put("_id", replicaSetName);
        config.put("members", members);

        LOG.info("Initiating MongoDB replica set {} on entity {}", replicaSetName, this);

        MongoClient client = getMongoClient(this);
        if (client == null) {
            LOG.error("Failed to initiate replica set for {} - could not obtain Mongo client",
                    getDisplayName());
            return;
        }

        try {
            DB db = client.getDB("admin");
            BasicDBObject dbObject = new BasicDBObject("replSetInitiate", config);
            CommandResult statusResult = db.command(dbObject);
            if (!statusResult.ok()) {
                LOG.error("Failed intiating MongoDB replica set {} on entity {}: {}",
                        new Object[] { replicaSetName, this, statusResult.getErrorMessage() });
                LOG.error("Failed intiating MongoDB replica set {} on entity {} - config is: {}",
                        new Object[] { replicaSetName, this, config.toString() });
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("Completed intiating MongoDB replica set {} on entity {}", replicaSetName, this);
            }
        } finally {
            client.close();
        }
    }

    @Override
    public void addToReplicaSet(MongoDbServer secondary) {
        throw new UnsupportedOperationException();
    }

    private static class ServerStatsCallable implements Callable<BasicBSONObject> {

        private final MongoDbServerImpl entity;

        ServerStatsCallable(MongoDbServerImpl entity) {
            this.entity = entity;
        }

        @Override
        public BasicBSONObject call() throws Exception {
            MongoClient client = getMongoClient(entity);
            if (client == null) return null;

            try {
                // Choose an existing database to connect to, or test if none exist.
                String dbName = Iterables.getFirst(client.getDatabaseNames(), "test");

                DB db = client.getDB(dbName);
                CommandResult statusResult = db.command("serverStatus");
                if (!statusResult.ok()) {
                    LOG.warn("No serverStatus data for {}: {}", entity, statusResult.getErrorMessage());
                    return null;
                } else {
                    return statusResult;
                }
            } finally {
                client.close();
            }
        }

    }

    private static MongoClient getMongoClient(MongoDbServerImpl entity) {
        if (!entity.getAttribute(SERVICE_UP)) {
            LOG.debug("No serverStatus data for {}: Service not up", entity);
            return null;
        }
        String hostname = entity.getAttribute(SoftwareProcess.HOSTNAME);
        Integer port = entity.getAttribute(PORT);
        MongoClient client = null;
        try {
            client = new MongoClient(hostname, port);
        } catch (UnknownHostException e) {
            LOG.warn("No serverStatus data for {}: {}", entity, e.getMessage());
            return null;
        }
        return client;
    }

}
