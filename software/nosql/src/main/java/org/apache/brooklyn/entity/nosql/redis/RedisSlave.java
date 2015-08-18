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

import org.apache.brooklyn.api.entity.proxying.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.event.basic.BasicConfigKey;
import org.apache.brooklyn.core.util.flags.SetFromFlag;

/**
 * A {@link RedisStore} configured as a slave.
 */
@ImplementedBy(RedisSlaveImpl.class)
public interface RedisSlave extends RedisStore {

    @SetFromFlag("master")
    ConfigKey<RedisStore> MASTER = new BasicConfigKey<RedisStore>(RedisStore.class, "redis.master", "Redis master");

    @SetFromFlag("redisConfigTemplateUrl")
    ConfigKey<String> REDIS_CONFIG_TEMPLATE_URL = new BasicConfigKey<String>(
            String.class, "redis.config.templateUrl", "Template file (in freemarker format) for the redis.conf config file", 
            "classpath://org/apache/brooklyn/entity/nosql/redis/slave.conf");

    RedisStore getMaster();

}
