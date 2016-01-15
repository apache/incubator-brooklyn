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

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link RedisStore} class.
 */
public class RedisIntegrationTest {

    private TestApplication app;
    private Location loc;
    private RedisStore redis;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc = app.newLocalhostProvisioningLocation();
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

        // Increase timeout because test was failing on jenkins sometimes. The log shows only one 
        // call to `info server` (for obtaining uptime) which took 26 seconds; then 4 seconds later 
        // this assert failed (with it checking every 500ms). The response did correctly contain
        // `uptime_in_seconds:27`.
        EntityTestUtils.assertPredicateEventuallyTrue(ImmutableMap.of("timeout", Duration.FIVE_MINUTES), redis, new Predicate<RedisStore>() {
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
