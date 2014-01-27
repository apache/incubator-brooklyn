package brooklyn.entity.nosql.redis;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

public class RedisEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RedisEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        RedisStore redis = app.createAndManageChild(EntitySpec.create(RedisStore.class));
        app.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(redis, RedisStore.SERVICE_UP, true);

        JedisSupport support = new JedisSupport(redis);
        support.redisTest();
        // Confirm sensors are valid
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

    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
