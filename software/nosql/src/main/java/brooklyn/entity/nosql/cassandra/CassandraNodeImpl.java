/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.RollingTimeWindowMeanEnricher;
import brooklyn.enricher.TimeWeightedDeltaEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.java.JavaAppUtils;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.event.feed.jmx.JmxOperationPollConfig;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.Machines;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

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

    private final AtomicReference<Boolean> requiresAlwaysPublicIp = new AtomicReference<Boolean>();
    
    public CassandraNodeImpl() {
    }
    
    /**
     * Some clouds (e.g. Rackspace) give us VMs that have two nics: one for private and one for public.
     * If the private IP is used then it doesn't work, even for a cluster purely internal to Rackspace!
     * 
     * TODO Need to investigate that further, e.g.:
     *  - is `openIptables` opening it up for both interfaces?
     *  - for aws->rackspace comms between nodes (thus using the public IP), will it be listening on an accessible port?
     *  
     * FIXME Really ugly code; surely can do better?!
     * 
     * @return
     */
    protected boolean requiresAlwaysPublicIp() {
        if (requiresAlwaysPublicIp.get() != null) {
            return requiresAlwaysPublicIp.get();
        }
        MachineProvisioningLocation<?> loc = getProvisioningLocation();
        if (loc != null) {
            try {
                Method method = loc.getClass().getMethod("getProvider");
                method.setAccessible(true);
                String provider = (String) method.invoke(loc);
                boolean result = (provider != null) && (provider.contains("rackspace") || provider.contains("cloudservers"));
                log.info("Inferred requiresAlwaysPublicIp={} for {}; using location {}, based on provider {}", new Object[] {result, this, loc, provider});
                requiresAlwaysPublicIp.set(result);
            } catch (Exception e) {
                log.info("Inferred requiresAlwaysPublicIp={} for {}; using location {}, based on: {}", new Object[] {false, this, loc, e});
                requiresAlwaysPublicIp.set(false);
            }
            return requiresAlwaysPublicIp.get();
        }
        return false;
    }
    
    @Override public Integer getGossipPort() { return getAttribute(CassandraNode.GOSSIP_PORT); }
    @Override public Integer getSslGossipPort() { return getAttribute(CassandraNode.SSL_GOSSIP_PORT); }
    @Override public Integer getThriftPort() { return getAttribute(CassandraNode.THRIFT_PORT); }
    @Override public String getClusterName() { return getAttribute(CassandraNode.CLUSTER_NAME); }
    @Override public Long getToken() { return getAttribute(CassandraNode.TOKEN); }
    @Override public String getListenAddress() {
        if (requiresAlwaysPublicIp()) {
            return getAttribute(CassandraNode.ADDRESS);
        } else {
            String subnetAddress = getAttribute(CassandraNode.SUBNET_ADDRESS);
            return Strings.isNonBlank(subnetAddress) ? subnetAddress : getAttribute(CassandraNode.ADDRESS);
        }
    }
    @Override public String getBroadcastAddress() {
        String snitchName = getConfig(CassandraNode.ENDPOINT_SNITCH_NAME);
        if (snitchName.equals("Ec2MultiRegionSnitch") || snitchName.contains("MultiCloudSnitch")) {
            // http://www.datastax.com/documentation/cassandra/2.0/mobile/cassandra/architecture/architectureSnitchEC2MultiRegion_c.html
            // describes that the listen_address is set to the private IP, and the broadcast_address is set to the public IP.
            return getPublicIp();
        } else if (!getDriver().isClustered()) {
            return getListenAddress();
        } else {
            // In other situations, prefer the hostname so other regions can see it
            return getAttribute(CassandraNode.HOSTNAME);
        }
    }
    
    public String getPrivateIp() {
        if (requiresAlwaysPublicIp()) {
            return getAttribute(CassandraNode.ADDRESS);
        } else {
            String subnetAddress = getAttribute(CassandraNode.SUBNET_ADDRESS);
            return Strings.isNonBlank(subnetAddress) ? subnetAddress : getAttribute(CassandraNode.ADDRESS);
        }
    }
    public String getPublicIp() {
        return getAttribute(CassandraNode.ADDRESS);
    }

    @Override public String getSeeds() { 
        Set<Entity> seeds = getConfig(CassandraNode.INITIAL_SEEDS);
        if (seeds==null) {
            log.warn("No seeds available when requested for "+this, new Throwable("source of no Cassandra seeds when requested"));
            return null;
        }
        String snitchName = getConfig(CassandraNode.ENDPOINT_SNITCH_NAME);
        MutableSet<String> seedsHostnames = MutableSet.of();
        for (Entity entity : seeds) {
            // tried removing ourselves if there are other nodes, but that is a BAD idea!
            // blows up with a "java.lang.RuntimeException: No other nodes seen!"
            
            if (snitchName.equals("Ec2MultiRegionSnitch") || snitchName.contains("MultiCloudSnitch")) {
                // http://www.datastax.com/documentation/cassandra/2.0/mobile/cassandra/architecture/architectureSnitchEC2MultiRegion_c.html
                // says the seeds should be public IPs.
                seedsHostnames.add(entity.getAttribute(CassandraNode.ADDRESS));
            } else if (requiresAlwaysPublicIp()) {
                seedsHostnames.add(entity.getAttribute(CassandraNode.HOSTNAME));
            } else {
                String seedHostname = Machines.findSubnetOrPublicHostname(entity).get();
                seedsHostnames.add(seedHostname);
            }
        }
        
        String result = Strings.join(seedsHostnames, ",");
        log.info("Seeds for {}: {}", this, result);
        return result;
    }

    public String getDatacenterName() {
        String name = getAttribute(CassandraNode.DATACENTER_NAME);
        if (name == null) {
            MachineLocation machine = getMachineOrNull();
            MachineProvisioningLocation<?> provisioningLocation = getProvisioningLocation();
            if (machine != null) {
                name = machine.getConfig(CloudLocationConfig.CLOUD_REGION_ID);
            }
            if (name == null && provisioningLocation != null) {
                name = provisioningLocation.getConfig(CloudLocationConfig.CLOUD_REGION_ID);
            }
            if (name == null) {
                name = "UNKNOWN_DATACENTER";
            }
            setAttribute((AttributeSensor<String>)DATACENTER_NAME, name);
        }
        return name;
    }

    public String getRackName() {
        String name = getAttribute(CassandraNode.RACK_NAME);
        if (name == null) {
            MachineLocation machine = getMachineOrNull();
            MachineProvisioningLocation<?> provisioningLocation = getProvisioningLocation();
            if (machine != null) {
                name = machine.getConfig(CloudLocationConfig.CLOUD_AVAILABILITY_ZONE_ID);
            }
            if (name == null && provisioningLocation != null) {
                name = provisioningLocation.getConfig(CloudLocationConfig.CLOUD_AVAILABILITY_ZONE_ID);
            }
            if (name == null) {
                name = "UNKNOWN_RACK";
            }
            setAttribute((AttributeSensor<String>)RACK_NAME, name);
        }
        return name;
    }

    @Override
    public Class<CassandraNodeDriver> getDriverInterface() {
        return CassandraNodeDriver.class;
    }
    
    public CassandraNodeDriver getDriver() {
        return (CassandraNodeDriver) super.getDriver();
    }

    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(EXECUTE_SCRIPT, new EffectorBody<String>() {
            @Override
            public String call(ConfigBag parameters) {
                return executeScript((String)parameters.getStringKey("commands"));
            }
        });
    }
    
    private volatile JmxFeed jmxFeed;
    private volatile FunctionFeed functionFeed;
    private JmxHelper jmxHelper;
    private ObjectName storageServiceMBean = JmxHelper.createObjectName("org.apache.cassandra.db:type=StorageService");
    private ObjectName readStageMBean = JmxHelper.createObjectName("org.apache.cassandra.request:type=ReadStage");
    private ObjectName mutationStageMBean = JmxHelper.createObjectName("org.apache.cassandra.request:type=MutationStage");
    private ObjectName snitchMBean = JmxHelper.createObjectName("org.apache.cassandra.db:type=EndpointSnitchInfo");
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void connectSensors() {
        // "cassandra" isn't really a protocol, but okay for now
        setAttribute(DATASTORE_URL, "cassandra://"+getAttribute(HOSTNAME)+":"+getAttribute(THRIFT_PORT));
        
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
                .pollOperation(new JmxOperationPollConfig<String>(DATACENTER_NAME)
                        .period(60, TimeUnit.SECONDS)
                        .objectName(snitchMBean)
                        .operationName("getDatacenter")
                        .operationParams(ImmutableList.of(getBroadcastAddress()))
                        .onException(Functions.<String>constant(null)))
                .pollOperation(new JmxOperationPollConfig<String>(RACK_NAME)
                        .period(60, TimeUnit.SECONDS)
                        .objectName(snitchMBean)
                        .operationName("getRack")
                        .operationParams(ImmutableList.of(getBroadcastAddress()))
                        .onException(Functions.<String>constant(null)))
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
                .pollAttribute(new JmxAttributePollConfig<Integer>(LIVE_NODE_COUNT)
                        .objectName(storageServiceMBean)
                        .attributeName("LiveNodes")
                        .onSuccess((Function) new Function<List, Integer>() {
                            @Override
                            public Integer apply(@Nullable List input) {
                                if (input == null || input.isEmpty()) return 0;
                                return input.size();
                            }
                        })
                        .onException(Functions.constant(-1)))
                .pollAttribute(new JmxAttributePollConfig<Integer>(READ_ACTIVE)
                        .objectName(readStageMBean)
                        .attributeName("ActiveCount")
                        .onException(Functions.constant((Integer)null)))
                .pollAttribute(new JmxAttributePollConfig<Long>(READ_PENDING)
                        .objectName(readStageMBean)
                        .attributeName("PendingTasks")
                        .onException(Functions.constant((Long)null)))
                .pollAttribute(new JmxAttributePollConfig<Long>(READ_COMPLETED)
                        .objectName(readStageMBean)
                        .attributeName("CompletedTasks")
                        .onException(Functions.constant((Long)null)))
                .pollAttribute(new JmxAttributePollConfig<Integer>(WRITE_ACTIVE)
                        .objectName(mutationStageMBean)
                        .attributeName("ActiveCount")
                        .onException(Functions.constant((Integer)null)))
                .pollAttribute(new JmxAttributePollConfig<Long>(WRITE_PENDING)
                        .objectName(mutationStageMBean)
                        .attributeName("PendingTasks")
                        .onException(Functions.constant((Long)null)))
                .pollAttribute(new JmxAttributePollConfig<Long>(WRITE_COMPLETED)
                        .objectName(mutationStageMBean)
                        .attributeName("CompletedTasks")
                        .onException(Functions.constant((Long)null)))
                .build();
        
        functionFeed = FunctionFeed.builder()
                .entity(this)
                .period(3000, TimeUnit.MILLISECONDS)
                .poll(new FunctionPollConfig<Long, Long>(THRIFT_PORT_LATENCY)
                        .onException(Functions.constant((Long)null))
                        .callable(new Callable<Long>() {
                            public Long call() {
                                try {
                                    long start = System.currentTimeMillis();
                                    Socket s = new Socket(getAttribute(Attributes.HOSTNAME), getThriftPort());
                                    s.close();
                                    long latency = System.currentTimeMillis() - start;
                                    computeServiceUp();
                                    return latency;
                                } catch (Exception e) {
                                    if (log.isDebugEnabled())
                                        log.debug("Cassandra thrift port poll failure: "+e);
                                    setAttribute(SERVICE_UP, false);
                                    return null;
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
        
        connectEnrichers();
    }

    protected void connectEnrichers() {
        connectEnrichers(Duration.TEN_SECONDS);
    }
    
    protected void connectEnrichers(Duration windowPeriod) {
        JavaAppUtils.connectMXBeanSensors(this);
        JavaAppUtils.connectJavaAppServerPolicies(this);

        addEnricher(TimeWeightedDeltaEnricher.<Long>getPerSecondDeltaEnricher(this, READ_COMPLETED, READS_PER_SECOND_LAST));
        addEnricher(TimeWeightedDeltaEnricher.<Long>getPerSecondDeltaEnricher(this, WRITE_COMPLETED, WRITES_PER_SECOND_LAST));
        
        if (windowPeriod!=null) {
            addEnricher(new RollingTimeWindowMeanEnricher<Long>(this, THRIFT_PORT_LATENCY, 
                    THRIFT_PORT_LATENCY_IN_WINDOW, windowPeriod));
            addEnricher(new RollingTimeWindowMeanEnricher<Double>(this, READS_PER_SECOND_LAST, 
                    READS_PER_SECOND_IN_WINDOW, windowPeriod));
            addEnricher(new RollingTimeWindowMeanEnricher<Double>(this, WRITES_PER_SECOND_LAST, 
                    WRITES_PER_SECOND_IN_WINDOW, windowPeriod));
        }
    }
    
    @Override
    public void disconnectSensors() {
        super.disconnectSensors();

        if (jmxFeed != null) jmxFeed.stop();
        if (jmxHelper.isConnected()) jmxHelper.disconnect();
        if (functionFeed != null) functionFeed.stop();
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
    
    @Override
    public String executeScript(String commands) {
        return ((CassandraNodeSshDriver)getDriver()).executeScriptHere(commands);
    }
    
}
