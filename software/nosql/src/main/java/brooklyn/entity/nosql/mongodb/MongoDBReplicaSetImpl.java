package brooklyn.entity.nosql.mongodb;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;

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

    // 8th+ members should have 0 votes
    private static final int MIN_MEMBERS = 3;
    private static final int MAX_MEMBERS = 7;

    // Provides IDs for replica set members. The first member will have ID 0.
    private final AtomicInteger nextMemberId = new AtomicInteger(0);

    private AbstractMembershipTrackingPolicy policy;
    private final AtomicBoolean mustInitialise = new AtomicBoolean(true);

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
                    && ReplicaSetMemberStatus.PRIMARY.equals(input.getAttribute(MongoDBServer.REPLICA_SET_MEMBER_STATUS));
        }
    };

    /** true iff. input is a non-null MongoDBServer with attribute REPLICA_SET_MEMBER_STATUS SECONDARY. */
    static final Predicate<Entity> IS_SECONDARY = new Predicate<Entity>() {
        @Override public boolean apply(@Nullable Entity input) {
            // getSecondaries relies on instanceof check
            return input != null
                    && input instanceof MongoDBServer
                    && ReplicaSetMemberStatus.SECONDARY.equals(input.getAttribute(MongoDBServer.REPLICA_SET_MEMBER_STATUS));
        }
    };

    /**
     * {@link Function} for use as the cluster's removal strategy. Chooses any entity with
     * {@link MongoDBServer#REPLICA_SET_PRIMARY} true last of all.
     */
    private static final Function<Collection<Entity>, Entity> NON_PRIMARY_REMOVAL_STRATEGY = new Function<Collection<Entity>, Entity>() {
        @Override
        public Entity apply(@Nullable Collection<Entity> entities) {
            checkArgument(entities != null && entities.size() > 0, "Expect list of MongoDBServers to have at least one entry");
            return Iterables.tryFind(entities, Predicates.not(IS_PRIMARY)).or(Iterables.get(entities, 0));
        }
    };

    /** @return {@link #NON_PRIMARY_REMOVAL_STRATEGY} */
    @Override
    public Function<Collection<Entity>, Entity> getRemovalStrategy() {
        return NON_PRIMARY_REMOVAL_STRATEGY;
    }

    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, BasicEntitySpec.newInstance(MongoDBServer.class));
    }

    /**
     * Sets {@link MongoDBServer#REPLICA_SET_ENABLED} and {@link MongoDBServer#REPLICA_SET_NAME}.
     */
    @Override
    protected Map getCustomChildFlags() {
        return ImmutableMap.builder()
                .putAll(super.getCustomChildFlags())
                .put(MongoDBServer.REPLICA_SET_ENABLED, true)
                .put(MongoDBServer.REPLICA_SET_NAME, getReplicaSetName())
                .build();
    }

    @Override
    public String getReplicaSetName() {
        return getConfig(REPLICA_SET_NAME);
    }

    @Override
    public MongoDBServer getPrimary() {
        return (MongoDBServer) Iterables.tryFind(getMembers(), IS_PRIMARY).orNull();
    }

    @Override
    public Collection<MongoDBServer> getSecondaries() {
        // IS_SECONDARY predicate guarantees cast in transform is safe.
        return FluentIterable.from(getMembers())
                .filter(IS_SECONDARY)
                .transform(new Function<Entity, MongoDBServer>() {
                    @Override public MongoDBServer apply(@Nullable Entity input) {
                        return MongoDBServer.class.cast(input);
                    }
                })
                .toList();
    }

    /**
     * Ignore attempts to resize the replica set to an even number of entities to avoid
     * having to introduce arbiters.
     * @see <a href="http://docs.mongodb.org/manual/administration/replica-set-architectures/#arbiters">
     *         http://docs.mongodb.org/manual/administration/replica-set-architectures/#arbiters</a>
     * @param desired
     *          The new size of the entity group. Ignored if even, less than {@link #MIN_MEMBERS}
     *          or more than {@link #MAX_MEMBERS}.
     * @return The eventual size of the replica set.
     */
    @Override
    public Integer resize(Integer desired) {
        if ((desired >= MIN_MEMBERS && desired <= MAX_MEMBERS && desired % 2 == 1) || desired == 0)
            return super.resize(desired);
        if (desired % 2 == 0)
            LOG.info("Ignored request to resize replica set {} to even number of members", getReplicaSetName());
        if (desired < MIN_MEMBERS)
            LOG.info("Ignored request to resize replica set {} to because smaller than min size of {}", getReplicaSetName(), MIN_MEMBERS);
        if (desired > MAX_MEMBERS)
            LOG.info("Ignored request to resize replica set {} to because larger than max size of {}", getReplicaSetName(), MAX_MEMBERS);
        return getCurrentSize();
    }

    /**
     * Initialises the replica set with the given server as primary if {@link #mustInitialise} is true,
     * otherwise schedules the addition of a new secondary.
     */
    private void serverAdded(MongoDBServer server) {
        LOG.debug("Server added: {}. SERVICE_UP: {}", server, server.getAttribute(MongoDBServer.SERVICE_UP));

        // Set the primary if the replica set hasn't been initialised.
        if (mustInitialise.compareAndSet(true, false)) {
            if (LOG.isInfoEnabled())
                LOG.info("First server up in {} is: {}", getReplicaSetName(), server);
            server.getClient().initializeReplicaSet(getReplicaSetName(), nextMemberId.getAndIncrement());
            setAttribute(PRIMARY, server);
            setAttribute(Startable.SERVICE_UP, true);
        } else {
            if (LOG.isDebugEnabled())
                LOG.debug("Scheduling addition of member to {}: {}", getReplicaSetName(), server);
            executor.submit(addSecondaryWhenPrimaryIsNonNull(server));
        }
    }

    /**
     * Adds a server as a secondary in the replica set.
     * <p/>
     * If {@link #getPrimary} returns non-null submit the secondary to the primary's
     * {@link MongoClientSupport}. Otherwise, reschedule the task to run again in three
     * seconds time (in the hope that next time the primary will be available).
     */
    private Runnable addSecondaryWhenPrimaryIsNonNull(final MongoDBServer secondary) {
        return new Runnable() {
            @Override
            public void run() {
                // SERVICE_UP is not guaranteed when additional members are added to the set.
                Boolean isAvailable = secondary.getAttribute(MongoDBServer.SERVICE_UP);
                MongoDBServer primary = getPrimary();
                if (isAvailable && primary != null) {
                    primary.getClient().addMemberToReplicaSet(secondary, nextMemberId.incrementAndGet());
                    if (LOG.isInfoEnabled()) {
                        LOG.info("{} added to replica set {}", secondary, getReplicaSetName());
                    }
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Rescheduling addition of member {} to replica set {}: service_up={}, primary={}",
                            new Object[]{secondary, getReplicaSetName(), isAvailable, primary});
                    }
                    // Could limit number of retries
                    executor.schedule(this, 3, TimeUnit.SECONDS);
                }
            }
        };
    }

    private void serverRemoved(MongoDBServer server) {
        if (LOG.isDebugEnabled())
            LOG.debug("Scheduling removal of member from {}: {}", getReplicaSetName(), server);
        executor.submit(removeMember(server));
    }

    private Runnable removeMember(final MongoDBServer member) {
        return new Runnable() {
            @Override
            public void run() {
                // Wait until the server has been stopped before reconfiguring the set. Quoth the MongoDB doc:
                // for best results always shut down the mongod instance before removing it from a replica set.
                Boolean isAvailable = member.getAttribute(MongoDBServer.SERVICE_UP);
                // Wait for the replica set to elect a new primary if the set is reconfiguring itself.
                MongoDBServer primary = getPrimary();
                if (primary != null && !isAvailable) {
                    primary.getClient().removeMemberFromReplicaSet(member);
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Removed {} from replica set {}", member, getReplicaSetName());
                    }
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Rescheduling removal of member {} from replica set {}: service_up={}, primary={}",
                            new Object[]{member, getReplicaSetName(), isAvailable, primary});
                    }
                    executor.schedule(this, 3, TimeUnit.SECONDS);
                }
            }
        };
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        // Promises that all the cluster's members have SERVICE_UP true on returning.
        super.start(locations);
        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", getReplicaSetName() + " membership tracker")) {
            @Override protected void onEntityChange(Entity member) {
                // Ignored
            }
            @Override protected void onEntityAdded(Entity member) {
                serverAdded((MongoDBServer) member);
            }
            @Override protected void onEntityRemoved(Entity member) {
                serverRemoved((MongoDBServer) member);
            }
        };

        addPolicy(policy);
        policy.setGroup(this);
    }

    @Override
    public void stop() {
        // Do we want to remove the members from the replica set?
        //  - if the set is being stopped forever it's irrelevant
        //  - if the set might be restarted I think it just inconveniences us
        // Terminate the executor immediately.
        // Note that after this the executor will not run if the set is restarted.
        executor.shutdownNow();
        super.stop();
        setAttribute(Startable.SERVICE_UP, false);
    }

}
