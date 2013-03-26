package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.bson.BasicBSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
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
                    }
                }
        });
    }

    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();
        if (serviceStats != null) serviceStats.stop();
    }

    private static class ServerStatsCallable implements Callable<BasicBSONObject> {

        private final MongoDbServerImpl entity;

        ServerStatsCallable(MongoDbServerImpl entity) {
            this.entity = entity;
        }

        @Override
        public BasicBSONObject call() throws Exception {
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

            // Choose an existing database to connect to, or test if none exist.
            String dbName = Iterables.getFirst(client.getDatabaseNames(), "test");

            try {
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

}
