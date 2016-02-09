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
package org.apache.brooklyn.rest.domain;

import static org.apache.brooklyn.rest.util.RestApiTestUtils.asJson;
import static org.apache.brooklyn.rest.util.RestApiTestUtils.fromJson;
import static org.apache.brooklyn.rest.util.RestApiTestUtils.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ApplicationTest {

    final EntitySpec entitySpec = new EntitySpec("Vanilla Java App", "org.apache.brooklyn.entity.java.VanillaJavaApp",
            ImmutableMap.<String, String>of(
                    "initialSize", "1",
                    "creationScriptUrl", "http://my.brooklyn.io/storage/foo.sql"));

    final ApplicationSpec applicationSpec = ApplicationSpec.builder().name("myapp")
            .entities(ImmutableSet.of(entitySpec))
            .locations(ImmutableSet.of("/v1/locations/1"))
            .build();

    final ApplicationSummary application = new ApplicationSummary(null, applicationSpec, Status.STARTING, null);

    @SuppressWarnings("serial")
    @Test
    public void testSerializeToJSON() throws IOException {
        ApplicationSummary application1 = new ApplicationSummary("myapp_id", applicationSpec, Status.STARTING, null) {
            @Override
            public Map<String, URI> getLinks() {
                return ImmutableMap.of(
                        "self", URI.create("/v1/applications/" + applicationSpec.getName()),
                        "entities", URI.create("fixtures/entity-summary-list.json"));
            }
        };
        assertEquals(asJson(application1), jsonFixture("fixtures/application.json"));
    }

    @Test
    public void testDeserializeFromJSON() throws IOException {
        assertEquals(fromJson(jsonFixture("fixtures/application.json"),
                ApplicationSummary.class), application);
    }

    @Test
    public void testTransitionToRunning() {
        ApplicationSummary running = application.transitionTo(Status.RUNNING);
        assertEquals(running.getStatus(), Status.RUNNING);
    }

    @Test
    public void testAppInAppTest() throws IOException {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        try {
            TestApplication app = mgmt.getEntityManager().createEntity(org.apache.brooklyn.api.entity.EntitySpec.create(TestApplication.class));
            app.addChild(org.apache.brooklyn.api.entity.EntitySpec.create(TestApplication.class));
            Asserts.assertEqualsIgnoringOrder(mgmt.getApplications(), ImmutableList.of(app));
        } finally {
            Entities.destroyAll(mgmt);
        }
    }
}
