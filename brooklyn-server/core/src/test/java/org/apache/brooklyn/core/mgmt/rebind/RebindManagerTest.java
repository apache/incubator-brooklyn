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
package org.apache.brooklyn.core.mgmt.rebind;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.util.core.task.BasicTask;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class RebindManagerTest extends RebindTestFixtureWithApp {

    @Test
    public void testRebindingEntityCanCallTask() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(TestEntityWithTaskInRebind.class));
        
        newApp = rebind();
        Entity newEntity = Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        assertEquals(newEntity.getAttribute(TestEntity.NAME), "abc");
    }
    public static class TestEntityWithTaskInRebind extends TestEntityImpl {
        @Override
        public void rebind() {
            super.rebind();
            Task<String> task = new BasicTask<String>(new Callable<String>() {
                @Override public String call() {
                    return "abc";
                }});
            String val = DynamicTasks.queueIfPossible(task)
                    .orSubmitAsync()
                    .asTask()
                    .getUnchecked();
            sensors().set(TestEntity.NAME, val);
        }
    }
}
