package brooklyn.entity.nosql.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.internal.TimeExtras;

import com.google.common.collect.ImmutableList;

/**
 * Test the operation of the {@link RedisStore} class.
 */
public class RedisIntegrationTest {

    static { TimeExtras.init(); }

    private TestApplication app;
    private Location testLocation;
    private RedisStore redis;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation(MutableMap.of("name", "london"));
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
        redis = app.createAndManageChild(EntitySpecs.spec(RedisStore.class));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(redis, Startable.SERVICE_UP, true);

        redis.stop();

        EntityTestUtils.assertAttributeEqualsEventually(redis, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a client can connect to the service.
     */
    @Test(groups = { "Integration" })
    public void testRedisConnection() throws Exception {
        redis = app.createAndManageChild(EntitySpecs.spec(RedisStore.class));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(redis, Startable.SERVICE_UP, true);

        JedisSupport support = new JedisSupport(redis);
        try {
            support.redisTest();
        } finally {
            redis.stop();
        }
    }

}
