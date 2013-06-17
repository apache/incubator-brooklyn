/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.IntegerAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.LongAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a Cassandra node in a {@link CassandraCluster}.
 */
@ImplementedBy(CassandraNodeImpl.class)
public interface CassandraNode extends SoftwareProcess, UsesJmx {

    @SetFromFlag("version")
    BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "1.2.2");

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

    @SetFromFlag("gossipPort")
    PortAttributeSensorAndConfigKey GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.gossip.port", "Cassandra Gossip communications port", PortRanges.fromString("7000+"));

    @SetFromFlag("sslGgossipPort")
    PortAttributeSensorAndConfigKey SSL_GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.ssl-gossip.port", "Cassandra Gossip SSL communications port", PortRanges.fromString("7001+"));

    @SetFromFlag("thriftPort")
    PortAttributeSensorAndConfigKey THRIFT_PORT = new PortAttributeSensorAndConfigKey("cassandra.thrift.port", "Cassandra Thrift RPC port", PortRanges.fromString("9160+"));

    @SetFromFlag("cassandraConfigTemplateUrl")
    BasicAttributeSensorAndConfigKey<String> CASSANDRA_CONFIG_TEMPLATE_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.config.templateUrl", "Template file (in freemarker format) for the cassandra.yaml config file", 
            "classpath://brooklyn/entity/nosql/cassandra/cassandra.yaml");

    @SetFromFlag("cassandraConfigFileName")
    BasicAttributeSensorAndConfigKey<String> CASSANDRA_CONFIG_FILE_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "cassandra.config.fileName", "Name for the copied config file", "cassandra.yaml");

    AttributeSensor<Long> TOKEN = new LongAttributeSensor("cassandra.token", "Cassandra Token");

    AttributeSensor<Integer> PEERS = new IntegerAttributeSensor( "cassandra.peers", "Number of peers in cluster");

    /* Metrics for read/write performance. */

    AttributeSensor<Long> READ_PENDING = new LongAttributeSensor("cassandra.read.pending", "Current pending ReadStage tasks");
    AttributeSensor<Integer> READ_ACTIVE = new IntegerAttributeSensor("cassandra.read.active", "Current active ReadStage tasks");
    AttributeSensor<Long> READ_COMPLETED = new LongAttributeSensor("cassandra.read.completed", "Total completed ReadStage tasks");
    AttributeSensor<Long> WRITE_PENDING = new LongAttributeSensor("cassandra.write.pending", "Current pending MutationStage tasks");
    AttributeSensor<Integer> WRITE_ACTIVE = new IntegerAttributeSensor("cassandra.write.active", "Current active MutationStage tasks");
    AttributeSensor<Long> WRITE_COMPLETED = new LongAttributeSensor("cassandra.write.completed", "Total completed MutationStage tasks");

    ConfigKey<String> SEEDS = CassandraCluster.SEEDS;

    Integer getGossipPort();

    Integer getSslGossipPort();

    Integer getThriftPort();

    String getClusterName();

    String getSeeds();

    Long getToken();

    void setToken(String token);
}
