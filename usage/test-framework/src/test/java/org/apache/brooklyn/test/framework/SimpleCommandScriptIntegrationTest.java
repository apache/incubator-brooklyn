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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.test.http.TestHttpRequestHandler;
import org.apache.brooklyn.test.http.TestHttpServer;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.FatalRuntimeException;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.UUID;

import static org.apache.brooklyn.test.framework.BaseTest.TARGET_ENTITY;
import static org.apache.brooklyn.test.framework.SimpleCommand.DEFAULT_COMMAND;
import static org.apache.brooklyn.test.framework.SimpleCommandTest.*;
import static org.assertj.core.api.Assertions.assertThat;

public class SimpleCommandScriptIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleCommandScriptIntegrationTest.class);

    private static final String UP = "up";
    private static final String SCRIPT_NAME = "script.sh";
    private static final String TEXT = "hello world";
    private TestApplication app;
    private ManagementContext managementContext;
    private LocalhostMachineProvisioningLocation localhost;
    private TestHttpServer server;
    private String testId;


    @BeforeClass
    public void setUpTests() {
        server = initializeServer();
    }

    @AfterClass
    public void tearDownTests() {
        if (null != server) {
            server.stop();
        }
    }

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


    private TestHttpServer initializeServerUnstarted() {
        return new TestHttpServer()
            .handler("/" + SCRIPT_NAME,
                new TestHttpRequestHandler().response("#!/bin/sh\necho " + TEXT + "\n"));
    }
    private TestHttpServer initializeServer() {
        return initializeServerUnstarted().start();
    }



    @Test(groups = "Integration")
    public void shouldInvokeScript() {
        TestEntity testEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        String testUrl = server.getUrl() + "/" + SCRIPT_NAME;
        HttpAsserts.assertContentContainsText(testUrl, TEXT);

        SimpleCommandTest uptime = app.createAndManageChild(EntitySpec.create(SimpleCommandTest.class)
            .configure(TARGET_ENTITY, testEntity)
            .configure(DOWNLOAD_URL, testUrl)
            .configure(ASSERT_STATUS, ImmutableMap.of(EQUALS, 0))
            .configure(ASSERT_OUT, ImmutableMap.of(CONTAINS, TEXT)));

        app.start(ImmutableList.of(localhost));

        assertThat(uptime.sensors().get(SERVICE_UP)).isTrue()
            .withFailMessage("Service should be up");
        assertThat(ServiceStateLogic.getExpectedState(uptime)).isEqualTo(Lifecycle.RUNNING)
            .withFailMessage("Service should be marked running");
    }

}
