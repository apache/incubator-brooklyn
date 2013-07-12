package brooklyn.entity.nosql.mongodb;

import org.bson.BasicBSONObject;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="MongoDB Server",
    description="MongoDB (from \"humongous\") is a scalable, high-performance, open source NoSQL database",
    iconUrl="classpath:///mongodb-logo.png")
@ImplementedBy(MongoDBServerImpl.class)
public interface MongoDBServer extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "2.2.4");

    // e.g. http://fastdl.mongodb.org/linux/mongodb-linux-x86_64-2.2.2.tgz,
    // http://fastdl.mongodb.org/osx/mongodb-osx-x86_64-2.2.2.tgz
    // http://downloads.mongodb.org/win32/mongodb-win32-x86_64-1.8.5.zip
    // Note Windows download is a zip.
    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://fastdl.mongodb.org/${driver.osDir}/${driver.osTag}-${version}.tgz");

    @SetFromFlag("port")
    PortAttributeSensorAndConfigKey PORT =
            new PortAttributeSensorAndConfigKey("mongodb.server.port", "Server port", "27017+");

    @SetFromFlag("dataDirectory")
    ConfigKey<String> DATA_DIRECTORY = new BasicConfigKey<String>(String.class,
            "mongodb.data.directory", "Data directory to store MongoDB journals");

    @SetFromFlag("mongodbConfTemplateUrl")
    ConfigKey<String> MONGODB_CONF_TEMPLATE_URL = new BasicConfigKey<String>(String.class,
            "mongodb.config.url", "Template file (in freemarker format) for a MongoDB configuration file",
            "classpath://brooklyn/entity/nosql/mongodb/default-mongodb.conf");

    @SetFromFlag("enableRestInterface")
    ConfigKey<Boolean> ENABLE_REST_INTERFACE = new BasicConfigKey<Boolean>(Boolean.class,
            "mongodb.config.enable_rest", "Adds --rest to server startup flags when true", Boolean.FALSE);

    AttributeSensor<String> HTTP_INTERFACE_URL = new BasicAttributeSensor<String>(String.class,
            "mongodb.server.http_interface", "URL of the server's HTTP console");

    // BasicBSONObjects are Maps
    AttributeSensor<BasicBSONObject> STATUS = new BasicAttributeSensor<BasicBSONObject>(BasicBSONObject.class,
            "mongodb.server.status", "Server status");

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
    ConfigKey<Boolean> REPLICA_SET_ENABLED = new BasicConfigKey<Boolean>(Boolean.class,
            "mongodb.server.replicaSet.enabled", "True if this server was started to be part of a replica set", Boolean.FALSE);

    AttributeSensorAndConfigKey<String, String> REPLICA_SET_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class,
            "mongodb.server.replicaSet.name", "The name of the replica set that the server belongs to");

    AttributeSensor<ReplicaSetMemberStatus> REPLICA_SET_MEMBER_STATUS = new BasicAttributeSensor<ReplicaSetMemberStatus>(
            ReplicaSetMemberStatus.class, "mongodb.server.replicaSet.memberStatus", "The status of this server in the replica set");

    AttributeSensor<Boolean> REPLICA_SET_PRIMARY = Sensors.newBooleanSensor(
            "mongodb.server.replicaSet.isPrimary", "True if this server is the write master for the replica set");

    AttributeSensor<Boolean> REPLICA_SET_SECONDARY = Sensors.newBooleanSensor(
            "mongodb.server.replicaSet.isSecondary", "True if this server is a secondary server in the replica set");

    AttributeSensor<String> REPLICA_SET_PRIMARY_NAME = new BasicAttributeSensor<String>(String.class,
            "mongodb.server.replicaSet.primary", "The name of the primary host in the replica set");

    MongoClientSupport getClient();
}
