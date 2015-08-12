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
package brooklyn.entity.software;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.management.Task;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.BasicEntityImpl;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.StopSoftwareParameters.StopMode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.basic.Sensors;
import brooklyn.location.jclouds.BailOutJcloudsLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.task.TaskInternal;
import brooklyn.util.time.Duration;

public class MachineLifecycleEffectorTasksTest {
    public static boolean canStop(StopMode stopMode, boolean isEntityStopped) {
        BasicEntityImpl entity = new BasicEntityImpl();
        Lifecycle state = isEntityStopped ? Lifecycle.STOPPED : Lifecycle.RUNNING;
        entity.setAttribute(SoftwareProcess.SERVICE_STATE_ACTUAL, state);
        return MachineLifecycleEffectorTasks.canStop(stopMode, entity);
    }
    
    @DataProvider(name = "canStopStates")
    public Object[][] canStopStates() {
        return new Object[][] {
            { StopMode.ALWAYS, true, true },
            { StopMode.ALWAYS, false, true },
            { StopMode.IF_NOT_STOPPED, true, false },
            { StopMode.IF_NOT_STOPPED, false, true },
            { StopMode.NEVER, true, false },
            { StopMode.NEVER, false, false },
        };
    }

    @Test(dataProvider = "canStopStates")
    public void testBasicSonftwareProcessCanStop(StopMode mode, boolean isEntityStopped, boolean expected) {
        boolean canStop = canStop(mode, isEntityStopped);
        assertEquals(canStop, expected);
    }

    @Test
    public void testProvisionLatchObeyed() throws Exception {

        AttributeSensor<Boolean> ready = Sensors.newBooleanSensor("readiness");

        TestApplication app = TestApplication.Factory.newManagedInstanceForTests();
        BasicEntity triggerEntity = app.createAndManageChild(EntitySpec.create(BasicEntity.class));

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(BrooklynConfigKeys.PROVISION_LATCH, DependentConfiguration.attributeWhenReady(triggerEntity, ready)));

        final Task<Void> task = Entities.invokeEffector(app, app, Startable.START, ImmutableMap.of(
                "locations", ImmutableList.of(BailOutJcloudsLocation.newBailOutJcloudsLocation(app.getManagementContext()))));

        assertEffectorBlockingDetailsEventually(entity, "Waiting for config " + BrooklynConfigKeys.PROVISION_LATCH.getName());

        Asserts.succeedsContinually(new Runnable() {
            @Override
            public void run() {
                assertFalse(task.isDone());
            }
        });
        try {
            ((EntityLocal) triggerEntity).setAttribute(ready, true);
            task.get(Duration.THIRTY_SECONDS);
        } catch (Throwable t) {
            // BailOut location throws but we don't care.
        } finally {
            Entities.destroyAll(app.getManagementContext());
        }
    }

    private void assertEffectorBlockingDetailsEventually(final Entity entity, final String blockingDetailsSnippet) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Task<?> entityTask = Iterables.getOnlyElement(entity.getApplication().getManagementContext().getExecutionManager().getTasksWithAllTags(
                        ImmutableList.of(BrooklynTaskTags.EFFECTOR_TAG, BrooklynTaskTags.tagForContextEntity(entity))));
                String blockingDetails = getBlockingDetails(entityTask);
                assertTrue(blockingDetails.contains(blockingDetailsSnippet));
            }});
    }

    private String getBlockingDetails(Task<?> task) {
        List<TaskInternal<?>> taskChain = Lists.newArrayList();
        TaskInternal<?> taskI = (TaskInternal<?>) task;
        while (taskI != null) {
            taskChain.add(taskI);
            if (taskI.getBlockingDetails() != null) {
                return taskI.getBlockingDetails();
            }
            taskI = (TaskInternal<?>) taskI.getBlockingTask();
        }
        throw new IllegalStateException("No blocking details for "+task+" (walked task chain "+taskChain+")");
    }

}
