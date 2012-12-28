package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.adapter.FunctionSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;
import com.google.common.collect.ImmutableMap;
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

public class MongoDbServer extends SoftwareProcessEntity {

    protected static final Logger LOG = LoggerFactory.getLogger(MongoDbServer.class);

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey PORT =
            new PortAttributeSensorAndConfigKey("mongodb.server.port", "Server port", "27017+");

    public static final BasicConfigKey<String> VERSION = new BasicConfigKey<String>(String.class,
            "mongodb.version", "Required version of Mongo", "2.2.2");

    public static final BasicConfigKey<String> CONFIG_URL = new BasicConfigKey<String>(String.class,
            "mongodb.config.url", "URL where a Mongo configuration file can be found", "classpath://default-mongodb.conf");

    // Can also treat this as a Map
    public static final BasicAttributeSensor<BasicBSONObject> STATUS = new BasicAttributeSensor<BasicBSONObject>(BasicBSONObject.class,
            "mongodb.server.status", "Server status");

    public static final BasicAttributeSensor<Double> UPTIME_SECONDS = new BasicAttributeSensor<Double>(Double.class,
            "mongodb.server.uptime", "Server uptime in seconds");

    public static final BasicAttributeSensor<Long> OPCOUNTERS_INSERTS = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.insert", "Server inserts");

    public static final BasicAttributeSensor<Long> OPCOUNTERS_QUERIES = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.query", "Server queries");

    public static final BasicAttributeSensor<Long> OPCOUNTERS_UPDATES = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.update", "Server updates");

    public static final BasicAttributeSensor<Long> OPCOUNTERS_DELETES = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.delete", "Server deletes");

    public static final BasicAttributeSensor<Long> OPCOUNTERS_GETMORE = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.getmore", "Server getmores");

    public static final BasicAttributeSensor<Long> OPCOUNTERS_COMMAND = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.command", "Server commands");

    public static final BasicAttributeSensor<Long> NETWORK_BYTES_IN = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.network.bytesIn", "Server deletes");

    public static final BasicAttributeSensor<Long> NETWORK_BYTES_OUT = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.network.bytesOut", "Server deletes");

    public static final BasicAttributeSensor<Long> NETWORK_NUM_REQUESTS = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.network.numRequests", "Server deletes");


    public MongoDbServer(Map flags){
        this(flags, null);
    }

    public MongoDbServer(Map flags, Entity owner) {
        super(flags, owner);
    }

    @Override
    public Class getDriverInterface() {
        return MongoDbDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        FunctionSensorAdapter serviceUpAdapter = sensorRegistry.register(new FunctionSensorAdapter(
                ImmutableMap.of("period", 10 * 1000),
                new Callable<Boolean>() {
                    public Boolean call() {
                        return getDriver().isRunning();
                    }
                }));
        serviceUpAdapter.poll(SERVICE_UP);

        FunctionSensorAdapter serviceStatsAdapter = sensorRegistry.register(new FunctionSensorAdapter(
                ImmutableMap.of("period", 5 * 1000), new ServerStatsCallable(this)));
        serviceStatsAdapter.poll(STATUS);

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

    private static class ServerStatsCallable implements Callable<BasicBSONObject> {

        private final MongoDbServer entity;

        ServerStatsCallable(MongoDbServer entity) {
            this.entity = entity;
        }

        @Override
        public BasicBSONObject call() throws Exception {
            if (!entity.getAttribute(SERVICE_UP)) {
                LOG.debug("No serverStatus data: Service not up");
                return null;
            }
            String hostname = entity.getAttribute(SoftwareProcessEntity.HOSTNAME);
            Integer port = entity.getAttribute(PORT);
            MongoClient client = null;
            try {
                client = new MongoClient(hostname, port);
            } catch (UnknownHostException e) {
                LOG.warn("No serverStatus data: " + e.getMessage());
                return null;
            }

            // Choose an existing database to connect to, or test if none exist.
            String dbName = Iterables.getFirst(client.getDatabaseNames(), "test");
            DB db = client.getDB(dbName);
            CommandResult statusResult = db.command("serverStatus");
            client.close();
            if (!statusResult.ok()) {
                LOG.warn("No serverStatus data: " + statusResult.getErrorMessage());
                return null;
            } else {
                return statusResult;
            }
        }
    }

}
