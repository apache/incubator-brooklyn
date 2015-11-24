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
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author m4rkmckenna on 27/10/2015.
 */
public class TestSensorTest {

    private static final AttributeSensorAndConfigKey<Boolean, Boolean> BOOLEAN_SENSOR = ConfigKeys.newSensorAndConfigKey(Boolean.class, "boolean-sensor", "Boolean Sensor");
    private static final AttributeSensorAndConfigKey<String, String> STRING_SENSOR = ConfigKeys.newSensorAndConfigKey(String.class, "string-sensor", "String Sensor");
    private static final AttributeSensorAndConfigKey<Integer, Integer> INTEGER_SENSOR = ConfigKeys.newIntegerSensorAndConfigKey("integer-sensor", "Integer Sensor");
    private static final AttributeSensorAndConfigKey<Object, Object> OBJECT_SENSOR = ConfigKeys.newSensorAndConfigKey(Object.class, "object-sensor", "Object Sensor");

    private TestApplication app;
    private ManagementContext managementContext;
    private LocalhostMachineProvisioningLocation loc;
    private String testId;

    @BeforeMethod
    public void setup() {
        testId = Identifiers.makeRandomId(8);
        app = TestApplication.Factory.newManagedInstanceForTests();
        managementContext = app.getManagementContext();
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                .configure("name", testId));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testAssertEqual() {
        int testInteger = 100;

        //Add Sensor Test for BOOLEAN sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, BOOLEAN_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("equals", true)));
        //Add Sensor Test for STRING sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("equals", testId)));
        //Add Sensor Test for INTEGER sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, INTEGER_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("equals", testInteger)));

        //Set BOOLEAN Sensor to true
        app.sensors().set(BOOLEAN_SENSOR, Boolean.TRUE);

        // Give a value to INTEGER sensor
        app.sensors().set(INTEGER_SENSOR, testInteger);

        //Set STRING sensor to random string
        app.sensors().set(STRING_SENSOR, testId);

        app.start(ImmutableList.of(loc));

    }

    @Test
    public void testAssertEqualFailure() {
        boolean booleanAssertFailed = false;

        //Add Sensor Test for BOOLEAN sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, BOOLEAN_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("equals", true)));

        //Set BOOLEAN Sensor to false
        app.sensors().set(BOOLEAN_SENSOR, Boolean.FALSE);

        try {
            app.start(ImmutableList.of(loc));
        } catch (final PropagatedRuntimeException pre) {
            final AssertionError assertionError = Exceptions.getFirstThrowableOfType(pre, AssertionError.class);
            assertThat(assertionError).isNotNull();
            booleanAssertFailed = true;
        } finally {
            assertThat(booleanAssertFailed).isTrue();
        }
    }

    @Test
    public void testAssertEqualOnNullSensor() {
        boolean booleanAssertFailed = false;

        //Add Sensor Test for BOOLEAN sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, BOOLEAN_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("equals", false)));

        try {
            app.start(ImmutableList.of(loc));
        } catch (final PropagatedRuntimeException pre) {
            final AssertionError assertionError = Exceptions.getFirstThrowableOfType(pre, AssertionError.class);
            assertThat(assertionError).isNotNull().as("An assertion error should have been thrown");
            booleanAssertFailed = true;
        } finally {
            assertThat(booleanAssertFailed).isTrue().as("Equals assert should have failed as the sensor is NULL");
        }
    }

    @Test
    public void testAssertNull() {
        //Add Sensor Test for BOOLEAN sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, BOOLEAN_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS,  newAssertion("isNull", true)));
        //Add Sensor Test for STRING sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("notNull", true)));

        //Set STRING sensor to random string
        app.sensors().set(STRING_SENSOR, testId);

        app.start(ImmutableList.of(loc));

    }


    @Test
    public void testAssertNullFail() {
        boolean sensorTestFail = false;
        //Add Sensor Test for STRING sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("isNull", true)));

        //Set STRING sensor to random string
        app.sensors().set(STRING_SENSOR, testId);


        try {
            app.start(ImmutableList.of(loc));
        } catch (final PropagatedRuntimeException pre) {
            final AssertionError assertionError = Exceptions.getFirstThrowableOfType(pre, AssertionError.class);
            assertThat(assertionError).isNotNull().as("An assertion error should have been thrown");
            sensorTestFail = true;
        } finally {
            assertThat(sensorTestFail).isTrue().as("isNull assert should have failed as the sensor has been set");
        }

    }

    @Test
    public void testAssertMatches() {
        final long time = System.currentTimeMillis();
        final String sensorValue = String.format("%s%s%s", Identifiers.makeRandomId(8), time, Identifiers.makeRandomId(8));

        //Add Sensor Test for STRING sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("matches", String.format(".*%s.*", time))));
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, BOOLEAN_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("matches", "true")));

        //Set STRING sensor
        app.sensors().set(STRING_SENSOR, sensorValue);
        app.sensors().set(BOOLEAN_SENSOR, true);


        app.start(ImmutableList.of(loc));
    }

    @Test
    public void testAssertmatchesFail() {
        boolean sensorTestFail = false;
        final String sensorValue = String.format("%s%s%s", Identifiers.makeRandomId(8), System.currentTimeMillis(), Identifiers.makeRandomId(8));

        //Add Sensor Test for STRING sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("matches", String.format(".*%s.*", Identifiers.makeRandomId(8)))));

        //Set STRING sensor
        app.sensors().set(STRING_SENSOR, sensorValue);
        try {
            app.start(ImmutableList.of(loc));
        } catch (final PropagatedRuntimeException pre) {
            final AssertionError assertionError = Exceptions.getFirstThrowableOfType(pre, AssertionError.class);
            assertThat(assertionError).isNotNull().as("An assertion error should have been thrown");
            sensorTestFail = true;
        } finally {
            assertThat(sensorTestFail).isTrue().as("matches assert should have failed");
        }
    }

    @Test
    public void testAssertmatchesOnNullSensor() {
        boolean sensorTestFail = false;
        //Add Sensor Test for STRING sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, STRING_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("matches", String.format(".*%s.*", Identifiers.makeRandomId(8)))));

        try {
            app.start(ImmutableList.of(loc));
        } catch (final PropagatedRuntimeException pre) {
            final AssertionError assertionError = Exceptions.getFirstThrowableOfType(pre, AssertionError.class);
            assertThat(assertionError).isNotNull().as("An assertion error should have been thrown");
            sensorTestFail = true;
        } finally {
            assertThat(sensorTestFail).isTrue().as("matches assert should have failed");
        }
    }


    @Test
    public void testAssertMatchesOnNonStringSensor() {
        //Add Sensor Test for OBJECT sensor
        app.createAndManageChild(EntitySpec.create(TestSensor.class)
                .configure(TestSensor.TARGET_ENTITY, app)
                .configure(TestSensor.SENSOR_NAME, OBJECT_SENSOR.getName())
                .configure(TestSensor.ASSERTIONS, newAssertion("matches", ".*TestObject.*id=.*")));

        app.sensors().set(OBJECT_SENSOR, new TestObject());

        app.start(ImmutableList.of(loc));

    }

    private List<Map<String, Object>> newAssertion(final String assertionKey, final Object assertionValue) {
        final List<Map<String, Object>> result = new ArrayList<>();
        result.add(ImmutableMap.<String, Object>of(assertionKey, assertionValue));
        return result;
    }


    class TestObject {
        private final String id;

        public TestObject() {
            id = Identifiers.makeRandomId(8);
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

}
