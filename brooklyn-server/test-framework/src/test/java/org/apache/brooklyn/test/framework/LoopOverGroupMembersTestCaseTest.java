/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.test.framework;

import static org.apache.brooklyn.core.entity.trait.Startable.SERVICE_UP;
import static org.apache.brooklyn.test.Asserts.fail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Identifiers;

/**
 * @author Graeme Miller on 27/10/2015.
 */
public class LoopOverGroupMembersTestCaseTest {

    private TestApplication app;
    private Group testGroup;
    private ManagementContext managementContext;
    private LocalhostMachineProvisioningLocation loc;
    private String testId;
    private final String SENSOR_VAL = "Hello World!";

    private static final AttributeSensorAndConfigKey<String, String> STRING_SENSOR = ConfigKeys.newSensorAndConfigKey(String.class, "string-sensor", "String Sensor");

    @BeforeMethod
    public void setup() {
        testId = Identifiers.makeRandomId(8);
        app = TestApplication.Factory.newManagedInstanceForTests();
        managementContext = app.getManagementContext();

        loc = managementContext.getLocationManager()
                .createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                        .configure("name", testId));

        testGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testOneChildWhichPasses() {
        EmptySoftwareProcess emptySoftwareProcess = addEmptySoftwareProcessToGroup();
        EntitySpec<TestSensor> testSpec = createPassingTestSensorSpec();

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TEST_SPEC, testSpec);
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TARGET_ENTITY, testGroup);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(1);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isTrue();

        Entity loopChildEntity = loopOverGroupMembersTestCase.getChildren().iterator().next();
        assertThat(loopChildEntity).isInstanceOf(TestSensor.class);
        assertThat(loopChildEntity.sensors().get(SERVICE_UP)).isTrue();
        assertThat(loopChildEntity.config().get(LoopOverGroupMembersTestCase.TARGET_ENTITY)).isEqualTo(emptySoftwareProcess);
    }

    @Test
    public void testMultipleChildrenWhichPass() {
        Set<EmptySoftwareProcess> emptySoftwareProcesses = addMultipleEmptySoftwareProcessesToGroup(4);
        EntitySpec<TestSensor> testSpec = createPassingTestSensorSpec();

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TEST_SPEC, testSpec);
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TARGET_ENTITY, testGroup);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(4);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isTrue();

        for (Entity loopChildEntity : loopOverGroupMembersTestCase.getChildren()) {
            assertThat(loopChildEntity).isInstanceOf(TestSensor.class);
            assertThat(loopChildEntity.sensors().get(SERVICE_UP)).isTrue();
            assertThat(emptySoftwareProcesses.contains(loopChildEntity.config().get(LoopOverGroupMembersTestCase.TARGET_ENTITY))).isTrue();
            emptySoftwareProcesses.remove(loopChildEntity.config().get(LoopOverGroupMembersTestCase.TARGET_ENTITY));
        }
    }

    @Test
    public void testMultipleChildrenWhichAllFail() {
        Set<EmptySoftwareProcess> emptySoftwareProcesses = addMultipleEmptySoftwareProcessesToGroup(4);
        EntitySpec<TestSensor> testSpec = createFailingTestSensorSpec();

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TEST_SPEC, testSpec);
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TARGET_ENTITY, testGroup);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(4);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isFalse();

        for (Entity loopChildEntity : loopOverGroupMembersTestCase.getChildren()) {
            assertThat(loopChildEntity).isInstanceOf(TestSensor.class);
            assertThat(loopChildEntity.sensors().get(SERVICE_UP)).isFalse();
            assertThat(emptySoftwareProcesses.contains(loopChildEntity.config().get(LoopOverGroupMembersTestCase.TARGET_ENTITY))).isTrue();
            emptySoftwareProcesses.remove(loopChildEntity.config().get(LoopOverGroupMembersTestCase.TARGET_ENTITY));
        }
    }

    @Test
    public void testMultipleChildrenOneOfWhichFails() {
        Set<EmptySoftwareProcess> emptySoftwareProcesses = addMultipleEmptySoftwareProcessesToGroup(3);
        EntitySpec<TestSensor> testSpec = createPassingTestSensorSpec();

        EmptySoftwareProcess failingProcess = testGroup.addMemberChild(EntitySpec.create(EmptySoftwareProcess.class));
        failingProcess.sensors().set(STRING_SENSOR, "THIS STRING WILL CAUSE SENSOR TEST TO FAIL");

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TEST_SPEC, testSpec);
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TARGET_ENTITY, testGroup);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(4);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isFalse();

        for (Entity loopChildEntity : loopOverGroupMembersTestCase.getChildren()) {
            assertThat(loopChildEntity).isInstanceOf(TestSensor.class);

            Entity targetedEntity = loopChildEntity.config().get(LoopOverGroupMembersTestCase.TARGET_ENTITY);

            if (targetedEntity.equals(failingProcess)) {
                assertThat(loopChildEntity.sensors().get(SERVICE_UP)).isFalse();
            } else if (emptySoftwareProcesses.contains(targetedEntity)) {
                assertThat(loopChildEntity.sensors().get(SERVICE_UP)).isTrue();
                emptySoftwareProcesses.remove(targetedEntity);
            } else {
                fail("Targeted entity not recognized");
            }
        }
    }

    @Test
    public void testOneChildWhichFails() {
        EmptySoftwareProcess emptySoftwareProcess = addEmptySoftwareProcessToGroup();
        EntitySpec<TestSensor> testSpec = createFailingTestSensorSpec();

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TEST_SPEC, testSpec);
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TARGET_ENTITY, testGroup);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(1);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isFalse();

        Entity loopChildEntity = loopOverGroupMembersTestCase.getChildren().iterator().next();
        assertThat(loopChildEntity).isInstanceOf(TestSensor.class);
        assertThat(loopChildEntity.sensors().get(SERVICE_UP)).isFalse();
        assertThat(loopChildEntity.config().get(LoopOverGroupMembersTestCase.TARGET_ENTITY)).isEqualTo(emptySoftwareProcess);
    }

    //negative
    // without test spec
    // without target + taget id
    // not a group

    @Test
    public void testNoTarget() {
        EmptySoftwareProcess emptySoftwareProcess = addEmptySoftwareProcessToGroup();
        EntitySpec<TestSensor> testSpec = createFailingTestSensorSpec();

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TEST_SPEC, testSpec);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(0);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isFalse();
    }

    @Test
    public void testNotTargetingGroup() {
        EmptySoftwareProcess emptySoftwareProcess = addEmptySoftwareProcessToGroup();
        EntitySpec<TestSensor> testSpec = createFailingTestSensorSpec();

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TEST_SPEC, testSpec);
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TARGET_ENTITY, app);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(0);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isFalse();
    }

    @Test
    public void testNoSpec() {
        EmptySoftwareProcess emptySoftwareProcess = addEmptySoftwareProcessToGroup();
        EntitySpec<TestSensor> testSpec = createFailingTestSensorSpec();

        LoopOverGroupMembersTestCase loopOverGroupMembersTestCase = app.createAndManageChild(EntitySpec.create(LoopOverGroupMembersTestCase.class));
        loopOverGroupMembersTestCase.config().set(LoopOverGroupMembersTestCase.TARGET_ENTITY, testGroup);

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(loopOverGroupMembersTestCase.getChildren().size()).isEqualTo(0);
        assertThat(loopOverGroupMembersTestCase.sensors().get(SERVICE_UP)).isFalse();
    }

    //UTILITY METHODS
    private EntitySpec<TestSensor> createFailingTestSensorSpec() {
        List<Map<String, Object>> assertions = ImmutableList.<Map<String, Object>>of(
                ImmutableMap.<String, Object>of(TestFrameworkAssertions.EQUAL_TO, "THIS IS THE WRONG STRING")
        );

        return EntitySpec.create(TestSensor.class)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, assertions);
    }

    private EntitySpec<TestSensor> createPassingTestSensorSpec() {
        List<Map<String, Object>> assertions = ImmutableList.<Map<String, Object>>of(
                ImmutableMap.<String, Object>of(TestFrameworkAssertions.EQUAL_TO, SENSOR_VAL)
        );

        return EntitySpec.create(TestSensor.class)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, assertions);
    }

    private Set<EmptySoftwareProcess> addMultipleEmptySoftwareProcessesToGroup(int number) {
        MutableSet<EmptySoftwareProcess> softwareProcesses = MutableSet.<EmptySoftwareProcess>of();
        for (int i = 0; i < number; i++) {
            softwareProcesses.add(addEmptySoftwareProcessToGroup());
        }

        return softwareProcesses;
    }

    private EmptySoftwareProcess addEmptySoftwareProcessToGroup() {
        EmptySoftwareProcess emptySoftwareProcess = testGroup.addMemberChild(EntitySpec.create(EmptySoftwareProcess.class));
        emptySoftwareProcess.sensors().set(STRING_SENSOR, SENSOR_VAL);
        return emptySoftwareProcess;
    }

}
