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
package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.config.render.RendererHints;
import brooklyn.config.render.TestRendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.rest.api.SensorApi;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import brooklyn.util.text.StringFunctions;

import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

/**
 * Test the {@link SensorApi} implementation.
 * <p>
 * Check that {@link SensorResource} correctly renders {@link AttributeSensor}
 * values, including {@link RendererHints.DisplayValue} hints.
 */
@Test(singleThreaded = true)
public class SensorResourceTest extends BrooklynRestResourceTest {

    private final ApplicationSpec simpleSpec = ApplicationSpec.builder()
            .name("simple-app")
            .entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName())))
            .locations(ImmutableSet.of("localhost"))
            .build();

    private static final String sensorsEndpoint = "/v1/applications/simple-app/entities/simple-ent/sensors";
    private static final String sensorName = "ammphibian.count";
    private static final AttributeSensor<Integer> sensor = Sensors.newIntegerSensor(sensorName);

    /**
     * Sets up the application and entity.
     * <p>
     * Adds a sensor and sets its value to {@code 12345}. Configures a display value
     * hint that appends {@code frogs} to the value of the sensor.
     */
    @BeforeClass(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Deploy application
        ClientResponse deploy = clientDeploy(simpleSpec);
        waitForApplicationToBeRunning(deploy.getLocation());

        // Add new sensor
        EntityInternal entity = (EntityInternal) Iterables.find(getManagementContext().getEntityManager().getEntities(), new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return "RestMockSimpleEntity".equals(input.getEntityType().getSimpleName());
            }
        });
        entity.getMutableEntityType().addSensor(sensor);
        entity.setAttribute(sensor, 12345);

        // Register display value hint
        RendererHints.register(sensor, RendererHints.displayValue(Functions.compose(StringFunctions.append(" frogs"), Functions.toStringFunction())));
    }

    @AfterClass(alwaysRun = true)
    @Override
    public void tearDown() throws Exception {
        TestRendererHints.clearRegistry();
        super.tearDown();
    }

    /** Check default is to use display value hint. */
    @Test
    public void testBatchSensorRead() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/current-state")
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        Map<String, ?> currentState = response.getEntity(new GenericType<Map<String,?>>(Map.class) {});

        for (String sensor : currentState.keySet()) {
            if (sensor.equals(sensorName)) {
                assertEquals(currentState.get(sensor), "12345 frogs");
            }
        }
    }

    /** Check setting {@code raw} to {@code true} ignores display value hint. */
    @Test(dependsOnMethods = "testBatchSensorRead")
    public void testBatchSensorReadRaw() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/current-state")
                .queryParam("raw", "true")
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        Map<String, ?> currentState = response.getEntity(new GenericType<Map<String,?>>(Map.class) {});

        for (String sensor : currentState.keySet()) {
            if (sensor.equals(sensorName)) {
                assertEquals(currentState.get(sensor), Integer.valueOf(12345));
            }
        }
    }

    /** Check default is to use display value hint. */
    @Test
    public void testGet() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/" + sensorName)
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        String value = response.getEntity(String.class);
        assertEquals(value, "\"12345 frogs\"");
    }

    /** Check default is to use display value hint. */
    @Test(dependsOnMethods = "testGet")
    public void testGetPlain() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/" + sensorName)
                .accept(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        String value = response.getEntity(String.class);
        assertEquals(value, "12345 frogs");
    }

    /** Check setting {@code raw} to {@code true} ignores display value hint. */
    @Test(dependsOnMethods = "testGetPlain")
    public void testGetPlainRaw() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/" + sensorName)
                .queryParam("raw", "true")
                .accept(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        String value = response.getEntity(String.class);
        assertEquals(value, "12345");
    }

    /** Check setting {@code raw} to {@code true} ignores display value hint. */
    @Test(dependsOnMethods = "testGet")
    public void testGetRaw() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/" + sensorName)
                .queryParam("raw", "true")
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        Integer value = response.getEntity(Integer.class);
        assertEquals(value, Integer.valueOf(12345));
    }

    /** Check setting {@code raw} to {@code false} uses display value hint. */
    @Test(dependsOnMethods = "testGetPlainRaw")
    public void testGetPlainRawFalse() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/" + sensorName)
                .queryParam("raw", "false")
                .accept(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        String value = response.getEntity(String.class);
        assertEquals(value, "12345 frogs");
    }

    /** Check empty vaue for {@code raw} will revert to using default. */
    @Test(dependsOnMethods = "testGetPlainRaw")
    public void testGetPlainRawEmpty() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/" + sensorName)
                .queryParam("raw", "")
                .accept(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        String value = response.getEntity(String.class);
        assertEquals(value, "12345 frogs");
    }

    /** Check unparseable vaue for {@code raw} will revert to using default. */
    @Test(dependsOnMethods = "testGetPlainRaw")
    public void testGetPlainRawError() throws Exception {
        ClientResponse response = client().resource(sensorsEndpoint + "/" + sensorName)
                .queryParam("raw", "biscuits")
                .accept(MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
        String value = response.getEntity(String.class);
        assertEquals(value, "12345 frogs");
    }
}
