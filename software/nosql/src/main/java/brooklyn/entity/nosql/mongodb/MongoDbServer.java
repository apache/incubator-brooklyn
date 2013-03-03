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
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "2.2.3");

    // e.g. http://fastdl.mongodb.org/linux/mongodb-linux-x86_64-2.2.2.tgz,
    // http://fastdl.mongodb.org/osx/mongodb-osx-x86_64-2.2.2.tgz
    // http://downloads.mongodb.org/win32/mongodb-win32-x86_64-1.8.5.zip
    // Note Windows download is a zip.
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://fastdl.mongodb.org/${driver.osDir}/${driver.osTag}-${version}.tgz");

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey PORT =
            new PortAttributeSensorAndConfigKey("mongodb.server.port", "Server port", "27017+");

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


}
