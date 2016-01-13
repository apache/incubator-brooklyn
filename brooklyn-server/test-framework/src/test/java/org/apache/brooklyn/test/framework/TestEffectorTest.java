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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.test.framework.entity.TestEntity;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Identifiers;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.apache.brooklyn.core.entity.trait.Startable.SERVICE_UP;
import static org.apache.brooklyn.test.Asserts.fail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

/**
 * @author m4rkmckenna on 27/10/2015.
 */
public class TestEffectorTest {

    private TestApplication app;
    private ManagementContext managementContext;
    private LocalhostMachineProvisioningLocation loc;
    private String testId;

    @BeforeMethod
    public void setup() {
        testId = Identifiers.makeRandomId(8);
        app = TestApplication.Factory.newManagedInstanceForTests();
        managementContext = app.getManagementContext();

        loc = managementContext.getLocationManager()
                .createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                .configure("name", testId));

    }


    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }


    @Test
    public void testSimpleEffector() {
        final TestCase testCase = app.createAndManageChild(EntitySpec.create(TestCase.class));
        final TestEntity testEntity = testCase.addChild(EntitySpec.create(TestEntity.class));


        final TestEffector testEffector = testCase.addChild(EntitySpec.create(TestEffector.class)
                .configure(TestEffector.TARGET_ENTITY, testEntity)
                .configure(TestEffector.EFFECTOR_NAME, "simpleEffector"));

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(testEntity.sensors().get(TestEntity.SIMPLE_EFFECTOR_INVOKED)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.SIMPLE_EFFECTOR_INVOKED)).isTrue();

        assertThat(testEffector.sensors().get(TestEffector.EFFECTOR_RESULT)).isNull();
    }

    @Test
    public void testEffectorPositiveAssertions() {
        final TestCase testCase = app.createAndManageChild(EntitySpec.create(TestCase.class));
        final TestEntity testEntity = testCase.addChild(EntitySpec.create(TestEntity.class));

        String stringToReturn = "Hello World!";

        Map<String, String> effectorParams = ImmutableMap.of("stringToReturn", stringToReturn);

        List<Map<String, Object>> assertions = ImmutableList.<Map<String, Object>>of(
                ImmutableMap.<String, Object>of(TestFrameworkAssertions.EQUAL_TO, stringToReturn),
                ImmutableMap.<String, Object>of(TestFrameworkAssertions.CONTAINS, "Hello")
        );

        final TestEffector testEffector = testCase.addChild(EntitySpec.create(TestEffector.class)
                .configure(TestEffector.TARGET_ENTITY, testEntity)
                .configure(TestEffector.EFFECTOR_NAME, "effectorReturnsString")
                .configure(TestEffector.EFFECTOR_PARAMS, effectorParams)
                .configure(TestEffector.ASSERTIONS, assertions));

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(testEffector.sensors().get(TestEffector.EFFECTOR_RESULT)).isEqualTo(stringToReturn);
        assertThat(testEffector.sensors().get(SERVICE_UP)).isTrue().withFailMessage("Service should be up");
    }

    @Test
    public void testEffectorNegativeAssertions() {
        final TestCase testCase = app.createAndManageChild(EntitySpec.create(TestCase.class));
        final TestEntity testEntity = testCase.addChild(EntitySpec.create(TestEntity.class));

        String stringToReturn = "Goodbye World!";

        Map<String, String> effectorParams = ImmutableMap.of("stringToReturn", stringToReturn);

        List<Map<String, Object>> assertions = ImmutableList.<Map<String, Object>>of(
                ImmutableMap.<String, Object>of(TestFrameworkAssertions.EQUAL_TO, "Not the string I expected"),
                ImmutableMap.<String, Object>of(TestFrameworkAssertions.CONTAINS, "Hello")
        );

        final TestEffector testEffector = testCase.addChild(EntitySpec.create(TestEffector.class)
                .configure(TestEffector.TARGET_ENTITY, testEntity)
                .configure(TestEffector.EFFECTOR_NAME, "effectorReturnsString")
                .configure(TestEffector.EFFECTOR_PARAMS, effectorParams)
                .configure(TestEffector.ASSERTIONS, assertions));

        try {
            app.start(ImmutableList.of(app.newSimulatedLocation()));
            fail("Should have thrown execption");
        } catch (Throwable throwable) {
            Throwable firstInteresting = Exceptions.getFirstInteresting(throwable);
            assertThat(firstInteresting).isNotNull();
            assertThat(throwable).isNotNull();
            assertThat(firstInteresting).isInstanceOf(AssertionError.class);
        }

        assertThat(testEffector.sensors().get(SERVICE_UP)).isFalse().withFailMessage("Service should not be up");
    }

    @Test
    public void testComplexffector() {
        final TestCase testCase = app.createAndManageChild(EntitySpec.create(TestCase.class));
        final TestEntity testEntity = testCase.addChild(EntitySpec.create(TestEntity.class));

        final long expectedLongValue = System.currentTimeMillis();
        final boolean expectedBooleanValue = expectedLongValue % 2 == 0;

        final TestEffector testEffector = testCase.addChild(EntitySpec.create(TestEffector.class)
                .configure(TestEffector.TARGET_ENTITY, testEntity)
                .configure(TestEffector.EFFECTOR_NAME, "complexEffector")
                .configure(TestEffector.EFFECTOR_PARAMS, ImmutableMap.of(
                        "stringValue", testId,
                        "booleanValue", expectedBooleanValue,
                        "longValue", expectedLongValue)));

        app.start(ImmutableList.of(app.newSimulatedLocation()));

        assertThat(testEntity.sensors().get(TestEntity.SIMPLE_EFFECTOR_INVOKED)).isNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_INVOKED)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_INVOKED)).isTrue();

        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_STRING)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_STRING)).isEqualTo(testId);

        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_BOOLEAN)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_BOOLEAN)).isEqualTo(expectedBooleanValue);

        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_LONG)).isNotNull();
        assertThat(testEntity.sensors().get(TestEntity.COMPLEX_EFFECTOR_LONG)).isEqualTo(expectedLongValue);

        assertThat(testEffector.sensors().get(TestEffector.EFFECTOR_RESULT)).isNotNull();
        assertThat(testEffector.sensors().get(TestEffector.EFFECTOR_RESULT)).isInstanceOf(TestEntity.TestPojo.class);

        final TestEntity.TestPojo effectorResult = (TestEntity.TestPojo) testEffector.sensors().get(TestEffector.EFFECTOR_RESULT);
        assertThat(effectorResult.getBooleanValue()).isEqualTo(expectedBooleanValue);
        assertThat(effectorResult.getStringValue()).isEqualTo(testId);
        assertThat(effectorResult.getLongValue()).isEqualTo(expectedLongValue);

    }

}
