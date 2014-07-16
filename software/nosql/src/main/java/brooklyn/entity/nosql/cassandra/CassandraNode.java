/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.cassandra;

import java.math.BigInteger;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
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
import brooklyn.util.time.Duration;

import com.google.common.reflect.TypeToken;

/**
 * An {@link brooklyn.entity.Entity} that represents a Cassandra node in a {@link CassandraDatacenter}.
 */
@ImplementedBy(CassandraNodeImpl.class)
public interface CassandraNode extends DatastoreMixins.DatastoreCommon, SoftwareProcess, UsesJmx, UsesJavaMXBeans, DatastoreMixins.HasDatastoreUrl, DatastoreMixins.CanExecuteScript {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.16");
    // when this changes remember to put a copy under releng2:/var/www/developer/brooklyn/repository/ !
    // TODO experiment with supporting 2.0.x

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "${driver.mirrorUrl}/${version}/apache-cassandra-${version}-bin.tar.gz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    ConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "cassandra.install.mirror.url", "URL of mirror", 
        "http://www.mirrorservice.org/sites/ftp.apache.org/cassandra"
        // for older versions, but slower:
//        "http://archive.apache.org/dist/cassandra/"
        );

    @SetFromFlag("tgzUrl")
    ConfigKey<String> TGZ_URL = new BasicConfigKey<String>(String.class, "cassandra.install.tgzUrl", "URL of TGZ download file");

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = CassandraDatacenter.CLUSTER_NAME;

    @SetFromFlag("snitchName")
    ConfigKey<String> ENDPOINT_SNITCH_NAME = CassandraDatacenter.ENDPOINT_SNITCH_NAME;

    @SetFromFlag("gossipPort")
    PortAttributeSensorAndConfigKey GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.gossip.port", "Cassandra Gossip communications port", PortRanges.fromString("7000+"));

    @SetFromFlag("sslGgossipPort")
    PortAttributeSensorAndConfigKey SSL_GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.ssl-gossip.port", "Cassandra Gossip SSL communications port", PortRanges.fromString("7001+"));

    @SetFromFlag("thriftPort")
    PortAttributeSensorAndConfigKey THRIFT_PORT = new PortAttributeSensorAndConfigKey("cassandra.thrift.port", "Cassandra Thrift RPC port", PortRanges.fromString("9160+"));

    @SetFromFlag("nativePort")
    PortAttributeSensorAndConfigKey NATIVE_TRANSPORT_PORT = new PortAttributeSensorAndConfigKey("cassandra.native.port", "Cassandra Native Transport port", PortRanges.fromString("9042+"));

    @SetFromFlag("rmiRegistryPort")
    // cassandra nodetool and others want 7199 - not required, but useful
    PortAttributeSensorAndConfigKey RMI_REGISTRY_PORT = new PortAttributeSensorAndConfigKey(UsesJmx.RMI_REGISTRY_PORT, 
        PortRanges.fromInteger(7199));

    // some of the cassandra tooing (eg nodetool) use RMI, but we want JMXMP, so do both!
    ConfigKey<JmxAgentModes> JMX_AGENT_MODE = ConfigKeys.newConfigKeyWithDefault(UsesJmx.JMX_AGENT_MODE, JmxAgentModes.JMXMP_AND_RMI);
    
    @SetFromFlag("customSnitchJarUrl")
    ConfigKey<String> CUSTOM_SNITCH_JAR_URL = ConfigKeys.newStringConfigKey("cassandra.config.customSnitchUrl", 
            "URL for a jar file to be uploaded (e.g. \"classpath://brooklyn/entity/nosql/cassandra/cassandra-multicloud-snitch.jar\"); defaults to null which means nothing to upload", 
            null);

    @SetFromFlag("cassandraConfigTemplateUrl")
    ConfigKey<String> CASSANDRA_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "cassandra.config.templateUrl", "A URL (in freemarker format) for a cassandra.yaml config file (in freemarker format)", 
            "classpath://brooklyn/entity/nosql/cassandra/cassandra-${entity.majorMinorVersion}.yaml");

    @SetFromFlag("cassandraConfigFileName")
    ConfigKey<String> CASSANDRA_CONFIG_FILE_NAME = ConfigKeys.newStringConfigKey(
            "cassandra.config.fileName", "Name for the copied config file", "cassandra.yaml");

    @SetFromFlag("cassandraRackdcConfigTemplateUrl")
    ConfigKey<String> CASSANDRA_RACKDC_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "cassandra.config.rackdc.templateUrl", "Template file (in freemarker format) for the cassandra-rackdc.properties config file", 
            "classpath://brooklyn/entity/nosql/cassandra/cassandra-rackdc.properties");

    @SetFromFlag("cassandraRackdcConfigFileName")
    ConfigKey<String> CASSANDRA_RACKDC_CONFIG_FILE_NAME = ConfigKeys.newStringConfigKey(
            "cassandra.config.rackdc.fileName", "Name for the copied rackdc config file (used for configuring replication, when a suitable snitch is used)", "cassandra-rackdc.properties");
    
    @SetFromFlag("datacenterName")
    BasicAttributeSensorAndConfigKey<String> DATACENTER_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.replication.datacenterName", "Datacenter name (used for configuring replication, when a suitable snitch is used)", 
            null);

    @SetFromFlag("rackName")
    BasicAttributeSensorAndConfigKey<String> RACK_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.replication.rackName", "Rack name (used for configuring replication, when a suitable snitch is used)", 
            null);

    ConfigKey<Integer> NUM_TOKENS_PER_NODE = ConfigKeys.newIntegerConfigKey("cassandra.numTokensPerNode",
            "Number of tokens per node; if using vnodes, should set this to a value like 256",
            1);
    
    /**
     * @deprecated since 0.7; use {@link #TOKENS}
     */
    @SetFromFlag("token")
    @Deprecated
    BasicAttributeSensorAndConfigKey<BigInteger> TOKEN = new BasicAttributeSensorAndConfigKey<BigInteger>(
            BigInteger.class, "cassandra.token", "Cassandra Token");

    @SetFromFlag("tokens")
    BasicAttributeSensorAndConfigKey<Set<BigInteger>> TOKENS = new BasicAttributeSensorAndConfigKey<Set<BigInteger>>(
            new TypeToken<Set<BigInteger>>() {}, "cassandra.tokens", "Cassandra Tokens");

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

    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.START_TIMEOUT, Duration.FIVE_MINUTES);
    
    ConfigKey<String> LISTEN_ADDRESS_SENSOR = ConfigKeys.newStringConfigKey("cassandra.listenAddressSensor", "sensor name from which to take the listen address; default (null) is a smart lookup");
    ConfigKey<String> BROADCAST_ADDRESS_SENSOR = ConfigKeys.newStringConfigKey("cassandra.broadcastAddressSensor", "sensor name from which to take the broadcast address; default (null) is a smart lookup");
    ConfigKey<String> RPC_ADDRESS_SENSOR = ConfigKeys.newStringConfigKey("cassandra.rpcAddressSensor", "sensor name from which to take the RPC address; default (null) is 0.0.0.0");

    Effector<String> EXECUTE_SCRIPT = CassandraDatacenter.EXECUTE_SCRIPT;

    /* Accessors used from template */
    
    String getMajorMinorVersion();
    Integer getGossipPort();
    Integer getSslGossipPort();
    Integer getThriftPort();
    Integer getNativeTransportPort();
    String getClusterName();
    String getListenAddress();
    String getBroadcastAddress();
    String getRpcAddress();
    String getSeeds();
    
    String getPrivateIp();
    String getPublicIp();
    
    /**
     * In range 0 to (2^127)-1; or null if not yet set or known.
     * Returns the first token if more than one token.
     * @deprecated since 0.7; see {@link #getTokens()}
     */
    @Deprecated
    BigInteger getToken();

    int getNumTokensPerNode();

    Set<BigInteger> getTokens();

    /**
     * string value of token (with no commas, which freemarker introduces!) or blank if none
     * @deprecated since 0.7; use {@link #getTokensAsString()}
     */
    @Deprecated
    String getTokenAsString();

    /** string value of comma-separated tokens; or blank if none */
    String getTokensAsString();

    /* For configuration */
    
    void setToken(String token);
    
    /* Using Cassandra */
    
    String executeScript(String commands);
    
}
