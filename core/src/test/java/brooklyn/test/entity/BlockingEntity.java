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
package brooklyn.test.entity;

import java.util.concurrent.CountDownLatch;

import brooklyn.config.ConfigKey;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * Mock entity that blocks on startup via the {@link CountDownLatch} argument.
 */
@ImplementedBy(BlockingEntityImpl.class)
public interface BlockingEntity extends TestEntity {
    
    @SetFromFlag("startupLatch")
    public static final ConfigKey<CountDownLatch> STARTUP_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.startupLatch", "Latch that blocks startup");
    
    @SetFromFlag("shutdownLatch")
    public static final ConfigKey<CountDownLatch> SHUTDOWN_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.shutdownLatch", "Latch that blocks shutdown");
    
    @SetFromFlag("executingStartupNotificationLatch")
    public static final ConfigKey<CountDownLatch> EXECUTING_STARTUP_NOTIFICATION_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.executingStartupNotificationLatch", "");
    
    @SetFromFlag("executingShutdownNotificationLatch")
    public static final ConfigKey<CountDownLatch> EXECUTING_SHUTDOWN_NOTIFICATION_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.executingShutdownNotificationLatch", "");
}
