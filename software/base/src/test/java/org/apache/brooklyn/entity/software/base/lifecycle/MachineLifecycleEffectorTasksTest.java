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
package org.apache.brooklyn.entity.software.base.lifecycle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.StopSoftwareParameters.StopMode;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.entity.stock.BasicEntityImpl;
import org.apache.brooklyn.location.jclouds.BailOutJcloudsLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.task.TaskInternal;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

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

    @Test(groups="Integration")
    public void testProvisionLatchObeyed() throws Exception {

        AttributeSensor<Boolean> ready = Sensors.newBooleanSensor("readiness");

        TestApplication app = TestApplication.Factory.newManagedInstanceForTests();
        BasicEntity triggerEntity = app.createAndManageChild(EntitySpec.create(BasicEntity.class));

        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(BrooklynConfigKeys.PROVISION_LATCH, DependentConfiguration.attributeWhenReady(triggerEntity, ready)));

        final Task<Void> task = Entities.invokeEffector(app, app, Startable.START, ImmutableMap.of(
                "locations", ImmutableList.of(BailOutJcloudsLocation.newBailOutJcloudsLocation(app.getManagementContext()))));
        
        Time.sleep(ValueResolver.PRETTY_QUICK_WAIT);
        if (task.isDone()) throw new IllegalStateException("Task finished early with: "+task.get());
        assertEffectorBlockingDetailsEventually(entity, "Waiting for config " + BrooklynConfigKeys.PROVISION_LATCH.getName());

        Asserts.succeedsContinually(new Runnable() {
            @Override
            public void run() {
                if (task.isDone()) throw new IllegalStateException("Task finished early with: "+task.getUnchecked());
            }
        });
        try {
            ((EntityLocal) triggerEntity).setAttribute(ready, true);
            task.get(Duration.THIRTY_SECONDS);
        } catch (Throwable t) {
            Exceptions.propagateIfFatal(t);
            if ((t.toString().contains(BailOutJcloudsLocation.ERROR_MESSAGE))) {
                // expected - BailOut location throws - just swallow
            } else {
                Exceptions.propagate(t);
            }
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
