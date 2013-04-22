/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Connection;

/**
 * {@link RedisStore} testing using Redis API.
 */
public class RedisSupport {
    private static final Logger log = LoggerFactory.getLogger(RedisSupport.class);

    private RedisStore redis;

    public RedisSupport(RedisStore redis) {
        this.redis = redis;
    }

    /**
     * Exercise the {@link RedisStore} using the Redis API.
     */
    public void redisTest() throws Exception {
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
}
