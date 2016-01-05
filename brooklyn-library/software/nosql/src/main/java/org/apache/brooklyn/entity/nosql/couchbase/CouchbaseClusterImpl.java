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
package org.apache.brooklyn.entity.nosql.couchbase;

import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.feed.http.JsonFunctions;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.guava.IfFunctions;
import org.apache.brooklyn.util.math.MathPredicates;
import org.apache.brooklyn.util.text.ByteSizeStrings;
import org.apache.brooklyn.util.text.StringFunctions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class CouchbaseClusterImpl extends DynamicClusterImpl implements CouchbaseCluster {
    
    /*
     * Refactoring required:
     * 
     * Currently, on start() the cluster waits for an arbitrary SERVICE_UP_TIME_OUT (3 minutes) before assuming that a quorate 
     * number of servers are available. The servers are then added to the cluster, and a further wait period of  
     * DELAY_BEFORE_ADVERTISING_CLUSTER (30 seconds) is used before advertising the cluster
     * 
     * DELAY_BEFORE_ADVERTISING_CLUSTER: It should be possible to refactor this away by adding a repeater that will poll
     * the REST API of the primary node (once established) until the API indicates that the cluster is available
     * 
     * SERVICE_UP_TIME_OUT: The refactoring of this would be more substantial. One method would be to remove the bulk of the 
     * logic from the start() method, and rely entirely on the membership tracking policy and the onServerPoolMemberChanged()
     * method. The addition of a RUNNING sensor on the nodes would allow the cluster to determine that a node is up and
     * running but has not yet been added to the cluster. The IS_CLUSTER_INITIALIZED key could be used to determine whether
     * or not the cluster should be initialized, or a node simply added to an existing cluster. A repeater could be used
     * in the driver's to ensure that the method does not return until the node has been fully added
     * 
     * There is an (incomplete) first-pass at this here: https://github.com/Nakomis/incubator-brooklyn/compare/couchbase-running-sensor
     * however, there have been significant changes to the cluster initialization since that work was done so it will probably
     * need to be re-done
     * 
     * Additionally, during bucket creation, a HttpPoll is used to check that the bucket has been created. This should be 
     * refactored to use a Repeater in CouchbaseNodeSshDriver.bucketCreate() in a similar way to the one employed in
     * CouchbaseNodeSshDriver.rebalance(). Were this done, this class could simply queue the bucket creation tasks
     * 
     */
    
    private static final Logger log = LoggerFactory.getLogger(CouchbaseClusterImpl.class);
    private final Object mutex = new Object[0];
    // Used to serialize bucket creation as only one bucket can be created at a time,
    // so a feed is used to determine when a bucket has finished being created
    private final AtomicReference<HttpFeed> resetBucketCreation = new AtomicReference<HttpFeed>();

    public void init() {
        log.info("Initializing the Couchbase cluster...");
        super.init();
        
        enrichers().add(
            Enrichers.builder()
                .transforming(COUCHBASE_CLUSTER_UP_NODES)
                .from(this)
                .publishing(COUCHBASE_CLUSTER_UP_NODE_ADDRESSES)
                .computing(new ListOfHostAndPort()).build() );
        enrichers().add(
            Enrichers.builder()
                .transforming(COUCHBASE_CLUSTER_UP_NODE_ADDRESSES)
                .from(this)
                .publishing(COUCHBASE_CLUSTER_CONNECTION_URL)
                .computing(
                    IfFunctions.<List<String>>ifPredicate(
                        Predicates.compose(MathPredicates.lessThan(getConfig(CouchbaseCluster.INITIAL_QUORUM_SIZE)), 
                            CollectionFunctionals.sizeFunction(0)) )
                    .value((String)null)
                    .defaultApply(
                        Functionals.chain(
                            CollectionFunctionals.<String,List<String>>limit(4), 
                            StringFunctions.joiner(","),
                            StringFunctions.formatter("http://%s/"))) )
                .build() );
        
        Map<? extends AttributeSensor<? extends Number>, ? extends AttributeSensor<? extends Number>> enricherSetup = 
            ImmutableMap.<AttributeSensor<? extends Number>, AttributeSensor<? extends Number>>builder()
                .put(CouchbaseNode.OPS, CouchbaseCluster.OPS_PER_NODE)
                .put(CouchbaseNode.COUCH_DOCS_DATA_SIZE, CouchbaseCluster.COUCH_DOCS_DATA_SIZE_PER_NODE)
                .put(CouchbaseNode.COUCH_DOCS_ACTUAL_DISK_SIZE, CouchbaseCluster.COUCH_DOCS_ACTUAL_DISK_SIZE_PER_NODE)
                .put(CouchbaseNode.EP_BG_FETCHED, CouchbaseCluster.EP_BG_FETCHED_PER_NODE)
                .put(CouchbaseNode.MEM_USED, CouchbaseCluster.MEM_USED_PER_NODE)
                .put(CouchbaseNode.COUCH_VIEWS_ACTUAL_DISK_SIZE, CouchbaseCluster.COUCH_VIEWS_ACTUAL_DISK_SIZE_PER_NODE)
                .put(CouchbaseNode.CURR_ITEMS, CouchbaseCluster.CURR_ITEMS_PER_NODE)
                .put(CouchbaseNode.VB_REPLICA_CURR_ITEMS, CouchbaseCluster.VB_REPLICA_CURR_ITEMS_PER_NODE)
                .put(CouchbaseNode.COUCH_VIEWS_DATA_SIZE, CouchbaseCluster.COUCH_VIEWS_DATA_SIZE_PER_NODE)
                .put(CouchbaseNode.GET_HITS, CouchbaseCluster.GET_HITS_PER_NODE)
                .put(CouchbaseNode.CMD_GET, CouchbaseCluster.CMD_GET_PER_NODE)
                .put(CouchbaseNode.CURR_ITEMS_TOT, CouchbaseCluster.CURR_ITEMS_TOT_PER_NODE)
            .build();
        
        for (AttributeSensor<? extends Number> nodeSensor : enricherSetup.keySet()) {
            addSummingMemberEnricher(nodeSensor);
            addAveragingMemberEnricher(nodeSensor, enricherSetup.get(nodeSensor));
        }
        
        enrichers().add(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
            .from(IS_CLUSTER_INITIALIZED).computing(
                IfFunctions.ifNotEquals(true).value("The cluster is not yet completely initialized")
                    .defaultValue(null).build()).build() );
    }
    
    private void addAveragingMemberEnricher(AttributeSensor<? extends Number> fromSensor, AttributeSensor<? extends Number> toSensor) {
        enrichers().add(Enrichers.builder()
            .aggregating(fromSensor)
            .publishing(toSensor)
            .fromMembers()
            .computingAverage()
            .build()
        );
    }

    private void addSummingMemberEnricher(AttributeSensor<? extends Number> source) {
        enrichers().add(Enrichers.builder()
            .aggregating(source)
            .publishing(source)
            .fromMembers()
            .computingSum()
            .build()
        );
    }

    @Override
    protected void doStart() {
        sensors().set(IS_CLUSTER_INITIALIZED, false);
        
        super.doStart();

        connectSensors();
        
        sensors().set(BUCKET_CREATION_IN_PROGRESS, false);

        //start timeout before adding the servers
        Tasks.setBlockingDetails("Pausing while Couchbase stabilizes");
        Time.sleep(getConfig(NODES_STARTED_STABILIZATION_DELAY));

        Optional<Set<Entity>> upNodes = Optional.<Set<Entity>>fromNullable(getAttribute(COUCHBASE_CLUSTER_UP_NODES));
        if (upNodes.isPresent() && !upNodes.get().isEmpty()) {

            Tasks.setBlockingDetails("Adding servers to Couchbase");
            
            //TODO: select a new primary node if this one fails
            Entity primaryNode = upNodes.get().iterator().next();
            ((EntityInternal) primaryNode).sensors().set(CouchbaseNode.IS_PRIMARY_NODE, true);
            sensors().set(COUCHBASE_PRIMARY_NODE, primaryNode);

            Set<Entity> serversToAdd = MutableSet.<Entity>copyOf(getUpNodes());

            if (serversToAdd.size() >= getQuorumSize() && serversToAdd.size() > 1) {
                log.info("Number of SERVICE_UP nodes:{} in cluster:{} reached Quorum:{}, adding the servers", new Object[]{serversToAdd.size(), getId(), getQuorumSize()});
                addServers(serversToAdd);

                //wait for servers to be added to the couchbase server
                try {
                    Tasks.setBlockingDetails("Delaying before advertising cluster up");
                    Time.sleep(getConfig(DELAY_BEFORE_ADVERTISING_CLUSTER));
                } finally {
                    Tasks.resetBlockingDetails();
                }
                
                ((CouchbaseNode)getPrimaryNode()).rebalance();
            } else {
                if (getQuorumSize()>1) {
                    log.warn(this+" is not quorate; will likely fail later, but proceeding for now");
                }
                for (Entity server: serversToAdd) {
                    ((EntityInternal) server).sensors().set(CouchbaseNode.IS_IN_CLUSTER, true);
                }
            }
                
            if (getConfig(CREATE_BUCKETS)!=null) {
                try {
                    Tasks.setBlockingDetails("Creating buckets in Couchbase");

                    createBuckets();
                    DependentConfiguration.waitInTaskForAttributeReady(this, CouchbaseCluster.BUCKET_CREATION_IN_PROGRESS, Predicates.equalTo(false));

                } finally {
                    Tasks.resetBlockingDetails();
                }
            }

            if (getConfig(REPLICATION)!=null) {
                try {
                    Tasks.setBlockingDetails("Configuring replication rules");

                    List<Map<String, Object>> replRules = getConfig(REPLICATION);
                    for (Map<String, Object> replRule: replRules) {
                        DynamicTasks.queue(Effectors.invocation(getPrimaryNode(), CouchbaseNode.ADD_REPLICATION_RULE, replRule));
                    }
                    DynamicTasks.waitForLast();

                } finally {
                    Tasks.resetBlockingDetails();
                }
            }

            sensors().set(IS_CLUSTER_INITIALIZED, true);
            
        } else {
            throw new IllegalStateException("No up nodes available after starting");
        }
    }

    @Override
    public void stop() {
        if (resetBucketCreation.get() != null) {
            resetBucketCreation.get().stop();
        }
        super.stop();
    }

    protected void connectSensors() {
        policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Controller targets tracker")
                .configure("group", this));
    }
    
    private final static class ListOfHostAndPort implements Function<Set<Entity>, List<String>> {
        @Override public List<String> apply(Set<Entity> input) {
            List<String> addresses = Lists.newArrayList();
            for (Entity entity : input) {
                addresses.add(String.format("%s",
                        BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, entity.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT))));
            }
            return addresses;
        }
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityChange(Entity member) {
            ((CouchbaseClusterImpl)entity).onServerPoolMemberChanged(member);
        }

        @Override protected void onEntityAdded(Entity member) {
            ((CouchbaseClusterImpl)entity).onServerPoolMemberChanged(member);
        }

        @Override protected void onEntityRemoved(Entity member) {
            ((CouchbaseClusterImpl)entity).onServerPoolMemberChanged(member);
        }
    };

    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (log.isTraceEnabled()) log.trace("For {}, considering membership of {} which is in locations {}",
                new Object[]{this, member, member.getLocations()});

        //FIXME: make use of servers to be added after cluster initialization.
        synchronized (mutex) {
            if (belongsInServerPool(member)) {

                Optional<Set<Entity>> upNodes = Optional.fromNullable(getUpNodes());
                if (upNodes.isPresent()) {

                    if (!upNodes.get().contains(member)) {
                        Set<Entity> newNodes = Sets.newHashSet(getUpNodes());
                        newNodes.add(member);
                        sensors().set(COUCHBASE_CLUSTER_UP_NODES, newNodes);

                        //add to set of servers to be added.
                        if (isClusterInitialized()) {
                            addServer(member);
                        }
                    }
                } else {
                    Set<Entity> newNodes = Sets.newHashSet();
                    newNodes.add(member);
                    sensors().set(COUCHBASE_CLUSTER_UP_NODES, newNodes);

                    if (isClusterInitialized()) {
                        addServer(member);
                    }
                }
            } else {
                Set<Entity> upNodes = getUpNodes();
                if (upNodes != null && upNodes.contains(member)) {
                    upNodes.remove(member);
                    sensors().set(COUCHBASE_CLUSTER_UP_NODES, upNodes);
                    log.info("Removing couchbase node {}: {}; from cluster", new Object[]{this, member});
                }
            }
            if (log.isTraceEnabled()) log.trace("Done {} checkEntity {}", this, member);
        }
    }

    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getMembers().contains(member)) {
            if (log.isTraceEnabled())
                log.trace("Members of {}, checking {}, eliminating because not member", this, member);

            return false;
        }
        if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, approving", this, member);

        return true;
    }


    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> result = super.getMemberSpec();
        if (result != null) return result;
        return EntitySpec.create(CouchbaseNode.class);
    }

    @Override
    public int getQuorumSize() {
        Integer quorumSize = getConfig(CouchbaseCluster.INITIAL_QUORUM_SIZE);
        if (quorumSize != null && quorumSize > 0)
            return quorumSize;
        // by default the quorum would be floor(initial_cluster_size/2) + 1
        return (int) Math.floor(getConfig(INITIAL_SIZE) / 2) + 1;
    }

    protected int getActualSize() {
        return Optional.fromNullable(getAttribute(CouchbaseCluster.ACTUAL_CLUSTER_SIZE)).or(-1);
    }

    private Set<Entity> getUpNodes() {
        return getAttribute(COUCHBASE_CLUSTER_UP_NODES);
    }

    private CouchbaseNode getPrimaryNode() {
        return (CouchbaseNode) getAttribute(COUCHBASE_PRIMARY_NODE);
    }

    @Override
    protected void initEnrichers() {
        enrichers().add(Enrichers.builder().updatingMap(ServiceStateLogic.SERVICE_NOT_UP_INDICATORS)
            .from(COUCHBASE_CLUSTER_UP_NODES)
            .computing(new Function<Set<Entity>, Object>() {
                @Override
                public Object apply(Set<Entity> input) {
                    if (input==null) return "Couchbase up nodes not set";
                    if (input.isEmpty()) return "No Couchbase up nodes";
                    if (input.size() < getQuorumSize()) return "Couchbase up nodes not quorate";
                    return null;
                }
            }).build());
        
        if (config().getLocalRaw(UP_QUORUM_CHECK).isAbsent()) {
            // TODO Only leaving CouchbaseQuorumCheck here in case it is contained in persisted state.
            // If so, need a transformer and then to delete it
            @SuppressWarnings({ "unused", "hiding" })
            @Deprecated
            class CouchbaseQuorumCheck implements QuorumCheck {
                @Override
                public boolean isQuorate(int sizeHealthy, int totalSize) {
                    // check members count passed in AND the sensor  
                    if (sizeHealthy < getQuorumSize()) return false;
                    return true;
                }
            }
            config().set(UP_QUORUM_CHECK, new CouchbaseClusterImpl.CouchbaseQuorumCheck(this));
        }
        super.initEnrichers();
    }
    
    static class CouchbaseQuorumCheck implements QuorumCheck {
        private final CouchbaseCluster cluster;
        CouchbaseQuorumCheck(CouchbaseCluster cluster) {
            this.cluster = cluster;
        }
        @Override
        public boolean isQuorate(int sizeHealthy, int totalSize) {
            // check members count passed in AND the sensor  
            if (sizeHealthy < cluster.getQuorumSize()) return false;
            return true;
        }
    }
    protected void addServers(Set<Entity> serversToAdd) {
        Preconditions.checkNotNull(serversToAdd);
        for (Entity s : serversToAdd) {
            addServerSeveralTimes(s, 12, Duration.TEN_SECONDS);
        }
    }

    /** try adding in a loop because we are seeing spurious port failures in AWS */
    protected void addServerSeveralTimes(Entity s, int numRetries, Duration delayOnFailure) {
        try {
            addServer(s);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            if (numRetries<=0) throw Exceptions.propagate(e);
            // retry once after sleep because we are getting some odd primary-change events
            log.warn("Error adding "+s+" to "+this+", "+numRetries+" retries remaining, will retry after delay ("+e+")");
            Time.sleep(delayOnFailure);
            addServerSeveralTimes(s, numRetries-1, delayOnFailure);
        }
    }

    protected void addServer(Entity serverToAdd) {
        Preconditions.checkNotNull(serverToAdd);
        if (serverToAdd.equals(getPrimaryNode())) {
            // no need to add; but we pass it in anyway because it makes the calling logic easier
            return;
        }
        if (!isMemberInCluster(serverToAdd)) {
            HostAndPort webAdmin = HostAndPort.fromParts(serverToAdd.getAttribute(SoftwareProcess.SUBNET_HOSTNAME),
                    serverToAdd.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT));
            String username = serverToAdd.getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME);
            String password = serverToAdd.getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD);

            if (isClusterInitialized()) {
                Entities.invokeEffectorWithArgs(this, getPrimaryNode(), CouchbaseNode.SERVER_ADD_AND_REBALANCE, webAdmin.toString(), username, password).getUnchecked();
            } else {
                Entities.invokeEffectorWithArgs(this, getPrimaryNode(), CouchbaseNode.SERVER_ADD, webAdmin.toString(), username, password).getUnchecked();
            }
            //FIXME check feedback of whether the server was added.
            ((EntityInternal) serverToAdd).sensors().set(CouchbaseNode.IS_IN_CLUSTER, true);
        }
    }

    /** finds the cluster name specified for a node or a cluster, 
     * using {@link CouchbaseCluster#CLUSTER_NAME} or falling back to the cluster (or node) ID. */
    public static String getClusterName(Entity node) {
        String name = node.getConfig(CLUSTER_NAME);
        if (!Strings.isBlank(name)) return Strings.makeValidFilename(name);
        return getClusterOrNode(node).getId();
    }
    
    /** returns Couchbase cluster in ancestry, defaulting to the given node if none */
    @Nonnull public static Entity getClusterOrNode(Entity node) {
        Iterable<CouchbaseCluster> clusterNodes = Iterables.filter(Entities.ancestors(node), CouchbaseCluster.class);
        return Iterables.getFirst(clusterNodes, node);
    }
    
    public boolean isClusterInitialized() {
        return Optional.fromNullable(getAttribute(IS_CLUSTER_INITIALIZED)).or(false);
    }

    public boolean isMemberInCluster(Entity e) {
        return Optional.fromNullable(e.getAttribute(CouchbaseNode.IS_IN_CLUSTER)).or(false);
    }
    
    public void createBuckets() {
        //TODO: check for port conflicts if buckets are being created with a port
        List<Map<String, Object>> bucketsToCreate = getConfig(CREATE_BUCKETS);
        if (bucketsToCreate==null) return;
        
        Entity primaryNode = getPrimaryNode();

        for (Map<String, Object> bucketMap : bucketsToCreate) {
            String bucketName = bucketMap.containsKey("bucket") ? (String) bucketMap.get("bucket") : "default";
            String bucketType = bucketMap.containsKey("bucket-type") ? (String) bucketMap.get("bucket-type") : "couchbase";
            // default bucket must be on this port; other buckets can (must) specify their own (unique) port
            Integer bucketPort = bucketMap.containsKey("bucket-port") ? (Integer) bucketMap.get("bucket-port") : 11211;
            Integer bucketRamSize = bucketMap.containsKey("bucket-ramsize") ? (Integer) bucketMap.get("bucket-ramsize") : 100;
            Integer bucketReplica = bucketMap.containsKey("bucket-replica") ? (Integer) bucketMap.get("bucket-replica") : 1;

            createBucket(primaryNode, bucketName, bucketType, bucketPort, bucketRamSize, bucketReplica);
        }
    }

    public void createBucket(final Entity primaryNode, final String bucketName, final String bucketType, final Integer bucketPort, final Integer bucketRamSize, final Integer bucketReplica) {
        DynamicTasks.queueIfPossible(TaskBuilder.<Void>builder().displayName("Creating bucket " + bucketName).body(
                new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        DependentConfiguration.waitInTaskForAttributeReady(CouchbaseClusterImpl.this, CouchbaseCluster.BUCKET_CREATION_IN_PROGRESS, Predicates.equalTo(false));
                        if (CouchbaseClusterImpl.this.resetBucketCreation.get() != null) {
                            CouchbaseClusterImpl.this.resetBucketCreation.get().stop();
                        }
                        sensors().set(CouchbaseCluster.BUCKET_CREATION_IN_PROGRESS, true);
                        HostAndPort hostAndPort = BrooklynAccessUtils.getBrooklynAccessibleAddress(primaryNode, primaryNode.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT));

                        CouchbaseClusterImpl.this.resetBucketCreation.set(HttpFeed.builder()
                                .entity(CouchbaseClusterImpl.this)
                                .period(500, TimeUnit.MILLISECONDS)
                                .baseUri(String.format("http://%s/pools/default/buckets/%s", hostAndPort, bucketName))
                                .credentials(primaryNode.getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME), primaryNode.getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD))
                                .poll(new HttpPollConfig<Boolean>(BUCKET_CREATION_IN_PROGRESS)
                                        .onSuccess(Functionals.chain(HttpValueFunctions.jsonContents(), JsonFunctions.walkN("nodes"), new Function<JsonElement, Boolean>() {
                                            @Override
                                            public Boolean apply(JsonElement input) {
                                                // Wait until bucket has been created on all nodes and the couchApiBase element has been published (indicating that the bucket is useable)
                                                JsonArray servers = input.getAsJsonArray();
                                                if (servers.size() != CouchbaseClusterImpl.this.getMembers().size()) {
                                                    return true;
                                                }
                                                for (JsonElement server : servers) {
                                                    Object api = server.getAsJsonObject().get("couchApiBase");
                                                    if (api == null || Strings.isEmpty(String.valueOf(api))) {
                                                        return true;
                                                    }
                                                }
                                                return false;
                                            }
                                        }))
                                        .onFailureOrException(new Function<Object, Boolean>() {
                                            @Override
                                            public Boolean apply(Object input) {
                                                if (input instanceof HttpToolResponse) {
                                                    if (((HttpToolResponse) input).getResponseCode() == 404) {
                                                        return true;
                                                    }
                                                }
                                                if (input instanceof Throwable)
                                                    Exceptions.propagate((Throwable) input);
                                                throw new IllegalStateException("Unexpected response when creating bucket:" + input);
                                            }
                                        }))
                                .build());

                        // TODO: Bail out if bucket creation fails, to allow next bucket to proceed
                        Entities.invokeEffectorWithArgs(CouchbaseClusterImpl.this, primaryNode, CouchbaseNode.BUCKET_CREATE, bucketName, bucketType, bucketPort, bucketRamSize, bucketReplica);
                        DependentConfiguration.waitInTaskForAttributeReady(CouchbaseClusterImpl.this, CouchbaseCluster.BUCKET_CREATION_IN_PROGRESS, Predicates.equalTo(false));
                        if (CouchbaseClusterImpl.this.resetBucketCreation.get() != null) {
                            CouchbaseClusterImpl.this.resetBucketCreation.get().stop();
                        }
                        return null;
                    }
                }
        ).build()).orSubmitAndBlock();
    }
    
    static {
        RendererHints.register(COUCH_DOCS_DATA_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(COUCH_DOCS_ACTUAL_DISK_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(MEM_USED_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(COUCH_VIEWS_ACTUAL_DISK_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(COUCH_VIEWS_DATA_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
    }
}
