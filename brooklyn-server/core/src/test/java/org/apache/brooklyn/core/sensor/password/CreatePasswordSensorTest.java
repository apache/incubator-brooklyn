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
package org.apache.brooklyn.core.sensor.password;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class CreatePasswordSensorTest extends BrooklynAppUnitTestSupport{

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString");
    private EntityInternal entity;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        super.setUp();

        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .location(app.newLocalhostProvisioningLocation().obtain()));
        app.start(ImmutableList.<Location>of());
    }

    @Test
    public void testCreatePasswordCreatesAPasswordOfDefaultLength() {

        final CreatePasswordSensor sensor = new CreatePasswordSensor(ConfigBag.newInstance()
                .configure(CreatePasswordSensor.SENSOR_NAME, SENSOR_STRING.getName()));
        sensor.apply(entity);

        String password = entity.getAttribute(SENSOR_STRING);
        Asserts.assertEquals(password.length(), 12);
    }
}