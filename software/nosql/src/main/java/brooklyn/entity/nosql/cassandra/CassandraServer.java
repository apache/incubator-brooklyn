/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.java.UsesJmx;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * An {@link brooklyn.entity.Entity} that represents an Cassandra service
 */
public class CassandraServer extends SoftwareProcessEntity implements UsesJmx {
    /** serialVersionUID */
    private static final long serialVersionUID = -5430475649331861964L;

    private static final Logger log = LoggerFactory.getLogger(CassandraServer.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "1.2.0");
    
    @SetFromFlag("clusterName")
    public static final BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "cassandra.cluster.name", "Name of the Cassandra cluster", "Brooklyn Cassandra Cluster");
    
    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    public static final ConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "cassandra.install.mirror.url", "URL of mirror", "http://www.mirrorservice.org/sites/ftp.apache.org/cassandra");

    @SetFromFlag("tgzUrl")
    public static final ConfigKey<String> TGZ_URL = new BasicConfigKey<String>(String.class, "cassandra.install.tgzUrl", "URL of TGZ download file");
    
    @SetFromFlag("gossipPort")
    public static final PortAttributeSensorAndConfigKey GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.gossip.port", "Cassandra Gossip communications port", PortRanges.fromString("7000+"));
    
    @SetFromFlag("sslGgossipPort")
    public static final PortAttributeSensorAndConfigKey SSL_GOSSIP_PORT = new PortAttributeSensorAndConfigKey("cassandra.ssl-gossip.port", "Cassandra Gossip SSL communications port", PortRanges.fromString("7001+"));

    @SetFromFlag("thriftPort")
    public static final PortAttributeSensorAndConfigKey THRIFT_PORT = new PortAttributeSensorAndConfigKey("cassandra.thrift.port", "Cassandra Thrift RPC port", PortRanges.fromString("9160+"));

    public static final BasicAttributeSensor<Long> TOKEN = new BasicAttributeSensor<Long>(Long.class, "cassandra.token", "Cassandra Token");

    public CassandraServer(Map<?, ?> flags){
        this(flags, null);
    }

    public CassandraServer(Entity owner){
        this(Maps.newHashMap(), owner);
    }

    public CassandraServer(Map<?, ?> flags, Entity owner) {
        super(flags, owner);
    }
    
    public Integer getGossipPort() { return getAttribute(GOSSIP_PORT); }
    public Integer getSslGossipPort() { return getAttribute(SSL_GOSSIP_PORT); }
    public Integer getThriftPort() { return getAttribute(THRIFT_PORT); }
    public String getClusterName() { return getAttribute(CLUSTER_NAME); }

    @Override
    public Class getDriverInterface() {
        return CassandraDriver.class;
    }

    private JmxFeed jmxFeed;
    private JmxHelper jmxHelper;
    private ObjectName storageServiceMBean = JmxHelper.createObjectName("org.apache.cassandra.db:type=StorageService");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void connectSensors() {
        super.connectSensors();

        jmxHelper = new JmxHelper(this);
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .helper(jmxHelper)
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                        .objectName(storageServiceMBean)
                        .attributeName("Initialized")
                        .onSuccess(Functions.forPredicate(Predicates.notNull()))
                        .onError(Functions.constant(false)))
                .pollAttribute(new JmxAttributePollConfig<Long>(TOKEN)
                        .objectName(storageServiceMBean)
                        .attributeName("TokenToEndpointMap")
                        .onSuccess((Function) new Function<Map, Long>() {
                            @Override
                            public Long apply(@Nullable Map input) { // FIXME
                                if (input == null || input.isEmpty()) return 0L;
                                Set tokens = Maps.filterValues(input, Predicates.equalTo(getLocalHostname())).keySet();
                                return Iterables.getFirst(tokens, 0L);
                            }
                        })
                        .onError(Functions.constant(0L)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();

        if (jmxFeed != null) jmxFeed.stop();
        if (jmxHelper.isConnected()) jmxHelper.disconnect();
    }

    @Override
    public void waitForServiceUp() {
        super.waitForServiceUp();

        try {
            if (!jmxHelper.isConnected()) jmxHelper.connect();
            jmxHelper.assertMBeanExistsEventually(storageServiceMBean, 60*1000);
            log.info("Connected to StorageService version {}", jmxHelper.getAttribute(storageServiceMBean, "ReleaseVersion"));
        } catch (IOException ioe) {
            Throwables.propagate(ioe);
        }
    }

    public void setToken(String token) {
        try {
            if (!jmxHelper.isConnected()) jmxHelper.connect();;
            jmxHelper.operation(storageServiceMBean, "move", token);
            log.info("Moved server {} to token {}", getId(), token);
        } catch (IOException ioe) {
            Throwables.propagate(ioe);
        }
    }
}
