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
package org.apache.brooklyn.test.framework;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.apache.brooklyn.test.framework.BaseTest.TARGET_ENTITY;
import static org.apache.brooklyn.test.framework.SimpleCommand.DEFAULT_COMMAND;
import static org.apache.brooklyn.test.framework.SimpleCommandTest.*;
import static org.assertj.core.api.Assertions.assertThat;

public class SimpleCommandImplIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleCommandImplIntegrationTest.class);

    private static final String UP = "up";
    private TestApplication app;
    private ManagementContext managementContext;
    private LocalhostMachineProvisioningLocation localhost;
    private String testId;


    @BeforeMethod
    public void setUp() {

        testId = UUID.randomUUID().toString();

        app = TestApplication.Factory.newManagedInstanceForTests();
        managementContext = app.getManagementContext();

        localhost = managementContext.getLocationManager()
            .createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                .configure("name", testId));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void shouldInvokeCommand() {
        TestEntity testEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        SimpleCommandTest uptime = app.createAndManageChild(EntitySpec.create(SimpleCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(DEFAULT_COMMAND, "uptime")
            .configure(ASSERT_STATUS, ImmutableMap.of(EQUALS, 0))
            .configure(ASSERT_OUT, ImmutableMap.of(CONTAINS, UP)));

        app.start(ImmutableList.of(localhost));

        assertThat(uptime.sensors().get(SERVICE_UP)).isTrue()
            .withFailMessage("Service should be up");
        assertThat(ServiceStateLogic.getExpectedState(uptime)).isEqualTo(Lifecycle.RUNNING)
            .withFailMessage("Service should be marked running");

    }

    @Test(groups = "Integration")
    public void shouldNotBeUpIfAssertionFails() {
        TestEntity testEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        SimpleCommandTest uptime = app.createAndManageChild(EntitySpec.create(SimpleCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(DEFAULT_COMMAND, "uptime")
            .configure(ASSERT_STATUS, ImmutableMap.of(EQUALS, 1)));

        try {
            app.start(ImmutableList.of(localhost));
        } catch (Exception e) {
            assertThat(e.getCause().getMessage().contains("exit code equals 1"));
        }

        assertThat(ServiceStateLogic.getExpectedState(uptime)).isEqualTo(Lifecycle.ON_FIRE)
            .withFailMessage("Service should be marked on fire");

    }

}
