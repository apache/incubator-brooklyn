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
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.test.http.TestHttpRequestHandler;
import org.apache.brooklyn.test.http.TestHttpServer;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author m4rkmckenna on 12/11/2015.
 */
public class TestHttpCallTest {


    private TestHttpServer server;
    private TestApplication app;
    private ManagementContext managementContext;
    private LocalhostMachineProvisioningLocation loc;
    private String testId;

    @BeforeMethod
    public void setup() {
        testId = Identifiers.makeRandomId(8);
        server = new TestHttpServer()
                .handler("/201", new TestHttpRequestHandler()
                        .response("Created - " + testId)
                        .code(201))
                .handler("/204", new TestHttpRequestHandler().code(204))
                .handler("/index.html", new TestHttpRequestHandler()
                        .response("<html><body><h1>Im a H1 tag!</h1></body></html>")
                        .code(200))
                .handler("/body.json", new TestHttpRequestHandler()
                        .response("{\"a\":\"b\",\"c\":\"d\",\"e\":123,\"g\":false}")
                        .code(200 + Identifiers.randomInt(99)))
                .start();
        app = TestApplication.Factory.newManagedInstanceForTests();
        managementContext = app.getManagementContext();
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                .configure("name", testId));
    }


    @Test
    public void testHttpBodyAssertions() {
        app.createAndManageChild(EntitySpec.create(TestHttpCall.class)
                .configure(TestHttpCall.TARGET_URL, server.getUrl() + "/201")
                .configure(TestHttpCall.TIMEOUT, new Duration(10L, TimeUnit.SECONDS))
                .configure(TestSensor.ASSERTIONS, newAssertion("isEqualTo", "Created - " + testId)));
        app.createAndManageChild(EntitySpec.create(TestHttpCall.class)
                .configure(TestHttpCall.TARGET_URL, server.getUrl() + "/204")
                .configure(TestHttpCall.TIMEOUT, new Duration(10L, TimeUnit.SECONDS))
                .configure(TestSensor.ASSERTIONS, newAssertion("isEqualTo", "")));
        app.createAndManageChild(EntitySpec.create(TestHttpCall.class)
                .configure(TestHttpCall.TARGET_URL, server.getUrl() + "/index.html")
                .configure(TestHttpCall.TIMEOUT, new Duration(10L, TimeUnit.SECONDS))
                .configure(TestSensor.ASSERTIONS, newAssertion("contains", "Im a H1 tag!")));
        app.createAndManageChild(EntitySpec.create(TestHttpCall.class)
                .configure(TestHttpCall.TARGET_URL, server.getUrl() + "/body.json")
                .configure(TestHttpCall.TIMEOUT, new Duration(10L, TimeUnit.SECONDS))
                .configure(TestSensor.ASSERTIONS, newAssertion("matches", ".*123.*")));
        app.start(ImmutableList.of(loc));
    }

    @Test
    public void testHttpStatusAssertions() {
        app.createAndManageChild(EntitySpec.create(TestHttpCall.class)
                .configure(TestHttpCall.TARGET_URL, server.getUrl() + "/201")
                .configure(TestHttpCall.TIMEOUT, new Duration(10L, TimeUnit.SECONDS))
                .configure(TestHttpCall.ASSERTION_TARGET, TestHttpCall.HttpAssertionTarget.status)
                .configure(TestSensor.ASSERTIONS, newAssertion("notNull", "")));
        app.createAndManageChild(EntitySpec.create(TestHttpCall.class)
                .configure(TestHttpCall.TARGET_URL, server.getUrl() + "/204")
                .configure(TestHttpCall.TIMEOUT, new Duration(10L, TimeUnit.SECONDS))
                .configure(TestHttpCall.ASSERTION_TARGET, TestHttpCall.HttpAssertionTarget.status)
                .configure(TestSensor.ASSERTIONS, newAssertion("isEqualTo", "204")));
        app.createAndManageChild(EntitySpec.create(TestHttpCall.class)
                .configure(TestHttpCall.TARGET_URL, server.getUrl() + "/body.json")
                .configure(TestHttpCall.TIMEOUT, new Duration(10L, TimeUnit.SECONDS))
                .configure(TestHttpCall.ASSERTION_TARGET, TestHttpCall.HttpAssertionTarget.status)
                .configure(TestSensor.ASSERTIONS, newAssertion("matches", "2[0-9][0-9]")));
        app.start(ImmutableList.of(loc));
    }

    private List<Map<String, Object>> newAssertion(final String assertionKey, final Object assertionValue) {
        final List<Map<String, Object>> result = new ArrayList<>();
        result.add(ImmutableMap.of(assertionKey, assertionValue));
        return result;
    }

}
