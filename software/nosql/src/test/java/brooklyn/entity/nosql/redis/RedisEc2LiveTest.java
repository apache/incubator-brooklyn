package brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import redis.clients.jedis.Connection;
import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class RedisEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RedisEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        // Start Redis
        RedisStore redis = new RedisStore(app);
        app.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(redis, RedisStore.SERVICE_UP, true);

        // Access Redis
        Connection connection = getRedisConnection(redis);
        assertTrue(connection.isConnected());
        connection.disconnect();
    }

    private Connection getRedisConnection(RedisStore redis) {
        int port = redis.getAttribute(RedisStore.REDIS_PORT);
        Connection connection = new Connection("localhost", port);
        connection.connect();
        return connection;
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
