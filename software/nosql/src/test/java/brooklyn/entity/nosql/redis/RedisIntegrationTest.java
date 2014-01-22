package brooklyn.entity.nosql.redis;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

/**
 * Test the operation of the {@link RedisStore} class.
 */
public class RedisIntegrationTest {

    private TestApplication app;
    private Location loc;
    private RedisStore redis;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that the server starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = { "Integration" })
    public void canStartupAndShutdown() throws Exception {
        redis = app.createAndManageChild(EntitySpec.create(RedisStore.class));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(redis, Startable.SERVICE_UP, true);

        redis.stop();

        EntityTestUtils.assertAttributeEqualsEventually(redis, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a client can connect to the service.
     */
    @Test(groups = { "Integration" })
    public void testRedisConnection() throws Exception {
        redis = app.createAndManageChild(EntitySpec.create(RedisStore.class));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(redis, Startable.SERVICE_UP, true);

        JedisSupport support = new JedisSupport(redis);
        support.redisTest();
    }

    /**
     * Test we get sensors from an instance on a non-default port
     */
    @Test(groups = { "Integration" })
    public void testNonStandardPort() throws Exception {
        redis = app.createAndManageChild(EntitySpec.create(RedisStore.class)
                .configure(RedisStore.REDIS_PORT, PortRanges.fromString("10000+")));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(redis, Startable.SERVICE_UP, true);
        JedisSupport support = new JedisSupport(redis);
        support.redisTest();

        EntityTestUtils.assertPredicateEventuallyTrue(redis, new Predicate<RedisStore>() {
            @Override public boolean apply(@Nullable RedisStore input) {
                return input != null &&
                        input.getAttribute(RedisStore.UPTIME) > 0 &&
                        input.getAttribute(RedisStore.TOTAL_COMMANDS_PROCESSED) >= 0 &&
                        input.getAttribute(RedisStore.TOTAL_CONNECTIONS_RECEIVED) >= 0 &&
                        input.getAttribute(RedisStore.EXPIRED_KEYS) >= 0 &&
                        input.getAttribute(RedisStore.EVICTED_KEYS) >= 0 &&
                        input.getAttribute(RedisStore.KEYSPACE_HITS) >= 0 &&
                        input.getAttribute(RedisStore.KEYSPACE_MISSES) >= 0;
            }
        });
    }
}
