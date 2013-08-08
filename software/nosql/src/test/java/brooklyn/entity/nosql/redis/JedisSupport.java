/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import redis.clients.jedis.Jedis;

import com.google.common.base.Strings;

/**
 * {@link RedisStore} testing using Jedis API.
 */
public class JedisSupport {
    private static final String TEST_DATA = Strings.repeat("0123456789", 16);

    private RedisStore redis;

    public JedisSupport(RedisStore redis) {
        this.redis = redis;
    }

    /**
     * Exercise the {@link RedisStore} using the Jedis API.
     */
    public void redisTest() throws Exception {
        writeData("brooklyn", TEST_DATA);
        String result = readData("brooklyn");
        assertEquals(result, TEST_DATA);
    }
    
    public void writeData(String key, String val) throws Exception {
        Jedis client = getRedisClient(redis);
        try {
            client.set(key, val);
        } finally {
            client.disconnect();
        }
    }

    public String readData(String key) throws Exception {
        Jedis client = getRedisClient(redis);
        try {
            return client.get(key);
        } finally {
            client.disconnect();
        }
    }

    private Jedis getRedisClient(RedisStore redis) {
        int port = redis.getAttribute(RedisStore.REDIS_PORT);
        String host = redis.getAttribute(RedisStore.HOSTNAME);
        Jedis client = new Jedis(host, port);
        client.connect();
        assertTrue(client.isConnected());
        return client;
    }
}
