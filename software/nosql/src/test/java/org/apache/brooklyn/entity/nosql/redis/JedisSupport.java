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
package org.apache.brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.entity.nosql.redis.RedisStore;

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
