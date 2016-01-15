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

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.ResourcePredicates;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * An entity that represents a Redis key-value store service.
 */
@Catalog(name="Redis Server", description="Redis is an open-source, networked, in-memory, key-value data store with optional durability", iconUrl="classpath:///redis-logo.png")
@ImplementedBy(RedisStoreImpl.class)
public interface RedisStore extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.8.4");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://download.redis.io/releases/redis-${version}.tar.gz");

    @SetFromFlag("redisPort")
    PortAttributeSensorAndConfigKey REDIS_PORT = new PortAttributeSensorAndConfigKey("redis.port", "Redis port number", "6379+");

    @SetFromFlag("redisConfigTemplateUrl")
    ConfigKey<String> REDIS_CONFIG_TEMPLATE_URL = ConfigKeys.builder(String.class)
            .name("redis.config.templateUrl")
            .description("Template file (in freemarker format) for the redis.conf config file")
            .defaultValue("classpath://org/apache/brooklyn/entity/nosql/redis/redis.conf")
            .constraint(ResourcePredicates.urlExists())
            .build();

    AttributeSensor<Integer> UPTIME = Sensors.newIntegerSensor("redis.uptime", "Redis uptime in seconds");

    // See http://redis.io/commands/info for details of all information available
    AttributeSensor<Integer> TOTAL_CONNECTIONS_RECEIVED = Sensors.newIntegerSensor("redis.connections.received.total", "Total number of connections accepted by the server");
    AttributeSensor<Integer> TOTAL_COMMANDS_PROCESSED = Sensors.newIntegerSensor("redis.commands.processed.total", "Total number of commands processed by the server");
    AttributeSensor<Integer> EXPIRED_KEYS = Sensors.newIntegerSensor("redis.keys.expired", "Total number of key expiration events");
    AttributeSensor<Integer> EVICTED_KEYS = Sensors.newIntegerSensor("redis.keys.evicted", "Number of evicted keys due to maxmemory limit");
    AttributeSensor<Integer> KEYSPACE_HITS = Sensors.newIntegerSensor("redis.keyspace.hits", "Number of successful lookup of keys in the main dictionary");
    AttributeSensor<Integer> KEYSPACE_MISSES = Sensors.newIntegerSensor("redis.keyspace.misses", "Number of failed lookup of keys in the main dictionary");

    String getAddress();

    Integer getRedisPort();

}
