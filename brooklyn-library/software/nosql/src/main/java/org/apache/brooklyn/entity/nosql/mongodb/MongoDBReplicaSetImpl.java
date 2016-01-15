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
package org.apache.brooklyn.entity.nosql.mongodb;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/**
 * Implementation of {@link MongoDBReplicaSet}.
 *
 * Replica sets have a <i>minimum</i> of three members.
 *
 * Removal strategy is always {@link #NON_PRIMARY_REMOVAL_STRATEGY}.
 */
public class MongoDBReplicaSetImpl extends DynamicClusterImpl implements MongoDBReplicaSet {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBReplicaSetImpl.class);

    // Provides IDs for replica set members. The first member will have ID 0.
    private final AtomicInteger nextMemberId = new AtomicInteger(0);

    private MemberTrackingPolicy policy;
    private final AtomicBoolean mustInitialise = new AtomicBoolean(true);

    @SuppressWarnings("unchecked")
    protected static final List<AttributeSensor<Long>> SENSORS_TO_SUM = Arrays.asList(
        MongoDBServer.OPCOUNTERS_INSERTS,
        MongoDBServer.OPCOUNTERS_QUERIES,
        MongoDBServer.OPCOUNTERS_UPDATES,
        MongoDBServer.OPCOUNTERS_DELETES,
        MongoDBServer.OPCOUNTERS_GETMORE,
        MongoDBServer.OPCOUNTERS_COMMAND,
        MongoDBServer.NETWORK_BYTES_IN,
        MongoDBServer.NETWORK_BYTES_OUT,
        MongoDBServer.NETWORK_NUM_REQUESTS);
    
    public MongoDBReplicaSetImpl() {
    }

    /**
     * Manages member addition and removal.
     *
     * It's important that this is a single thread: the concurrent addition and removal
     * of members from the set would almost certainly have unintended side effects,
     * like reconfigurations using outdated ReplicaSetConfig instances.
     */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    /** true iff input is a non-null MongoDBServer with attribute REPLICA_SET_MEMBER_STATUS PRIMARY. */
    static final Predicate<Entity> IS_PRIMARY = new Predicate<Entity>() {
        // getPrimary relies on instanceof check
        @Override public boolean apply(@Nullable Entity input) {
            return input != null
                    && input instanceof MongoDBServer
                    && ReplicaSetMemberStatus.PRIMARY.equals(input.sensors().get(MongoDBServer.REPLICA_SET_MEMBER_STATUS));
        }
    };

    /** true iff. input is a non-null MongoDBServer with attribute REPLICA_SET_MEMBER_STATUS SECONDARY. */
    static final Predicate<Entity> IS_SECONDARY = new Predicate<Entity>() {
        @Override public boolean apply(@Nullable Entity input) {
            // getSecondaries relies on instanceof check
            return input != null
                    && input instanceof MongoDBServer
                    && ReplicaSetMemberStatus.SECONDARY.equals(input.sensors().get(MongoDBServer.REPLICA_SET_MEMBER_STATUS));
        }
    };

    /**
     * {@link Function} for use as the cluster's removal strategy. Chooses any entity with
     * {@link MongoDBServer#IS_PRIMARY_FOR_REPLICA_SET} true last of all.
     */
    private static final Function<Collection<Entity>, Entity> NON_PRIMARY_REMOVAL_STRATEGY = new Function<Collection<Entity>, Entity>() {
        @Override
        public Entity apply(@Nullable Collection<Entity> entities) {
            checkArgument(entities != null && entities.size() > 0, "Expect list of MongoDBServers to have at least one entry");
            return Iterables.tryFind(entities, Predicates.not(IS_PRIMARY)).or(Iterables.get(entities, 0));
        }
    };
    
    @Override
    public void init() {
        super.init();
        enrichers().add(Enrichers.builder()
                .aggregating(MongoDBAuthenticationMixins.ROOT_USERNAME)
                .publishing(MongoDBAuthenticationMixins.ROOT_USERNAME)
                .fromMembers()
                .valueToReportIfNoSensors(null)
                .computing(new RootUsernameReducer())
                .build());
    }

    public static class RootUsernameReducer implements Function<Collection<String>, String>{
        @Override
        public String apply(Collection<String> input) {
            // when authentication is used all members have the same value
            return (input == null || input.isEmpty()) ? null : Iterables.getFirst(input, null);
        };
    }

    /** @return {@link #NON_PRIMARY_REMOVAL_STRATEGY} */
    @Override
    public Function<Collection<Entity>, Entity> getRemovalStrategy() {
        return NON_PRIMARY_REMOVAL_STRATEGY;
    }

    @Override
    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> spec = config().get(MEMBER_SPEC);
        if (spec == null) {
            spec = EntitySpec.create(MongoDBServer.class);
            config().set(MEMBER_SPEC, spec);
        }
        MongoDBAuthenticationUtils.setAuthenticationConfig(spec, this);
        return spec;
    }

    /**
     * Sets {@link MongoDBServer#REPLICA_SET}.
     */
    @Override
    protected Map<?,?> getCustomChildFlags() {
        return ImmutableMap.builder()
                .putAll(super.getCustomChildFlags())
                .put(MongoDBServer.REPLICA_SET, getProxy())
                .build();
    }

    @Override
    public String getName() {
        // FIXME: Names must be unique if the replica sets are used in a sharded cluster
        return config().get(REPLICA_SET_NAME) + this.getId();
    }

    @Override
    public MongoDBServer getPrimary() {
        return Iterables.tryFind(getReplicas(), IS_PRIMARY).orNull();
    }

    @Override
    public Collection<MongoDBServer> getSecondaries() {
        return FluentIterable.from(getReplicas())
                .filter(IS_SECONDARY)
                .toList();
    }

    @Override
    public Collection<MongoDBServer> getReplicas() {
        return FluentIterable.from(getMembers())
                .transform(new Function<Entity, MongoDBServer>() {
                    @Override public MongoDBServer apply(Entity input) {
                        return MongoDBServer.class.cast(input);
                    }
                })
                .toList();
    }

    /**
     * Initialises the replica set with the given server as primary if {@link #mustInitialise} is true,
     * otherwise schedules the addition of a new secondary.
     */
    private void serverAdded(MongoDBServer server) {
        try {
            LOG.debug("Server added: {}. SERVICE_UP: {}", server, server.sensors().get(MongoDBServer.SERVICE_UP));

            // Set the primary if the replica set hasn't been initialised.
            if (mustInitialise.compareAndSet(true, false)) {
                if (LOG.isInfoEnabled())
                    LOG.info("First server up in {} is: {}", getName(), server);
                boolean replicaSetInitialised = server.initializeReplicaSet(getName(), nextMemberId.getAndIncrement());
                if (replicaSetInitialised) {
                    sensors().set(PRIMARY_ENTITY, server);
                    sensors().set(Startable.SERVICE_UP, true);
                } else {
                    ServiceStateLogic.ServiceNotUpLogic.updateNotUpIndicator(this, "initialization", "replicaset failed to initialize");
                    ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
                }
            } else {
                if (LOG.isDebugEnabled())
                    LOG.debug("Scheduling addition of member to {}: {}", getName(), server);
                addSecondaryWhenPrimaryIsNonNull(server);
            }
        } catch (Exception e) {
            ServiceStateLogic.ServiceNotUpLogic.updateNotUpIndicator((EntityLocal)server, "Failed to update replicaset", e);
        }
    }

    /**
     * Adds a server as a secondary in the replica set.
     * <p/>
     * If {@link #getPrimary} returns non-null submit the secondary to the primary's
     * {@link MongoDBClientSupport}. Otherwise, reschedule the task to run again in three
     * seconds time (in the hope that next time the primary will be available).
     */
    private void addSecondaryWhenPrimaryIsNonNull(final MongoDBServer secondary) {
        // TODO Don't use executor, use ExecutionManager
        executor.submit(new Runnable() {
            @Override
            public void run() {
                // SERVICE_UP is not guaranteed when additional members are added to the set.
                Boolean isAvailable = secondary.sensors().get(MongoDBServer.SERVICE_UP);
                MongoDBServer primary = getPrimary();
                boolean reschedule;
                if (Boolean.TRUE.equals(isAvailable) && primary != null) {
                    boolean added = primary.addMemberToReplicaSet(secondary, nextMemberId.incrementAndGet());
                    if (added) {
                        LOG.info("{} added to replica set {}", secondary, getName());
                        reschedule = false;
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} could not be added to replica set via {}; rescheduling", secondary, getName());
                        }
                        reschedule = true;
                    }
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Rescheduling addition of member {} to replica set {}: service_up={}, primary={}",
                            new Object[] {secondary, getName(), isAvailable, primary});
                    }
                    reschedule = true;
                }
                
                if (reschedule) {
                    // TODO Could limit number of retries
                    executor.schedule(this, 3, TimeUnit.SECONDS);
                }
            }
        });
    }

    /**
     * Removes a server from the replica set.
     * <p/>
     * Submits a task that waits for the member to be down and for the replica set to have a primary
     * member, then reconfigures the set to remove the member, to {@link #executor}. If either of the
     * two conditions are not met then the task reschedules itself.
     *
     * @param member The server to be removed from the replica set.
     */
    private void serverRemoved(final MongoDBServer member) {
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Scheduling removal of member from {}: {}", getName(), member);
            // FIXME is there a chance of race here?
            if (member.equals(sensors().get(PRIMARY_ENTITY)))
                sensors().set(PRIMARY_ENTITY, null);
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    // Wait until the server has been stopped before reconfiguring the set. Quoth the MongoDB doc:
                    // for best results always shut down the mongod instance before removing it from a replica set.
                    Boolean isAvailable = member.sensors().get(MongoDBServer.SERVICE_UP);
                    // Wait for the replica set to elect a new primary if the set is reconfiguring itself.
                    MongoDBServer primary = getPrimary();
                    boolean reschedule;

                    if (primary != null && !isAvailable) {
                        boolean removed = primary.removeMemberFromReplicaSet(member);
                        if (removed) {
                            LOG.info("Removed {} from replica set {}", member, getName());
                            reschedule = false;
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} could not be removed from replica set via {}; rescheduling", member, getName());
                            }
                            reschedule = true;
                        }

                    } else {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Rescheduling removal of member {} from replica set {}: service_up={}, primary={}",
                                    new Object[]{member, getName(), isAvailable, primary});
                        }
                        reschedule = true;
                    }

                    if (reschedule) {
                        // TODO Could limit number of retries
                        executor.schedule(this, 3, TimeUnit.SECONDS);
                    }
                }
            });
        } catch (Exception e) {
            ServiceStateLogic.ServiceNotUpLogic.updateNotUpIndicator((EntityLocal)member, "Failed to update replicaset", e);
        }
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        // Promises that all the cluster's members have SERVICE_UP true on returning.
        super.start(locations);
        policy = policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName(getName() + " membership tracker")
                .configure("group", this));

        for (AttributeSensor<Long> sensor: SENSORS_TO_SUM)
            enrichers().add(Enrichers.builder()
                    .aggregating(sensor)
                    .publishing(sensor)
                    .fromMembers()
                    .computingSum()
                    .valueToReportIfNoSensors(null)
                    .defaultValueForUnreportedSensors(null)
                    .build());
        
        // FIXME would it be simpler to have a *subscription* on four or five sensors on allMembers, including SERVICE_UP
        // (which we currently don't check), rather than an enricher, and call to an "update" method?
        enrichers().add(Enrichers.builder()
                .aggregating(MongoDBServer.REPLICA_SET_PRIMARY_ENDPOINT)
                .publishing(MongoDBServer.REPLICA_SET_PRIMARY_ENDPOINT)
                .fromMembers()
                .valueToReportIfNoSensors(null)
                .computing(new Function<Collection<String>, String>() {
                        @Override
                        public String apply(Collection<String> input) {
                            if (input==null || input.isEmpty()) return null;
                            Set<String> distinct = MutableSet.of();
                            for (String endpoint: input)
                                if (!Strings.isBlank(endpoint))
                                    distinct.add(endpoint);
                            if (distinct.size()>1)
                                LOG.warn("Mongo replica set "+MongoDBReplicaSetImpl.this+" detetcted multiple masters (transitioning?): "+distinct);
                            return input.iterator().next();
                        }})
                .build());

        enrichers().add(Enrichers.builder()
                .aggregating(MongoDBServer.MONGO_SERVER_ENDPOINT)
                .publishing(REPLICA_SET_ENDPOINTS)
                .fromMembers()
                .valueToReportIfNoSensors(null)
                .computing(new Function<Collection<String>, List<String>>() {
                        @Override
                        public List<String> apply(Collection<String> input) {
                            Set<String> endpoints = new TreeSet<String>();
                            for (String endpoint: input) {
                                if (!Strings.isBlank(endpoint)) {
                                    endpoints.add(endpoint);
                                }
                            }
                            return MutableList.copyOf(endpoints);
                        }})
                .build());
        
        enrichers().add(Enrichers.builder()
                .transforming(REPLICA_SET_ENDPOINTS)
                .publishing(DATASTORE_URL)
                .computing(new EndpointsToDatastoreUrlMapper(this))
                .build());

        subscriptions().subscribeToMembers(this, MongoDBServer.IS_PRIMARY_FOR_REPLICA_SET, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                if (Boolean.TRUE == event.getValue())
                    sensors().set(PRIMARY_ENTITY, (MongoDBServer)event.getSource());
            }
        });

    }
    
    public static class EndpointsToDatastoreUrlMapper implements Function<Collection<String>, String> {
        
        private Entity entity;

        public EndpointsToDatastoreUrlMapper(Entity entity) {
            this.entity = entity;
        }
        
        @Override
        public String apply(Collection<String> input) {
            String credentials = MongoDBAuthenticationUtils.usesAuthentication(entity) 
                    ? String.format("%s:%s@", 
                            entity.config().get(MongoDBAuthenticationMixins.ROOT_USERNAME), 
                            entity.config().get(MongoDBAuthenticationMixins.ROOT_PASSWORD)) 
                    : "";
            return String.format("mongodb://%s%s", credentials, Strings.join(input, ","));
        }
    }

    @Override
    public void stop() {
        // Do we want to remove the members from the replica set?
        //  - if the set is being stopped forever it's irrelevant
        //  - if the set might be restarted I think it just inconveniences us
        // Terminate the executor immediately.
        // TODO Note that after this the executor will not run if the set is restarted.
        executor.shutdownNow();
        super.stop();
        sensors().set(Startable.SERVICE_UP, false);
    }

    @Override
    public void onManagementStopped() {
        super.onManagementStopped();
        executor.shutdownNow();
    }
    
    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityChange(Entity member) {
            // Ignored
        }
        @Override protected void onEntityAdded(Entity member) {
            ((MongoDBReplicaSetImpl) entity).serverAdded((MongoDBServer) member);
        }
        @Override protected void onEntityRemoved(Entity member) {
            ((MongoDBReplicaSetImpl) entity).serverRemoved((MongoDBServer) member);
        }
    }
}
