package brooklyn.entity.nosql.mongodb;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;
import org.bson.BasicBSONObject;

@Catalog(name="MongoDB Server", description="MongoDB (from \"humongous\") is a scalable, high-performance, open source NoSQL database", iconUrl="classpath:///mongodb-logo.png")
@ImplementedBy(MongoDbServerImpl.class)
public interface MongoDbServer extends SoftwareProcess {

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "2.2.3");

    // e.g. http://fastdl.mongodb.org/linux/mongodb-linux-x86_64-2.2.2.tgz,
    // http://fastdl.mongodb.org/osx/mongodb-osx-x86_64-2.2.2.tgz
    // http://downloads.mongodb.org/win32/mongodb-win32-x86_64-1.8.5.zip
    // Note Windows download is a zip.
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://fastdl.mongodb.org/${driver.osDir}/${driver.osTag}-${version}.tgz");

    @SetFromFlag("port")
    PortAttributeSensorAndConfigKey PORT =
            new PortAttributeSensorAndConfigKey("mongodb.server.port", "Server port", "27017+");

    @SetFromFlag("dataDirectory")
    BasicConfigKey<String> DATA_DIRECTORY = new BasicConfigKey<String>(String.class,
            "mongodb.data.directory", "Data directory to store MongoDB journals");

    public static final BasicAttributeSensorAndConfigKey<String> REPLICA_SET_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class,
            "mongodb.server.replicaSet.name", "The name of the replica set that the server belongs to");

    @SetFromFlag("mongodbConfTemplateUrl")
    BasicConfigKey<String> MONGODB_CONF_TEMPLATE_URL = new BasicConfigKey<String>(String.class,
            "mongodb.config.url", "Template file (in freemarker format) for a a Mongo configuration file",
            "classpath://brooklyn/entity/nosql/mongodb/default-mongodb.conf");

    // Can also treat this as a Map
    BasicAttributeSensor<BasicBSONObject> STATUS = new BasicAttributeSensor<BasicBSONObject>(BasicBSONObject.class,
            "mongodb.server.status", "Server status");

    BasicAttributeSensor<Double> UPTIME_SECONDS = new BasicAttributeSensor<Double>(Double.class,
            "mongodb.server.uptime", "Server uptime in seconds");

    BasicAttributeSensor<Long> OPCOUNTERS_INSERTS = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.insert", "Server inserts");

    BasicAttributeSensor<Long> OPCOUNTERS_QUERIES = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.query", "Server queries");

    BasicAttributeSensor<Long> OPCOUNTERS_UPDATES = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.update", "Server updates");

    BasicAttributeSensor<Long> OPCOUNTERS_DELETES = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.delete", "Server deletes");

    BasicAttributeSensor<Long> OPCOUNTERS_GETMORE = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.getmore", "Server getmores");

    BasicAttributeSensor<Long> OPCOUNTERS_COMMAND = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.opcounters.command", "Server commands");

    BasicAttributeSensor<Long> NETWORK_BYTES_IN = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.network.bytesIn", "Server incoming network traffic (in bytes)");

    BasicAttributeSensor<Long> NETWORK_BYTES_OUT = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.network.bytesOut", "Server outgoing network traffic (in bytes)");

    BasicAttributeSensor<Long> NETWORK_NUM_REQUESTS = new BasicAttributeSensor<Long>(Long.class,
            "mongodb.server.network.numRequests", "Server network requests");

    public static final BasicAttributeSensor<Boolean> REPLICA_SET_ENABLED = new BasicAttributeSensor<Boolean>(Boolean.class,
            "mongodb.server.replicaSet.enabled", "True if this server was started to be part of a replica set");

    public static final BasicAttributeSensor<Boolean> REPLICA_SET_MASTER = new BasicAttributeSensor<Boolean>(Boolean.class,
            "mongodb.server.replicaSet.isMaster", "True if this server is the write master for the replica set");

    public static final BasicAttributeSensor<Boolean> REPLICA_SET_SECONDARY = new BasicAttributeSensor<Boolean>(Boolean.class,
            "mongodb.server.replicaSet.isSecondary", "True if this server is a secondary server in the replica set");

    public static final BasicAttributeSensor<String> REPLICA_SET_HOSTS = new BasicAttributeSensor<String>(String.class,
            "mongodb.server.replicaSet.hosts", "All hosts in the replica set (comma-seperated hostname:port)");

    public static final BasicAttributeSensor<Integer> REPLICA_SET_NUMBER_OF_HOSTS = new BasicAttributeSensor<Integer>(Integer.class,
            "mongodb.server.replicaSet.numberOfHosts", "Number of hosts in the replica set");

    public static final BasicAttributeSensor<String> REPLICA_SET_PRIMARY = new BasicAttributeSensor<String>(String.class,
            "mongodb.server.replicaSet.primary", "The name of the primary host in the replica set");

    Integer getServerPort();

    public static final BasicAttributeSensor<String> REPLICA_SET_ME = new BasicAttributeSensor<String>(String.class,
            "mongodb.server.replicaSet.me", "This server's host name in the replica");

    void initializeReplicaSet();

    void addToReplicaSet(MongoDbServer server);
}
