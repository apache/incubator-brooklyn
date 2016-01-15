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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.rest.transform.SensorTransformer;

import com.google.common.collect.ImmutableMap;

public class SensorSummaryTest {

    private SensorSummary sensorSummary = new SensorSummary("redis.uptime", "Integer",
            "Description", ImmutableMap.of(
            "self", URI.create("/v1/applications/redis-app/entities/redis-ent/sensors/redis.uptime")));

    private TestApplication app;
    private TestEntity entity;
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        mgmt = app.getManagementContext();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    @Test
    public void testSerializeToJSON() throws IOException {
        assertEquals(asJson(sensorSummary), jsonFixture("fixtures/sensor-summary.json"));
    }

    @Test
    public void testDeserializeFromJSON() throws IOException {
        assertEquals(fromJson(jsonFixture("fixtures/sensor-summary.json"), SensorSummary.class), sensorSummary);
    }

    @Test
    public void testEscapesUriForSensorName() throws IOException {
        Sensor<String> sensor = Sensors.newStringSensor("name with space");
        SensorSummary summary = SensorTransformer.sensorSummary(entity, sensor);
        URI selfUri = summary.getLinks().get("self");

        String expectedUri = "/v1/applications/" + entity.getApplicationId() + "/entities/" + entity.getId() + "/sensors/" + "name%20with%20space";

        assertEquals(selfUri, URI.create(expectedUri));
    }

    // Previously failed because immutable-map builder threw exception if put same key multiple times,
    // and the NamedActionWithUrl did not have equals/hashCode
    @Test
    public void testSensorWithMultipleOpenUrlActionsRegistered() throws IOException {
        AttributeSensor<String> sensor = Sensors.newStringSensor("sensor1");
        entity.sensors().set(sensor, "http://myval");
        RendererHints.register(sensor, RendererHints.namedActionWithUrl());
        RendererHints.register(sensor, RendererHints.namedActionWithUrl());

        SensorSummary summary = SensorTransformer.sensorSummary(entity, sensor);

        assertEquals(summary.getLinks().get("action:open"), URI.create("http://myval"));
    }
}
