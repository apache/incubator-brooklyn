/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.location.basic.Machines;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * Implementation of {@link CassandraNode}.
 */
public class CassandraNodeImpl extends SoftwareProcessImpl implements CassandraNode {

    private static final Logger log = LoggerFactory.getLogger(CassandraNodeImpl.class);

    public CassandraNodeImpl() {
    }

    @Override public Integer getGossipPort() { return getAttribute(CassandraNode.GOSSIP_PORT); }
    @Override public Integer getSslGossipPort() { return getAttribute(CassandraNode.SSL_GOSSIP_PORT); }
    @Override public Integer getThriftPort() { return getAttribute(CassandraNode.THRIFT_PORT); }
    @Override public String getClusterName() { return getAttribute(CassandraNode.CLUSTER_NAME); }
    @Override public String getSubnetAddress() { return Machines.findSubnetOrPublicHostname(this).get(); }
    @Override public Long getToken() { return getAttribute(CassandraNode.TOKEN); }
    
    @Override public String getSeeds() { 
        Set<Entity> seeds = getConfig(CassandraNode.INITIAL_SEEDS);
        if (seeds==null) {
            log.warn("No seeds available when requested for "+this, new Throwable("source of no Cassandra seeds when requested"));
            return null;
        }
        MutableSet<String> seedsHostnames = MutableSet.of();
        for (Entity e: seeds) {
            // tried removing ourselves if there are other nodes, but that is a BAD idea!
            // blows up with a "java.lang.RuntimeException: No other nodes seen!"
            seedsHostnames.add(Machines.findSubnetOrPublicHostname(e).get());
        }
        return Strings.join(seedsHostnames, ",");
    }

    @Override
    public Class<CassandraNodeDriver> getDriverInterface() {
        return CassandraNodeDriver.class;
    }

    @Override
    public void init() {
        super.init();
    }
    
    private volatile JmxFeed jmxFeed;
    private volatile FunctionFeed functionFeed;
    private JmxHelper jmxHelper;
    private ObjectName storageServiceMBean = JmxHelper.createObjectName("org.apache.cassandra.db:type=StorageService");
    private ObjectName readStageMBean = JmxHelper.createObjectName("org.apache.cassandra.request:type=ReadStage");
    private ObjectName mutationStageMBean = JmxHelper.createObjectName("org.apache.cassandra.request:type=MutationStage");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void connectSensors() {
        super.connectSensors();

        jmxHelper = new JmxHelper(this);
        jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(3000, TimeUnit.MILLISECONDS)
                .helper(jmxHelper)
                .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP_JMX)
                        .objectName(storageServiceMBean)
                        .attributeName("Initialized")
                        .onSuccess(Functions.forPredicate(Predicates.notNull()))
                        .onException(Functions.constant(false)))
                .pollAttribute(new JmxAttributePollConfig<Long>(TOKEN)
                        .objectName(storageServiceMBean)
                        .attributeName("TokenToEndpointMap")
                        .onSuccess((Function) new Function<Map, Long>() {
                            @Override
                            public Long apply(@Nullable Map input) {
                                if (input == null || input.isEmpty()) return 0L;
                                // FIXME does not work on aws-ec2, uses RFC1918 address
                                Predicate<String> self = Predicates.in(ImmutableList.of(getAttribute(HOSTNAME), getAttribute(ADDRESS)));
                                Set tokens = Maps.filterValues(input, self).keySet();
                                return Long.parseLong(Iterables.getFirst(tokens, "-1"));
                            }
                        })
                        .onException(Functions.constant(-1L)))
                .pollAttribute(new JmxAttributePollConfig<Integer>(PEERS)
                        .objectName(storageServiceMBean)
                        .attributeName("TokenToEndpointMap")
                        .onSuccess((Function) new Function<Map, Integer>() {
                            @Override
                            public Integer apply(@Nullable Map input) {
                                if (input == null || input.isEmpty()) return 0;
                                return input.size();
                            }
                        })
                        .onException(Functions.constant(-1)))
                .pollAttribute(new JmxAttributePollConfig<Integer>(READ_ACTIVE)
                        .objectName(readStageMBean)
                        .attributeName("ActiveCount")
                        .onException(Functions.constant(-1)))
                .pollAttribute(new JmxAttributePollConfig<Long>(READ_PENDING)
                        .objectName(readStageMBean)
                        .attributeName("PendingTasks")
                        .onException(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(READ_COMPLETED)
                        .objectName(readStageMBean)
                        .attributeName("CompletedTasks")
                        .onException(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Integer>(WRITE_ACTIVE)
                        .objectName(mutationStageMBean)
                        .attributeName("ActiveCount")
                        .onException(Functions.constant(-1)))
                .pollAttribute(new JmxAttributePollConfig<Long>(WRITE_PENDING)
                        .objectName(mutationStageMBean)
                        .attributeName("PendingTasks")
                        .onException(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(WRITE_COMPLETED)
                        .objectName(mutationStageMBean)
                        .attributeName("CompletedTasks")
                        .onException(Functions.constant(-1l)))
                .build();
        functionFeed = FunctionFeed.builder()
                .entity(this)
                .period(3000, TimeUnit.MILLISECONDS)
                .poll(new FunctionPollConfig<Long, Long>(THRIFT_PORT_LATENCY)
                        .onException(Functions.constant(-1L))
                        .callable(new Callable<Long>() {
                            public Long call() {
                                try {
                                    long start = System.currentTimeMillis();
                                    Socket s = new Socket(getAttribute(Attributes.HOSTNAME), getThriftPort());
                                    s.close();
                                    computeServiceUp();
                                    return System.currentTimeMillis() - start;
                                } catch (Exception e) {
                                    if (log.isDebugEnabled())
                                        log.debug("Cassandra thrift port poll failure: "+e);
                                    setAttribute(SERVICE_UP, false);
                                    return -1L;
                                }
                            }
                            public void computeServiceUp() {
                                // this will wait an additional poll period after thrift port is up,
                                // as the caller will not have set yet, but that will help ensure it is really healthy!
                                setAttribute(SERVICE_UP,
                                        getAttribute(THRIFT_PORT_LATENCY)!=null && getAttribute(THRIFT_PORT_LATENCY)>=0 && 
                                        getAttribute(SERVICE_UP_JMX)==Boolean.TRUE);
                            }
                        }))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();

        if (jmxFeed != null && jmxFeed.isActivated()) jmxFeed.stop();
        if (jmxHelper.isConnected()) jmxHelper.disconnect();
        if (functionFeed != null && functionFeed.isActivated()) functionFeed.stop();
    }

    @Override
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
