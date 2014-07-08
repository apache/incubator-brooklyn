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

import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import brooklyn.location.Location;

import com.google.common.base.Throwables;

/**
 * Mock entity that blocks on startup via the {@link CountDownLatch} argument.
 */
public class BlockingEntityImpl extends TestEntityImpl implements BlockingEntity {
    
    public BlockingEntityImpl() {
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        try {
            if (getConfig(EXECUTING_STARTUP_NOTIFICATION_LATCH) != null) getConfig(EXECUTING_STARTUP_NOTIFICATION_LATCH).countDown();
            if (getConfig(STARTUP_LATCH) != null) getConfig(STARTUP_LATCH).await();
            super.start(locs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public void stop() {
        try {
            if (getConfig(EXECUTING_SHUTDOWN_NOTIFICATION_LATCH) != null) getConfig(EXECUTING_SHUTDOWN_NOTIFICATION_LATCH).countDown();
            if (getConfig(SHUTDOWN_LATCH) != null) getConfig(SHUTDOWN_LATCH).await();
            super.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
}
