/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.java.UsesJavaMXBeans;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a Cassandra node in a {@link CassandraCluster}.
 */
@ImplementedBy(CassandraNodeImpl.class)
public interface CassandraNode extends DatastoreMixins.DatastoreCommon, SoftwareProcess, UsesJmx, UsesJavaMXBeans, DatastoreMixins.HasDatastoreUrl, DatastoreMixins.CanExecuteScript {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.11");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "${driver.mirrorUrl}/${version}/apache-cassandra-${version}-bin.tar.gz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    ConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "cassandra.install.mirror.url", "URL of mirror", "http://www.mirrorservice.org/sites/ftp.apache.org/cassandra");

    @SetFromFlag("tgzUrl")
    ConfigKey<String> TGZ_URL = new BasicConfigKey<String>(String.class, "cassandra.install.tgzUrl", "URL of TGZ download file");

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = CassandraCluster.CLUSTER_NAME;

    @SetFromFlag("snitchName")
    ConfigKey<String> ENDPOINT_SNITCH_NAME = CassandraCluster.ENDPOINT_SNITCH_NAME;

    @SetFromFlag("gossipPort")
    PortAttributeSensorAndConfigKey GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.gossip.port", "Cassandra Gossip communications port", PortRanges.fromString("7000+"));

    @SetFromFlag("sslGgossipPort")
    PortAttributeSensorAndConfigKey SSL_GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.ssl-gossip.port", "Cassandra Gossip SSL communications port", PortRanges.fromString("7001+"));

    @SetFromFlag("thriftPort")
    PortAttributeSensorAndConfigKey THRIFT_PORT = new PortAttributeSensorAndConfigKey("cassandra.thrift.port", "Cassandra Thrift RPC port", PortRanges.fromString("9160+"));

    @SetFromFlag("customSnitchJarUrl")
    ConfigKey<String> CUSTOM_SNITCH_JAR_URL = ConfigKeys.newStringConfigKey("cassandra.config.customSnitchUrl", 
            "URL for a jar file to be uploaded (e.g. \"classpath://brooklyn/entity/nosql/cassandra/multiCloudSnitch.jar\"); defaults to null which means nothing to upload", 
            null);

    @SetFromFlag("cassandraConfigTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> CASSANDRA_CONFIG_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.config.templateUrl", "Template file (in freemarker format) for the cassandra.yaml config file", 
            "classpath://brooklyn/entity/nosql/cassandra/cassandra.yaml");

    @SetFromFlag("cassandraConfigFileName")
    BasicAttributeSensorAndConfigKey<String> CASSANDRA_CONFIG_FILE_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.config.fileName", "Name for the copied config file", "cassandra.yaml");

    @SetFromFlag("cassandraRackdcConfigTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> CASSANDRA_RACKDC_CONFIG_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.config.rackdc.templateUrl", "Template file (in freemarker format) for the cassandra-rackdc.properties config file", 
            "classpath://brooklyn/entity/nosql/cassandra/cassandra-rackdc.properties");

    @SetFromFlag("cassandraRackdcConfigFileName")
    BasicAttributeSensorAndConfigKey<String> CASSANDRA_RACKDC_CONFIG_FILE_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.config.rackdc.fileName", "Name for the copied rackdc config file", "cassandra-rackdc.properties");
    
    @SetFromFlag("datacenterName")
    BasicAttributeSensorAndConfigKey<String> DATACENTER_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.replication.datacenterName", "Datacenter name (used for configuring replication)", 
            null);

    @SetFromFlag("rackName")
    BasicAttributeSensorAndConfigKey<String> RACK_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.replication.rackName", "Rack name (used for configuring replication)", 
            null);

    AttributeSensor<Long> TOKEN = Sensors.newLongSensor("cassandra.token", "Cassandra Token");

    AttributeSensor<Integer> PEERS = Sensors.newIntegerSensor( "cassandra.peers", "Number of peers in cluster");

    AttributeSensor<Integer> LIVE_NODE_COUNT = Sensors.newIntegerSensor( "cassandra.liveNodeCount", "Number of live nodes in cluster");

    /* Metrics for read/write performance. */

    AttributeSensor<Long> READ_PENDING = Sensors.newLongSensor("cassandra.read.pending", "Current pending ReadStage tasks");
    AttributeSensor<Integer> READ_ACTIVE = Sensors.newIntegerSensor("cassandra.read.active", "Current active ReadStage tasks");
    AttributeSensor<Long> READ_COMPLETED = Sensors.newLongSensor("cassandra.read.completed", "Total completed ReadStage tasks");
    AttributeSensor<Long> WRITE_PENDING = Sensors.newLongSensor("cassandra.write.pending", "Current pending MutationStage tasks");
    AttributeSensor<Integer> WRITE_ACTIVE = Sensors.newIntegerSensor("cassandra.write.active", "Current active MutationStage tasks");
    AttributeSensor<Long> WRITE_COMPLETED = Sensors.newLongSensor("cassandra.write.completed", "Total completed MutationStage tasks");
    
    AttributeSensor<Boolean> SERVICE_UP_JMX = Sensors.newBooleanSensor("cassandra.service.jmx.up", "Whether JMX is up for this service");
    AttributeSensor<Long> THRIFT_PORT_LATENCY = Sensors.newLongSensor("cassandra.thrift.latency", "Latency for thrift port connection (ms) or null if down");

    AttributeSensor<Double> READS_PER_SECOND_LAST = Sensors.newDoubleSensor("cassandra.reads.perSec.last", "Reads/sec (last datapoint)");
    AttributeSensor<Double> WRITES_PER_SECOND_LAST = Sensors.newDoubleSensor("cassandra.write.perSec.last", "Writes/sec (last datapoint)");

    AttributeSensor<Double> THRIFT_PORT_LATENCY_IN_WINDOW = Sensors.newDoubleSensor("cassandra.thrift.latency.windowed", "Latency for thrift port (ms, averaged over time window)");
    AttributeSensor<Double> READS_PER_SECOND_IN_WINDOW = Sensors.newDoubleSensor("cassandra.reads.perSec.windowed", "Reads/sec (over time window)");
    AttributeSensor<Double> WRITES_PER_SECOND_IN_WINDOW = Sensors.newDoubleSensor("cassandra.writes.perSec.windowed", "Writes/sec (over time window)");

    @SuppressWarnings({ "rawtypes", "unchecked" })
    ConfigKey<Set<Entity>> INITIAL_SEEDS = (ConfigKey)ConfigKeys.newConfigKey(Set.class, "cassandra.cluster.seeds.initial", 
            "List of cluster nodes to seed this node");

    ConfigKey<Integer> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, 3*60);

    public static Effector<String> EXECUTE_SCRIPT = CassandraCluster.EXECUTE_SCRIPT;

    /* Accessors used from template */
    
    Integer getGossipPort();
    Integer getSslGossipPort();
    Integer getThriftPort();
    String getClusterName();
    String getListenAddress();
    String getBroadcastAddress();
    String getSeeds();
    Long getToken();

    /* For configuration */
    
    void setToken(String token);
    
    /* Using Cassandra */
    
    String executeScript(String commands);
    
}
