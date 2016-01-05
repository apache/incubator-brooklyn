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
package org.apache.brooklyn.entity.nosql.cassandra;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.management.ObjectName;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.java.JavaAppUtils;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.feed.jmx.JmxAttributePollConfig;
import org.apache.brooklyn.feed.jmx.JmxFeed;
import org.apache.brooklyn.feed.jmx.JmxHelper;
import org.apache.brooklyn.feed.jmx.JmxOperationPollConfig;
import org.apache.brooklyn.policy.enricher.RollingTimeWindowMeanEnricher;
import org.apache.brooklyn.policy.enricher.TimeWeightedDeltaEnricher;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;

/**
 * Implementation of {@link CassandraNode}.
 */
public class CassandraNodeImpl extends SoftwareProcessImpl implements CassandraNode {

    private static final Logger log = LoggerFactory.getLogger(CassandraNodeImpl.class);

    private final AtomicReference<Boolean> detectedCloudSensors = new AtomicReference<Boolean>(false);
    
    public CassandraNodeImpl() {
    }
    
    @Override
    public void init() {
        super.init();
        
        // TODO PERSISTENCE WORKAROUND kept anonymous class in case referenced in persisted state
        new EffectorBody<String>() {
            @Override
            public String call(ConfigBag parameters) {
                return executeScript((String)parameters.getStringKey("commands"));
            }
        };
        
        getMutableEntityType().addEffector(EXECUTE_SCRIPT, new ExecuteScriptEffectorBody(this));
        
        Entities.checkRequiredUrl(this, getCassandraConfigTemplateUrl());
        Entities.getRequiredUrlConfig(this, CASSANDRA_RACKDC_CONFIG_TEMPLATE_URL);
        
        connectEnrichers();
    }
    
    private static class ExecuteScriptEffectorBody extends EffectorBody<String> {
        private final CassandraNode entity;
        
        public ExecuteScriptEffectorBody(CassandraNode entity) {
            this.entity = entity;
        }
        @Override
        public String call(ConfigBag parameters) {
            return entity.executeScript((String)parameters.getStringKey("commands"));
        }
    }
    
    /**
     * Some clouds (e.g. Rackspace) give us VMs that have two nics: one for private and one for public.
     * If the private IP is used then it doesn't work, even for a cluster purely internal to Rackspace!
     * 
     * TODO Ugly. Need to understand more and find a better fix. Perhaps in Cassandra itself if necessary.
     * Also need to investigate further:
     *  - does it still fail if BroadcastAddress is set to private IP?
     *  - is `openIptables` opening it up for both interfaces?
     *  - for aws->rackspace comms between nodes (thus using the public IP), will it be listening on an accessible port?
     *  - ideally do a check, open a server on one port on the machine, see if it is contactable on the public address;
     *    and set that as a flag on the cloud
     */
    protected void setCloudPreferredSensorNames() {
        if (detectedCloudSensors.get()) return;
        synchronized (detectedCloudSensors) {
            if (detectedCloudSensors.get()) return;

            MachineProvisioningLocation<?> loc = getProvisioningLocation();
            if (loc != null) {
                try {
                    Method method = loc.getClass().getMethod("getProvider");
                    method.setAccessible(true);
                    String provider = (String) method.invoke(loc);
                    String result = "(nothing special)";
                    if (provider!=null) {
                        if (provider.contains("rackspace") || provider.contains("cloudservers") || provider.contains("softlayer")) {
                            /* These clouds have 2 NICs and it has to be consistent, so use public IP here to allow external access;
                             * (TODO internal access could be configured to improve performance / lower cost, 
                             * if we know all nodes are visible to each other) */
                            if (getConfig(LISTEN_ADDRESS_SENSOR)==null)
                                config().set(LISTEN_ADDRESS_SENSOR, CassandraNode.ADDRESS.getName());
                            if (getConfig(BROADCAST_ADDRESS_SENSOR)==null)
                                config().set(BROADCAST_ADDRESS_SENSOR, CassandraNode.ADDRESS.getName());
                            result = "public IP for both listen and broadcast";
                        } else if (provider.contains("google-compute")) {
                            /* Google nodes cannot reach themselves/each-other on the public IP,
                             * and there is no hostname, so use private IP here */
                            if (getConfig(LISTEN_ADDRESS_SENSOR)==null)
                                config().set(LISTEN_ADDRESS_SENSOR, CassandraNode.SUBNET_HOSTNAME.getName());
                            if (getConfig(BROADCAST_ADDRESS_SENSOR)==null)
                                config().set(BROADCAST_ADDRESS_SENSOR, CassandraNode.SUBNET_HOSTNAME.getName());
                            result = "private IP for both listen and broadcast";
                        }
                    }
                    log.debug("Cassandra NICs inferred {} for {}; using location {}, based on provider {}", new Object[] {result, this, loc, provider});
                } catch (Exception e) {
                    log.debug("Cassandra NICs auto-detection failed for {} in location {}: {}", new Object[] {this, loc, e});
                }
            }
            detectedCloudSensors.set(true);
        }
    }
    
    @Override
    protected void preStart() {
        super.preStart();
        setCloudPreferredSensorNames();
    }
    
    // Used for freemarker
    public String getMajorMinorVersion() {
        String version = getConfig(CassandraNode.SUGGESTED_VERSION);
        if (Strings.isBlank(version)) return "";
        List<String> versionParts = ImmutableList.copyOf(Splitter.on(".").split(version));
        return versionParts.get(0) + (versionParts.size() > 1 ? "."+versionParts.get(1) : "");
    }
    
    public String getCassandraConfigTemplateUrl() {
        String templatedUrl = getConfig(CassandraNode.CASSANDRA_CONFIG_TEMPLATE_URL);
        return TemplateProcessor.processTemplateContents(templatedUrl, this, ImmutableMap.<String, Object>of());
    }

    @Override public Integer getGossipPort() { return getAttribute(CassandraNode.GOSSIP_PORT); }
    @Override public Integer getSslGossipPort() { return getAttribute(CassandraNode.SSL_GOSSIP_PORT); }
    @Override public Integer getThriftPort() { return getAttribute(CassandraNode.THRIFT_PORT); }
    @Override public Integer getNativeTransportPort() { return getAttribute(CassandraNode.NATIVE_TRANSPORT_PORT); }
    @Override public String getClusterName() { return getAttribute(CassandraNode.CLUSTER_NAME); }
    
    @Override public int getNumTokensPerNode() {
        return getConfig(CassandraNode.NUM_TOKENS_PER_NODE);
    }

    @Override public Set<BigInteger> getTokens() {
        // Prefer an already-set attribute over the config.
        // Prefer TOKENS over TOKEN.
        Set<BigInteger> tokens = getAttribute(CassandraNode.TOKENS);
        if (tokens == null) {
            tokens = getConfig(CassandraNode.TOKENS);
        }
        return tokens;
    }
    
    @Override public String getTokensAsString() {
        // TODO check what is required when replacing failed node.
        // with vnodes in Cassandra 2.x, don't bother supplying token
        Set<BigInteger> tokens = getTokens();
        if (tokens == null) return "";
        return Joiner.on(",").join(tokens);
    }
    
    @Override public String getListenAddress() {
        String sensorName = getConfig(LISTEN_ADDRESS_SENSOR);
        if (Strings.isNonBlank(sensorName))
            return Entities.submit(this, DependentConfiguration.attributeWhenReady(this, Sensors.newStringSensor(sensorName))).getUnchecked();
        
        String subnetAddress = getAttribute(CassandraNode.SUBNET_ADDRESS);
        return Strings.isNonBlank(subnetAddress) ? subnetAddress : getAttribute(CassandraNode.ADDRESS);
    }
    
    @Override public String getBroadcastAddress() {
        String sensorName = getConfig(BROADCAST_ADDRESS_SENSOR);
        if (Strings.isNonBlank(sensorName))
            return Entities.submit(this, DependentConfiguration.attributeWhenReady(this, Sensors.newStringSensor(sensorName))).getUnchecked();
        
        String snitchName = getConfig(CassandraNode.ENDPOINT_SNITCH_NAME);
        if (snitchName.equals("Ec2MultiRegionSnitch") || snitchName.contains("MultiCloudSnitch")) {
            // http://www.datastax.com/documentation/cassandra/2.0/mobile/cassandra/architecture/architectureSnitchEC2MultiRegion_c.html
            // describes that the listen_address is set to the private IP, and the broadcast_address is set to the public IP.
            return getAttribute(CassandraNode.ADDRESS);
        } else if (!getDriver().isClustered()) {
            return getListenAddress();
        } else {
            // In other situations, prefer the hostname, so other regions can see it
            // *Unless* hostname resolves at the target to a local-only interface which is different to ADDRESS
            // (workaround for issue deploying to localhost)
            String hostname = getAttribute(CassandraNode.HOSTNAME);
            try {
                String resolvedAddress = getDriver().getResolvedAddress(hostname);
                if (resolvedAddress==null) {
                    log.debug("Cassandra using broadcast address "+getListenAddress()+" for "+this+" because hostname "+hostname+" could not be resolved at remote machine");
                    return getListenAddress();
                }
                if (resolvedAddress.equals("127.0.0.1")) {
                    log.debug("Cassandra using broadcast address "+getListenAddress()+" for "+this+" because hostname "+hostname+" resolves to 127.0.0.1");
                    return getListenAddress();                    
                }
                return hostname;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                log.warn("Error resolving hostname "+hostname+" for "+this+": "+e, e);
                return hostname;
            }
        }
    }
    /** not always the private IP, if public IP has been insisted on for broadcast, e.g. setting up a rack topology */
    // have not confirmed this does the right thing in all clouds ... only used for rack topology however
    public String getPrivateIp() {
        String sensorName = getConfig(BROADCAST_ADDRESS_SENSOR);
        if (Strings.isNonBlank(sensorName)) {
            return getAttribute(Sensors.newStringSensor(sensorName));
        } else {
            String subnetAddress = getAttribute(CassandraNode.SUBNET_ADDRESS);
            return Strings.isNonBlank(subnetAddress) ? subnetAddress : getAttribute(CassandraNode.ADDRESS);
        }
    }
    public String getPublicIp() {
        // may need to be something else in google
        return getAttribute(CassandraNode.ADDRESS);
    }

    @Override public String getRpcAddress() {
        String sensorName = getConfig(RPC_ADDRESS_SENSOR);
        if (Strings.isNonBlank(sensorName))
            return Entities.submit(this, DependentConfiguration.attributeWhenReady(this, Sensors.newStringSensor(sensorName))).getUnchecked();
        return "0.0.0.0";
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
            } else {
                String sensorName = getConfig(BROADCAST_ADDRESS_SENSOR);
                if (Strings.isNonBlank(sensorName)) {
                    seedsHostnames.add(entity.getAttribute(Sensors.newStringSensor(sensorName)));
                } else {
                    Maybe<String> optionalSeedHostname = Machines.findSubnetOrPublicHostname(entity);
                    if (optionalSeedHostname.isPresent()) {
                        String seedHostname = optionalSeedHostname.get();
                        seedsHostnames.add(seedHostname);
                    } else {
                        log.warn("In node {}, seed hostname missing for {}; not including in seeds list", this, entity);
                    }
                }
            }
        }
        
        String result = Strings.join(seedsHostnames, ",");
        log.info("Seeds for {}: {}", this, result);
        return result;
    }

    // referenced by cassandra-rackdc.properties, read by some of the cassandra snitches
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
            sensors().set((AttributeSensor<String>)DATACENTER_NAME, name);
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
            sensors().set((AttributeSensor<String>)RACK_NAME, name);
        }
        return name;
    }

    @Override
    public Class<? extends CassandraNodeDriver> getDriverInterface() {
        return CassandraNodeDriver.class;
    }
    
    @Override
    public CassandraNodeDriver getDriver() {
        return (CassandraNodeDriver) super.getDriver();
    }

    private volatile JmxFeed jmxFeed;
    private volatile FunctionFeed functionFeed;
    private JmxFeed jmxMxBeanFeed;
    private JmxHelper jmxHelper;
    private ObjectName storageServiceMBean = JmxHelper.createObjectName("org.apache.cassandra.db:type=StorageService");
    private ObjectName readStageMBean = JmxHelper.createObjectName("org.apache.cassandra.request:type=ReadStage");
    private ObjectName mutationStageMBean = JmxHelper.createObjectName("org.apache.cassandra.request:type=MutationStage");
    private ObjectName snitchMBean = JmxHelper.createObjectName("org.apache.cassandra.db:type=EndpointSnitchInfo");

    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void connectSensors() {
        // "cassandra" isn't really a protocol, but okay for now
        sensors().set(DATASTORE_URL, "cassandra://"+getAttribute(HOSTNAME)+":"+getAttribute(THRIFT_PORT));
        
        super.connectSensors();

        jmxHelper = new JmxHelper(this);
        boolean retrieveUsageMetrics = getConfig(RETRIEVE_USAGE_METRICS);
        
        if (getDriver().isJmxEnabled()) {
            jmxFeed = JmxFeed.builder()
                    .entity(this)
                    .period(3000, TimeUnit.MILLISECONDS)
                    .helper(jmxHelper)
                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP_JMX)
                            .objectName(storageServiceMBean)
                            .attributeName("Initialized")
                            .onSuccess(Functions.forPredicate(Predicates.notNull()))
                            .onException(Functions.constant(false))
                            .suppressDuplicates(true))
                    .pollAttribute(new JmxAttributePollConfig<Set<BigInteger>>(TOKENS)
                            .objectName(storageServiceMBean)
                            .attributeName("TokenToEndpointMap")
                            .onSuccess(new Function<Object, Set<BigInteger>>() {
                                @Override
                                public Set<BigInteger> apply(@Nullable Object arg) {
                                    Map input = (Map)arg;
                                    if (input == null || input.isEmpty()) return null;
                                    // FIXME does not work on aws-ec2, uses RFC1918 address
                                    Predicate<String> self = Predicates.in(ImmutableList.of(getAttribute(HOSTNAME), getAttribute(ADDRESS), getAttribute(SUBNET_ADDRESS), getAttribute(SUBNET_HOSTNAME)));
                                    Set<String> tokens = Maps.filterValues(input, self).keySet();
                                    Set<BigInteger> result = Sets.newLinkedHashSet();
                                    for (String token : tokens) {
                                        result.add(new BigInteger(token));
                                    }
                                    return result;
                                }})
                            .onException(Functions.<Set<BigInteger>>constant(null))
                            .suppressDuplicates(true))
                    .pollOperation(new JmxOperationPollConfig<String>(DATACENTER_NAME)
                            .period(60, TimeUnit.SECONDS)
                            .objectName(snitchMBean)
                            .operationName("getDatacenter")
                            .operationParams(ImmutableList.of(getBroadcastAddress()))
                            .onException(Functions.<String>constant(null))
                            .suppressDuplicates(true))
                    .pollOperation(new JmxOperationPollConfig<String>(RACK_NAME)
                            .period(60, TimeUnit.SECONDS)
                            .objectName(snitchMBean)
                            .operationName("getRack")
                            .operationParams(ImmutableList.of(getBroadcastAddress()))
                            .onException(Functions.<String>constant(null))
                            .suppressDuplicates(true))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(PEERS)
                            .objectName(storageServiceMBean)
                            .attributeName("TokenToEndpointMap")
                            .onSuccess(new Function<Object, Integer>() {
                                @Override
                                public Integer apply(@Nullable Object arg) {
                                    Map input = (Map)arg;
                                    if (input == null || input.isEmpty()) return 0;
                                    return input.size();
                                }
                            })
                            .onException(Functions.constant(-1)))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(LIVE_NODE_COUNT)
                            .objectName(storageServiceMBean)
                            .attributeName("LiveNodes")
                            .onSuccess(new Function<Object, Integer>() {
                                @Override
                                public Integer apply(@Nullable Object arg) {
                                    List input = (List)arg;
                                    if (input == null || input.isEmpty()) return 0;
                                    return input.size();
                                }
                            })
                            .onException(Functions.constant(-1)))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(READ_ACTIVE)
                            .objectName(readStageMBean)
                            .attributeName("ActiveCount")
                            .onException(Functions.constant((Integer)null))
                            .enabled(retrieveUsageMetrics))
                    .pollAttribute(new JmxAttributePollConfig<Long>(READ_PENDING)
                            .objectName(readStageMBean)
                            .attributeName("PendingTasks")
                            .onException(Functions.constant((Long)null))
                            .enabled(retrieveUsageMetrics))
                    .pollAttribute(new JmxAttributePollConfig<Long>(READ_COMPLETED)
                            .objectName(readStageMBean)
                            .attributeName("CompletedTasks")
                            .onException(Functions.constant((Long)null))
                            .enabled(retrieveUsageMetrics))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(WRITE_ACTIVE)
                            .objectName(mutationStageMBean)
                            .attributeName("ActiveCount")
                            .onException(Functions.constant((Integer)null))
                            .enabled(retrieveUsageMetrics))
                    .pollAttribute(new JmxAttributePollConfig<Long>(WRITE_PENDING)
                            .objectName(mutationStageMBean)
                            .attributeName("PendingTasks")
                            .onException(Functions.constant((Long)null))
                            .enabled(retrieveUsageMetrics))
                    .pollAttribute(new JmxAttributePollConfig<Long>(WRITE_COMPLETED)
                            .objectName(mutationStageMBean)
                            .attributeName("CompletedTasks")
                            .onException(Functions.constant((Long)null))
                            .enabled(retrieveUsageMetrics))
                    .build();
            
            jmxMxBeanFeed = JavaAppUtils.connectMXBeanSensors(this);
        }
        
        if (Boolean.TRUE.equals(getConfig(USE_THRIFT_MONITORING))) {
            functionFeed = FunctionFeed.builder()
                    .entity(this)
                    .period(3000, TimeUnit.MILLISECONDS)
                    .poll(new FunctionPollConfig<Long, Long>(THRIFT_PORT_LATENCY)
                            .onException(Functions.constant(-1L))
                            .callable(new ThriftLatencyChecker(CassandraNodeImpl.this))
                            .enabled(retrieveUsageMetrics))
                    .build();
        }
        
        connectServiceUpIsRunning();
    }
    
    protected void connectEnrichers() {
        connectEnrichers(Duration.TEN_SECONDS);
    }
    
    protected void connectEnrichers(Duration windowPeriod) {
        JavaAppUtils.connectJavaAppServerPolicies(this);

        enrichers().add(TimeWeightedDeltaEnricher.<Long>getPerSecondDeltaEnricher(this, READ_COMPLETED, READS_PER_SECOND_LAST));
        enrichers().add(TimeWeightedDeltaEnricher.<Long>getPerSecondDeltaEnricher(this, WRITE_COMPLETED, WRITES_PER_SECOND_LAST));
        
        if (windowPeriod!=null) {
            enrichers().add(new RollingTimeWindowMeanEnricher<Long>(this, THRIFT_PORT_LATENCY, 
                    THRIFT_PORT_LATENCY_IN_WINDOW, windowPeriod));
            enrichers().add(new RollingTimeWindowMeanEnricher<Double>(this, READS_PER_SECOND_LAST, 
                    READS_PER_SECOND_IN_WINDOW, windowPeriod));
            enrichers().add(new RollingTimeWindowMeanEnricher<Double>(this, WRITES_PER_SECOND_LAST, 
                    WRITES_PER_SECOND_IN_WINDOW, windowPeriod));
        }
        
        // service-up checks
        if (Boolean.TRUE.equals(getConfig(USE_THRIFT_MONITORING))) {
            enrichers().add(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                    .from(THRIFT_PORT_LATENCY)
                    .computing(Functionals.ifEquals(-1L).value("Thrift latency polling failed") )
                    .build());
        }
        
        if (Boolean.TRUE.equals(getConfig(UsesJmx.USE_JMX))) {
            enrichers().add(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                    .from(SERVICE_UP_JMX)
                    .computing(Functionals.ifEquals(false).value("JMX reports not up") )
                    .build());
        }
    }
    
    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
        if (jmxMxBeanFeed != null) jmxMxBeanFeed.stop();
        if (jmxHelper != null) jmxHelper.terminate();
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
        return getDriver().executeScriptAsync(commands).block().getStdout();
    }
    
    private static class ThriftLatencyChecker implements Callable<Long> {
        private final CassandraNode entity;
        
        public ThriftLatencyChecker(CassandraNode entity) {
            this.entity = entity;
        }
        public Long call() {
            Integer privatePort = entity.getThriftPort();
            if (privatePort == null) return -1L;
            
            HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, privatePort);

            try {
                long start = System.currentTimeMillis();
                Socket s = new Socket(hp.getHostText(), hp.getPort());
                s.close();
                long latency = System.currentTimeMillis() - start;
                return latency;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (log.isDebugEnabled())
                    log.debug("Cassandra thrift port poll failure: "+e);
                return -1L;
            }
        }
    }
}
