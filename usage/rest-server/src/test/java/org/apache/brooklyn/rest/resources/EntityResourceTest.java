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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;

@Test(singleThreaded = true)
public class EntityResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(EntityResourceTest.class);
    
    private final ApplicationSpec simpleSpec = ApplicationSpec.builder()
            .name("simple-app")
            .entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName())))
            .locations(ImmutableSet.of("localhost"))
            .build();

    private EntityInternal entity;

    private static final String entityEndpoint = "/v1/applications/simple-app/entities/simple-ent";

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Deploy application
        ClientResponse deploy = clientDeploy(simpleSpec);
        waitForApplicationToBeRunning(deploy.getLocation());

        // Add tag
        entity = (EntityInternal) Iterables.find(getManagementContext().getEntityManager().getEntities(), new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return "RestMockSimpleEntity".equals(input.getEntityType().getSimpleName());
            }
        });
    }

    @Test
    public void testTagsSanity() throws Exception {
        entity.tags().addTag("foo");
        
        ClientResponse response = client().resource(entityEndpoint + "/tags")
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get(ClientResponse.class);
        String data = response.getEntity(String.class);
        
        try {
            List<Object> tags = new ObjectMapper().readValue(data, new TypeReference<List<Object>>() {});
            Assert.assertTrue(tags.contains("foo"));
            Assert.assertFalse(tags.contains("bar"));
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalStateException("Error with deserialization of tags list: "+e+"\n"+data, e);
        }
    }
    
    @Test
    public void testRename() throws Exception {
        try {
            ClientResponse response = client().resource(entityEndpoint + "/name")
                .queryParam("name", "New Name")
                .post(ClientResponse.class);

            HttpAsserts.assertHealthyStatusCode(response.getStatus());
            Assert.assertTrue(entity.getDisplayName().equals("New Name"));
        } finally {
            // restore it for other tests!
            entity.setDisplayName("simple-ent");
        }
    }
    
    @Test
    public void testAddChild() throws Exception {
        try {
            // to test in GUI: 
            // services: [ { type: org.apache.brooklyn.entity.stock.BasicEntity }]
            ClientResponse response = client().resource(entityEndpoint + "/children?timeout=10s")
                .entity("services: [ { type: "+TestEntity.class.getName()+" }]", "application/yaml")
                .post(ClientResponse.class);

            HttpAsserts.assertHealthyStatusCode(response.getStatus());
            Assert.assertEquals(entity.getChildren().size(), 1);
            Entity child = Iterables.getOnlyElement(entity.getChildren());
            Assert.assertTrue(Entities.isManaged(child));
            
            TaskSummary task = response.getEntity(TaskSummary.class);
            Assert.assertEquals(task.getResult(), MutableList.of(child.getId()));
            
        } finally {
            // restore it for other tests
            Collection<Entity> children = entity.getChildren();
            if (!children.isEmpty()) Entities.unmanage(Iterables.getOnlyElement(children));
        }
    }
    
    @Test
    public void testTagsDoNotSerializeTooMuch() throws Exception {
        entity.tags().addTag("foo");
        entity.tags().addTag(entity.getParent());

        ClientResponse response = client().resource(entityEndpoint + "/tags")
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        String raw = response.getEntity(String.class);
        log.info("TAGS raw: "+raw);
        HttpAsserts.assertHealthyStatusCode(response.getStatus());
        
        Assert.assertTrue(raw.contains(entity.getParent().getId()), "unexpected app tag, does not include ID: "+raw);
        
        Assert.assertTrue(raw.length() < 1000, "unexpected app tag, includes too much mgmt info (len "+raw.length()+"): "+raw);
        
        Assert.assertFalse(raw.contains(entity.getManagementContext().getManagementNodeId()), "unexpected app tag, includes too much mgmt info: "+raw);
        Assert.assertFalse(raw.contains("managementContext"), "unexpected app tag, includes too much mgmt info: "+raw);
        Assert.assertFalse(raw.contains("localhost"), "unexpected app tag, includes too much mgmt info: "+raw);
        Assert.assertFalse(raw.contains("catalog"), "unexpected app tag, includes too much mgmt info: "+raw);

        @SuppressWarnings("unchecked")
        List<Object> tags = mapper().readValue(raw, List.class);
        log.info("TAGS are: "+tags);
        
        Assert.assertEquals(tags.size(), 2, "tags are: "+tags);

        Assert.assertTrue(tags.contains("foo"));
        Assert.assertFalse(tags.contains("bar"));
        
        MutableList<Object> appTags = MutableList.copyOf(tags);
        appTags.remove("foo");
        Object appTag = Iterables.getOnlyElement( appTags );
        
        // it's a map at this point, because there was no way to make it something stronger than Object
        Assert.assertTrue(appTag instanceof Map, "Should have deserialized an entity: "+appTag);
        // let's re-serialize it as an entity
        appTag = mapper().readValue(mapper().writeValueAsString(appTag), Entity.class);
        
        Assert.assertTrue(appTag instanceof Entity, "Should have deserialized an entity: "+appTag);
        Assert.assertEquals( ((Entity)appTag).getId(), entity.getApplicationId(), "Wrong ID: "+appTag);
        Assert.assertTrue(appTag instanceof BasicApplication, "Should have deserialized BasicApplication: "+appTag);
    }
    
}
