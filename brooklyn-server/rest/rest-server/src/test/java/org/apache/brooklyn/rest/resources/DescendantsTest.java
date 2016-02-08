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
package org.apache.brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.text.StringEscapes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;

@Test(singleThreaded = true)
public class DescendantsTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(DescendantsTest.class);

    private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").
        entities(ImmutableSet.of(
            new EntitySpec("simple-ent-1", RestMockSimpleEntity.class.getName()),
            new EntitySpec("simple-ent-2", RestMockSimpleEntity.class.getName()))).
        locations(ImmutableSet.of("localhost")).
        build();

    @Test
    public void testDescendantsInSimpleDeployedApplication() throws InterruptedException, TimeoutException, UniformInterfaceException, ClientHandlerException, IOException {
        ClientResponse response = clientDeploy(simpleSpec);
        assertTrue(response.getStatus()/100 == 2, "response is "+response);
        Application application = Iterables.getOnlyElement( getManagementContext().getApplications() );
        List<Entity> entities = MutableList.copyOf( application.getChildren() );
        log.debug("Created app "+application+" with children entities "+entities);
        assertEquals(entities.size(), 2);
        
        Set<EntitySummary> descs;
        descs = client().resource("/v1/applications/"+application.getApplicationId()+"/descendants")
            .get(new GenericType<Set<EntitySummary>>() {});
        // includes itself
        assertEquals(descs.size(), 3);
        
        descs = client().resource("/v1/applications/"+application.getApplicationId()+"/descendants"
            + "?typeRegex="+StringEscapes.escapeUrlParam(".*\\.RestMockSimpleEntity"))
            .get(new GenericType<Set<EntitySummary>>() {});
        assertEquals(descs.size(), 2);
        
        descs = client().resource("/v1/applications/"+application.getApplicationId()+"/descendants"
            + "?typeRegex="+StringEscapes.escapeUrlParam(".*\\.BestBockSimpleEntity"))
            .get(new GenericType<Set<EntitySummary>>() {});
        assertEquals(descs.size(), 0);

        descs = client().resource("/v1/applications/"+application.getApplicationId()
            + "/entities/"+entities.get(1).getId()
            + "/descendants"
            + "?typeRegex="+StringEscapes.escapeUrlParam(".*\\.RestMockSimpleEntity"))
            .get(new GenericType<Set<EntitySummary>>() {});
        assertEquals(descs.size(), 1);
        
        Map<String,Object> sensors = client().resource("/v1/applications/"+application.getApplicationId()+"/descendants/sensor/foo"
            + "?typeRegex="+StringEscapes.escapeUrlParam(".*\\.RestMockSimpleEntity"))
            .get(new GenericType<Map<String,Object>>() {});
        assertEquals(sensors.size(), 0);

        long v = 0;
        application.sensors().set(Sensors.newLongSensor("foo"), v);
        for (Entity e: entities)
            e.sensors().set(Sensors.newLongSensor("foo"), v+=123);
        
        sensors = client().resource("/v1/applications/"+application.getApplicationId()+"/descendants/sensor/foo")
            .get(new GenericType<Map<String,Object>>() {});
        assertEquals(sensors.size(), 3);
        assertEquals(sensors.get(entities.get(1).getId()), 246);
        
        sensors = client().resource("/v1/applications/"+application.getApplicationId()+"/descendants/sensor/foo"
            + "?typeRegex="+StringEscapes.escapeUrlParam(".*\\.RestMockSimpleEntity"))
            .get(new GenericType<Map<String,Object>>() {});
        assertEquals(sensors.size(), 2);
        
        sensors = client().resource("/v1/applications/"+application.getApplicationId()+"/"
            + "entities/"+entities.get(1).getId()+"/"
            + "descendants/sensor/foo"
            + "?typeRegex="+StringEscapes.escapeUrlParam(".*\\.RestMockSimpleEntity"))
            .get(new GenericType<Map<String,Object>>() {});
        assertEquals(sensors.size(), 1);

        sensors = client().resource("/v1/applications/"+application.getApplicationId()+"/"
            + "entities/"+entities.get(1).getId()+"/"
            + "descendants/sensor/foo"
            + "?typeRegex="+StringEscapes.escapeUrlParam(".*\\.FestPockSimpleEntity"))
            .get(new GenericType<Map<String,Object>>() {});
        assertEquals(sensors.size(), 0);
    }
    
}
