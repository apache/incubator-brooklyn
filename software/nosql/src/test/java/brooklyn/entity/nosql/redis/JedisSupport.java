/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/**
 * {@link RedisStore} testing using Jedis API.
 */
public class JedisSupport {
    private static final Logger log = LoggerFactory.getLogger(JedisSupport.class);

    private RedisStore redis;

    public JedisSupport(RedisStore redis) {
        this.redis = redis;
    }

    /**
     * Exercise the {@link RedisStore} using the Jedis API.
     */
    public void redisTest() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Jedis client = getRedisClient(redis);
        client.subscribe(new JedisPubSub() {
            public void onMessage(String channel, String message) {
                assertEquals(channel, "brooklyn");
                assertEquals(message, "message");
                latch.countDown();
            }
            public void onSubscribe(String channel, int subscribedChannels) { }
            public void onUnsubscribe(String channel, int subscribedChannels) { }
            public void onPSubscribe(String pattern, int subscribedChannels) { }
            public void onPUnsubscribe(String pattern, int subscribedChannels) { }
            public void onPMessage(String pattern, String channel, String message) { }
        }, "brooklyn");

        client.publish("brooklyn", "message");

        assertTrue(latch.await(60, TimeUnit.SECONDS));
        client.disconnect();
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
