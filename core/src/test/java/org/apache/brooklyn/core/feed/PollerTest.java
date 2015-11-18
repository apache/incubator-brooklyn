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
package org.apache.brooklyn.core.feed;

import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class PollerTest extends BrooklynAppUnitTestSupport {

    @DataProvider(name = "specProvider")
    public Object[][] specProvider() {
        EntitySpec<FeedExceptionEntity> pollFailer = EntitySpec.create(FeedExceptionEntity.class)
                .configure(FeedExceptionEntity.POLLER, new PollFailer());
        EntitySpec<FeedExceptionEntity> taskFailer = EntitySpec.create(FeedExceptionEntity.class)
                .configure(FeedExceptionEntity.POLLER, new TaskFailer());
        return new Object[][]{{pollFailer}, {taskFailer}};
    }

    @Test(dataProvider = "specProvider")
    public void testFeedContinuesWhenPollerThrows(EntitySpec<FeedExceptionEntity> spec) {
        Map<?, ?> timeoutFlags = ImmutableMap.of("timeout", "100ms");
        FeedExceptionEntity fee = app.createAndManageChild(spec);
        app.start(ImmutableList.of(app.newSimulatedLocation()));
        EntityAsserts.assertAttributeEqualsEventually(timeoutFlags, fee, FeedExceptionEntity.FLAG, true);

        fee.startThrowingPollExceptions();
        EntityAsserts.assertAttributeEqualsEventually(timeoutFlags, fee, FeedExceptionEntity.FLAG, false);
        EntityAsserts.assertAttributeEqualsContinually(timeoutFlags, fee, FeedExceptionEntity.FLAG, false);

        fee.stopThrowingPollExceptions();
        EntityAsserts.assertAttributeEqualsEventually(timeoutFlags, fee, FeedExceptionEntity.FLAG, true);
        EntityAsserts.assertAttributeEqualsContinually(timeoutFlags, fee, FeedExceptionEntity.FLAG, true);
    }

    @ImplementedBy(FeedExceptionEntityImpl.class)
    public static interface FeedExceptionEntity extends Entity {
        ConfigKey<ThrowingPoller> POLLER = ConfigKeys.newConfigKey(ThrowingPoller.class, "poller");
        AttributeSensor<Boolean> FLAG = Sensors.newBooleanSensor("flag");

        void startThrowingPollExceptions();
        void stopThrowingPollExceptions();
    }

    public static class FeedExceptionEntityImpl extends AbstractEntity implements FeedExceptionEntity {
        private ThrowingPoller poller;

        @Override
        public void init() {
            super.init();
            poller = config().get(POLLER);
            FunctionFeed.builder()
                    .entity(this)
                    .period(1L)
                    .poll(new FunctionPollConfig<Boolean, Boolean>(FLAG)
                            .callable(poller)
                            .onException(Functions.constant(false)))
                    .build();
        }

        public void startThrowingPollExceptions() {
            this.poller.setShouldThrow(true);
        }

        public void stopThrowingPollExceptions() {
            this.poller.setShouldThrow(false);
        }
    }

    private static class TaskFailer extends ThrowingPoller {
        public Boolean execute(final boolean shouldThrow) {
            Task<Boolean> t = Tasks.<Boolean>builder()
                    .body(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            if (shouldThrow) {
                                throw new IllegalArgumentException("exception in feed task");
                            }
                            return true;
                        }
                    })
                    .build();
            return DynamicTasks.queueIfPossible(t).orSubmitAsync().asTask().getUnchecked();
        }
    }

    private static class PollFailer extends ThrowingPoller {
        public Boolean execute(final boolean shouldThrow) {
            if (shouldThrow) {
                throw new IllegalArgumentException("exception in poller");
            }
            return true;
        }
    }

    private static abstract class ThrowingPoller implements Callable<Boolean> {
        protected final Object throwLock = new Object[0];
        boolean shouldThrow = false;

        abstract Boolean execute(boolean shouldThrow);

        @Override
        public Boolean call() throws Exception {
            synchronized (throwLock) {
                return execute(shouldThrow);
            }
        }

        public void setShouldThrow(boolean shouldThrow) {
            synchronized (throwLock) {
                this.shouldThrow = shouldThrow;
            }
        }
    }

}
