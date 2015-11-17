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
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import static org.apache.brooklyn.test.framework.BaseTest.TARGET_ENTITY;
import static org.apache.brooklyn.test.framework.SimpleShellCommand.COMMAND;
import static org.apache.brooklyn.test.framework.SimpleShellCommandTest.*;
import static org.assertj.core.api.Assertions.assertThat;

public class SimpleShellCommandIntegrationTest extends BrooklynAppUnitTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleShellCommandIntegrationTest.class);

    private static final String UP = "up";
    private LocalhostMachineProvisioningLocation localhost;

    protected void setUpApp() {
        super.setUpApp();
        localhost = app.getManagementContext().getLocationManager()
            .createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
    }

    @Test(groups = "Integration")
    public void shouldInvokeCommand() {
        TestEntity testEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        SimpleShellCommandTest uptime = app.createAndManageChild(EntitySpec.create(SimpleShellCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(COMMAND, "uptime")
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

        SimpleShellCommandTest uptime = app.createAndManageChild(EntitySpec.create(SimpleShellCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(COMMAND, "uptime")
            .configure(ASSERT_STATUS, ImmutableMap.of(EQUALS, 1)));

        try {
            app.start(ImmutableList.of(localhost));
        } catch (Exception e) {
            assertThat(e.getCause().getMessage().contains("exit code equals 1"));
        }

        assertThat(ServiceStateLogic.getExpectedState(uptime)).isEqualTo(Lifecycle.ON_FIRE)
            .withFailMessage("Service should be marked on fire");

    }

    @Test(groups = "Integration")
    public void shouldInvokeScript() {
        TestEntity testEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        String text = "hello world";
        String testUrl = createTempScript("script.sh", "echo " + text);

        SimpleShellCommandTest uptime = app.createAndManageChild(EntitySpec.create(SimpleShellCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(DOWNLOAD_URL, testUrl)
            .configure(ASSERT_STATUS, ImmutableMap.of(EQUALS, 0))
            .configure(ASSERT_OUT, ImmutableMap.of(CONTAINS, text)));

        app.start(ImmutableList.of(localhost));

        assertThat(uptime.sensors().get(SERVICE_UP)).isTrue()
            .withFailMessage("Service should be up");
        assertThat(ServiceStateLogic.getExpectedState(uptime)).isEqualTo(Lifecycle.RUNNING)
            .withFailMessage("Service should be marked running");
    }

    @Test
    public void shouldExecuteInTheRightPlace() throws Exception {
        TestEntity testEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        String remoteTmp = randomName();
        SimpleShellCommandTest uptime = app.createAndManageChild(EntitySpec.create(SimpleShellCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(COMMAND, "mkdir " + remoteTmp)
            .configure(ASSERT_STATUS, ImmutableMap.of(EQUALS, 0)));

        String pwdUrl = createTempScript("pwd.sh", "pwd");

        SimpleShellCommandTest pwd = app.createAndManageChild(EntitySpec.create(SimpleShellCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(DOWNLOAD_URL, pwdUrl)
            .configure(RUN_DIR, remoteTmp)
            .configure(ASSERT_STATUS, ImmutableMap.of(EQUALS, 0))
            .configure(ASSERT_OUT, ImmutableMap.of(CONTAINS, remoteTmp)));

        app.start(ImmutableList.of(localhost));

        assertThat(uptime.sensors().get(SERVICE_UP)).isTrue()
            .withFailMessage("Service should be up");
        assertThat(ServiceStateLogic.getExpectedState(uptime)).isEqualTo(Lifecycle.RUNNING)
            .withFailMessage("Service should be marked running");
    }

    private String createTempScript(String filename, String contents) {
        try {
            Path tempDirectory = Files.createTempDirectory(randomName());
            tempDirectory.toFile().deleteOnExit();
            Path tempFile = Files.createFile(tempDirectory.resolve(filename));
            Files.write(tempFile, contents.getBytes());
            return "file:" + tempFile.toString();
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    private String randomName() {
        return Integer.valueOf(new Random(System.currentTimeMillis()).nextInt(100000)).toString();
    }

}
