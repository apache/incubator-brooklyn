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
package org.apache.brooklyn.camp.brooklyn.policy;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.test.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

public class CreatePasswordSensorIntegrationTest extends AbstractYamlTest {

    private static final Logger LOG = LoggerFactory.getLogger(CreatePasswordSensorIntegrationTest.class);
    AttributeSensor<String> PASSWORD_1 = Sensors.newStringSensor("test.password.1", "Host name as known internally in " +
            "the subnet where it is running (if different to host.name)");
    AttributeSensor<String> PASSWORD_2 = Sensors.newStringSensor("test.password.2", "Host name as known internally in " +
            "the subnet where it is running (if different to host.name)");

    @Test(groups = "Integration")
    public void testProvisioningProperties() throws Exception {
        final Entity app = createAndStartApplication(loadYaml("EmptySoftwareProcessWithPassword.yaml"));

        waitForApplicationTasks(app);
        EmptySoftwareProcess entity = Iterables.getOnlyElement(Entities.descendants(app, EmptySoftwareProcess.class));

        assertPasswordLength(entity, PASSWORD_1, 15);

        assertPasswordOnlyContains(entity, PASSWORD_2, "abc");

    }

    private void assertPasswordOnlyContains(EmptySoftwareProcess entity, AttributeSensor<String> password, String acceptableChars) {
        String attribute_2 = entity.getAttribute(password);
        for (char c : attribute_2.toCharArray()) {
            Asserts.assertTrue(acceptableChars.indexOf(c) != -1);
        }
    }

    private void assertPasswordLength(EmptySoftwareProcess entity, AttributeSensor<String> password, int expectedLength) {
        String attribute_1 = entity.getAttribute(password);
        Asserts.assertEquals(attribute_1.length(), expectedLength);
    }

}
