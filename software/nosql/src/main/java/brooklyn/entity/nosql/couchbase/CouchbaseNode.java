package brooklyn.entity.nosql.couchbase;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(CouchbaseNodeImpl.class)
public interface CouchbaseNode extends SoftwareProcess {

    @SetFromFlag("adminUsername")
    ConfigKey<String> COUCHBASE_ADMIN_USERNAME = ConfigKeys.newStringConfigKey("couchbase.adminUsername", "Username for the admin user on the node", "Administrator");

    @SetFromFlag("adminPassword")
    ConfigKey<String> COUCHBASE_ADMIN_PASSWORD = ConfigKeys.newStringConfigKey("couchbase.adminPassword", "Username for the admin user on the node", "password");

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "2.5.1");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://packages.couchbase.com/releases/${version}/couchbase-server-enterprise_${version}_${driver.osTag}");

    @SetFromFlag("clusterInitRamSize")
    BasicAttributeSensorAndConfigKey<Integer> COUCHBASE_CLUSTER_INIT_RAM_SIZE = new BasicAttributeSensorAndConfigKey<Integer>(
            Integer.class, "couchbase.clusterInitRamSize", "initial ram size of the cluster", 300);

    PortAttributeSensorAndConfigKey COUCHBASE_WEB_ADMIN_PORT = new PortAttributeSensorAndConfigKey("couchbase.webAdminPort", "Web Administration Port", "8091+");
    PortAttributeSensorAndConfigKey COUCHBASE_API_PORT = new PortAttributeSensorAndConfigKey("couchbase.apiPort", "Couchbase API Port", "8092+");
    PortAttributeSensorAndConfigKey COUCHBASE_INTERNAL_BUCKET_PORT = new PortAttributeSensorAndConfigKey("couchbase.internalBucketPort", "Internal Bucket Port", "11209");
    PortAttributeSensorAndConfigKey COUCHBASE_INTERNAL_EXTERNAL_BUCKET_PORT = new PortAttributeSensorAndConfigKey("couchbase.internalExternalBucketPort", "Internal/External Bucket Port", "11210");
    PortAttributeSensorAndConfigKey COUCHBASE_CLIENT_INTERFACE_PROXY = new PortAttributeSensorAndConfigKey("couchbase.clientInterfaceProxy", "Client interface (proxy)", "11211");
    PortAttributeSensorAndConfigKey COUCHBASE_INCOMING_SSL_PROXY = new PortAttributeSensorAndConfigKey("couchbase.incomingSslProxy", "Incoming SSL Proxy", "11214");
    PortAttributeSensorAndConfigKey COUCHBASE_INTERNAL_OUTGOING_SSL_PROXY = new PortAttributeSensorAndConfigKey("couchbase.internalOutgoingSslProxy", "Internal Outgoing SSL Proxy", "11215");
    PortAttributeSensorAndConfigKey COUCHBASE_REST_HTTPS_FOR_SSL = new PortAttributeSensorAndConfigKey("couchbase.internalRestHttpsForSsl", "Internal REST HTTPS for SSL", "18091");
    PortAttributeSensorAndConfigKey COUCHBASE_CAPI_HTTPS_FOR_SSL = new PortAttributeSensorAndConfigKey("couchbase.internalCapiHttpsForSsl", "Internal CAPI HTTPS for SSL", "18092");
    PortAttributeSensorAndConfigKey ERLANG_PORT_MAPPER = new PortAttributeSensorAndConfigKey("couchbase.erlangPortMapper", "Erlang Port Mapper Daemon Listener Port (epmd)", "4369");
    PortAttributeSensorAndConfigKey NODE_DATA_EXCHANGE_PORT_RANGE_START = new PortAttributeSensorAndConfigKey("couchbase.nodeDataExchangePortRangeStart", "Node data exchange Port Range Start", "21100+");
    PortAttributeSensorAndConfigKey NODE_DATA_EXCHANGE_PORT_RANGE_END = new PortAttributeSensorAndConfigKey("couchbase.nodeDataExchangePortRangeEnd", "Node data exchange Port Range End", "21199+");

    AttributeSensor<String> COUCHBASE_WEB_ADMIN_URL = Sensors.newStringSensor("couchbase.webAdminUrl", "The administration console Url for this Couchbase node");
    AttributeSensor<Boolean> IS_PRIMARY_NODE = Sensors.newBooleanSensor("couchbase.isPrimaryNode", "flag to determine if the current couchbase node is the primary node for the cluster");
    AttributeSensor<Boolean> IS_IN_CLUSTER = Sensors.newBooleanSensor("couchbase.isInCluster", "flag to determine if the current couchbase node has been added to a cluster");

    public static final MethodEffector<Void> SERVER_ADD = new MethodEffector<Void>(CouchbaseNode.class, "serverAdd");
    public static final MethodEffector<Void> REBALANCE = new MethodEffector<Void>(CouchbaseNode.class, "rebalance");

    @Effector(description = "add a server to a cluster")
    public void serverAdd(@EffectorParam(name = "serverHostname") String serverToAdd, @EffectorParam(name = "username") String username, @EffectorParam(name = "password") String password);

    @Effector(description = "rebalance the couchbase cluster")
    public void rebalance();


}
