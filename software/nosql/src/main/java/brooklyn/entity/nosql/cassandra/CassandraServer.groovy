/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra;

import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.java.UsesJmx
import brooklyn.event.adapter.JmxHelper
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.util.flags.SetFromFlag


/**
 * An {@link brooklyn.entity.Entity} that represents an Cassandra service
 */
public class CassandraServer extends SoftwareProcessEntity implements UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(CassandraServer.class)

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "1.1.6" ]
    
    @SetFromFlag("clusterName")
    public static final BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = [ String, "cassandra.cluster.name", "Name of the Cassandra cluster", "Brooklyn Cassandra Cluster" ]
    
    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    public static final BasicConfigKey<String> MIRROR_URL = [ String, "cassandra.install.mirror.url", "URL of mirror",
        "http://www.mirrorservice.org/sites/ftp.apache.org/cassandra" ]

    @SetFromFlag("tgzUrl")
    public static final BasicConfigKey<String> TGZ_URL = [ String, "cassandra.install.tgzUrl", "URL of TGZ download file", null ]
    
    @SetFromFlag("gossipPort")
    public static final PortAttributeSensorAndConfigKey GOSSIP_PORT = [ "cassandra.gossip.port", "Cassandra Gossip communications port", "7000" ]
    
    @SetFromFlag("sslGgossipPort")
    public static final PortAttributeSensorAndConfigKey SSL_GOSSIP_PORT = [ "cassandra.ssl-gossip.port", "Cassandra Gossip SSL communications port", "7001" ]

    @SetFromFlag("thriftPort")
    public static final PortAttributeSensorAndConfigKey THRIFT_PORT = [ "cassandra.thrift.port", "Cassandra Thrift RPC port", "9160" ]

    public static final BasicAttributeSensor TOKEN = [ String, "cassandra.token", "Cassandra Token" ]

    public CassandraServer(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }
    
    public Integer getGossipPort() { return getAttribute(GOSSIP_PORT) }
    public Integer getSslGossipPort() { return getAttribute(SSL_GOSSIP_PORT) }
    public Integer getThriftPort() { return getAttribute(THRIFT_PORT) }
    public String getClusterName() { return getAttribute(CLUSTER_NAME) }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        // TODO What if port to use is the default?
        Collection<Integer> result = super.getRequiredOpenPorts()
        if (getConfig(GOSSIP_PORT)) result.add(getConfig(GOSSIP_PORT))
        if (getConfig(SSL_GOSSIP_PORT)) result.add(getConfig(SSL_GOSSIP_PORT))
        if (getConfig(THRIFT_PORT)) result.add(getConfig(THRIFT_PORT))
        return result
    }

    @Override
    public Class getDriverInterface() {
        return CassandraDriver.class;
    }

    transient JmxSensorAdapter jmxAdapter;

    @Override
    protected void connectSensors() {
        jmxAdapter = sensorRegistry.register(new JmxSensorAdapter())
        jmxAdapter.objectName("org.apache.cassandra.db:type=StorageService")
            .attribute("Initialized")
            .subscribe(SERVICE_UP) { it as Boolean }
        jmxAdapter.objectName("org.apache.cassandra.db:type=StorageService")
            .attribute("Token")
            .subscribe(TOKEN) { it as String }
        jmxAdapter.activateAdapter()
    }

    public void waitForServiceUp() {
        super.waitForServiceUp();

        ObjectName storageService = new ObjectName("org.apache.cassandra.db:type=StorageService")
        JmxHelper helper = new JmxHelper(this)
        helper.connect();
        try {
            helper.assertMBeanExistsEventually(storageService, 60*1000);
            log.info("Connected to StorageService version " + helper.getAttribute(storageService, "ReleaseVersion"));
        } finally {
            helper.disconnect();
        }
    }

    public void setToken(String token) {
        ObjectName storageService = new ObjectName("org.apache.cassandra.db:type=StorageService")
        JmxHelper helper = new JmxHelper(this)
        helper.connect();
        try {
            helper.operation(storageService, "move", token)
            log.info("Moved server ${this} to token ${token}");
        } finally {
            helper.disconnect();
        }
    }
}
