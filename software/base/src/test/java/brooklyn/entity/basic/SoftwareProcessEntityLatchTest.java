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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.management.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntityTest.MyService;
import brooklyn.entity.basic.SoftwareProcessEntityTest.SimulatedDriver;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.FixedListMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.Asserts;
import brooklyn.util.task.TaskInternal;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;


public class SoftwareProcessEntityLatchTest extends BrooklynAppUnitTestSupport {

    // NB: These tests don't actually require ssh to localhost -- only that 'localhost' resolves.

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SoftwareProcessEntityLatchTest.class);

    private SshMachineLocation machine;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = getLocation();
    }

    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<SshMachineLocation> getLocation() {
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class));
        machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        loc.addMachine(machine);
        return loc;
    }

    @Test
    public void testStartLatchBlocks() throws Exception {
        runTestLatchBlocks(SoftwareProcess.START_LATCH, ImmutableList.<String>of());
    }

    @Test
    public void testSetupLatchBlocks() throws Exception {
        runTestLatchBlocks(SoftwareProcess.SETUP_LATCH, ImmutableList.<String>of());
    }

    @Test
    public void testIntallResourcesLatchBlocks() throws Exception {
        runTestLatchBlocks(SoftwareProcess.INSTALL_RESOURCES_LATCH, ImmutableList.of("setup"));
    }

    @Test
    public void testInstallLatchBlocks() throws Exception {
        runTestLatchBlocks(SoftwareProcess.INSTALL_LATCH, ImmutableList.of("setup", "copyInstallResources"));
    }

    @Test
    public void testCustomizeLatchBlocks() throws Exception {
        runTestLatchBlocks(SoftwareProcess.CUSTOMIZE_LATCH, ImmutableList.of("setup", "copyInstallResources", "install"));
    }

    @Test
    public void testRuntimeResourcesLatchBlocks() throws Exception {
        runTestLatchBlocks(SoftwareProcess.RUNTIME_RESOURCES_LATCH, ImmutableList.of("setup", "copyInstallResources", "install", "customize"));
    }

    @Test
    public void testLaunchLatchBlocks() throws Exception {
        runTestLatchBlocks(SoftwareProcess.LAUNCH_LATCH, ImmutableList.of("setup", "copyInstallResources", "install", "customize", "copyRuntimeResources"));
    }

    protected void runTestLatchBlocks(final ConfigKey<Boolean> latch, List<String> preLatchEvents) throws Exception {
        final BasicEntity triggerEntity = app.createAndManageChild(EntitySpec.create(BasicEntity.class));
        final MyService entity = app.createAndManageChild(EntitySpec.create(MyService.class)
                .configure(latch, DependentConfiguration.attributeWhenReady(triggerEntity, Attributes.SERVICE_UP)));
        
        final Task<Void> task = Entities.invokeEffector(app, app, MyService.START, ImmutableMap.of("locations", ImmutableList.of(loc)));
        
        assertEffectorBlockingDetailsEventually(entity, "Waiting for config "+latch.getName());
        assertDriverEventsEquals(entity, preLatchEvents);

        assertFalse(task.isDone());
        ((EntityLocal)triggerEntity).setAttribute(Attributes.SERVICE_UP, true);
        task.get(Duration.THIRTY_SECONDS);
        assertDriverEventsEquals(entity, ImmutableList.of("setup", "copyInstallResources", "install", "customize", "copyRuntimeResources", "launch"));
    }

    private void assertDriverEventsEquals(MyService entity, List<String> expectedEvents) {
        List<String> events = ((SimulatedDriver)entity.getDriver()).events;
        assertEquals(events, expectedEvents, "events="+events);
    }

    private void assertEffectorBlockingDetailsEventually(final Entity entity, final String blockingDetailsSnippet) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Task<?> entityTask = Iterables.getOnlyElement(mgmt.getExecutionManager().getTasksWithAllTags(ImmutableList.of(BrooklynTaskTags.EFFECTOR_TAG, BrooklynTaskTags.tagForContextEntity(entity))));
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
