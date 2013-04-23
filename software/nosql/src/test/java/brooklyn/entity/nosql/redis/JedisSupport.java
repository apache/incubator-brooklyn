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
        writeData(TEST_DATA);
        String result = readData();
        assertEquals(result, TEST_DATA);
    }
    public void writeData(String data) throws Exception {
        Jedis client = getRedisClient(redis);
        client.set("brooklyn", data);
        client.disconnect();
    }

    public String readData() throws Exception {
        Jedis client = getRedisClient(redis);
        String result = client.get("brooklyn");
        client.disconnect();
        return result;
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
